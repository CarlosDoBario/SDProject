package server;

import common.IOUtils;
import common.Message;
import common.Protocol;
import server.model.Event;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Estratégia:
 * - Loop principal lê mensagens do socket (Message.readFrom).
 * - Cada pedido é processado numa nova Thread manual (em vez de ExecutorService).
 * - Estado de autenticação protegido por stateLock (em vez de AtomicBoolean).
 * - Escrita ao DataOutputStream sincronizada com outLock (ReentrantLock).
 */
public class ConnectionHandler implements Runnable {
    private final Socket socket;
    private final AuthManager authManager;
    private final DayManager dayManager;
    private final PersistenceManager persistenceManager;
    private final AggregationManager aggregationManager;
    private final NotificationManager notificationManager;

    private final ReentrantLock stateLock = new ReentrantLock();
    private final ReentrantLock outLock = new ReentrantLock();

    private boolean authenticated = false;
    private String username = null;

    public ConnectionHandler(Socket socket,
                             AuthManager authManager,
                             DayManager dayManager,
                             PersistenceManager persistenceManager,
                             AggregationManager aggregationManager,
                             NotificationManager notificationManager) {
        this.socket = socket;
        this.authManager = authManager;
        this.dayManager = dayManager;
        this.persistenceManager = persistenceManager;
        this.aggregationManager = aggregationManager;
        this.notificationManager = notificationManager;
    }

    @Override
    public void run() {
        try (DataInputStream din = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             DataOutputStream dout = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {

            while (!socket.isClosed()) {
                Message req = Message.readFrom(din);
                if (req == null) break;

                // Processamento concorrente por conexão via Threads manuais
                new Thread(() -> {
                    try {
                        handleRequest(req, dout);
                    } catch (IOException ioe) {
                        System.err.println("I/O error handling request: " + ioe.getMessage());
                        try { socket.close(); } catch (IOException ignored) {}
                    } catch (Throwable t) {
                        System.err.println("Unexpected error: " + t.getMessage());
                    }
                }).start();
            }
        } catch (IOException e) {
            System.err.println("Connection I/O error: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void handleRequest(Message req, DataOutputStream dout) throws IOException {
        int reqId = req.getRequestId();
        byte op = req.getOpCode();
        byte[] payload = req.getPayload();

        switch (op) {
            case Protocol.REGISTER:
                handleRegister(reqId, payload, dout);
                break;
            case Protocol.LOGIN:
                handleLogin(reqId, payload, dout);
                break;
            case Protocol.ADD_EVENT:
                handleAddEvent(reqId, payload, dout);
                break;
            case Protocol.ADVANCE_DAY:
                handleAdvanceDay(reqId, payload, dout);
                break;
            case Protocol.AGG_QUANTITY:
            case Protocol.AGG_VOLUME:
            case Protocol.AGG_AVG_PRICE:
            case Protocol.AGG_MAX_PRICE:
                handleAggregation(reqId, op, payload, dout);
                break;
            case Protocol.FILTER_EVENTS:
                handleFilter(reqId, op, payload, dout);
                break;
            case Protocol.WAIT_SIMULTANEOUS:
                handleWaitSimultaneous(reqId, payload, dout);
                break;
            case Protocol.WAIT_CONSECUTIVE:
                handleWaitConsecutive(reqId, payload, dout);
                break;
            default:
                writeError(dout, reqId, Protocol.STATUS_INVALID_REQUEST, "OpCode not supported");
                break;
        }
    }

    private void handleRegister(int reqId, byte[] payload, DataOutputStream dout) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        String user = IOUtils.readString(in);
        String pass = IOUtils.readString(in);

        boolean created = authManager.register(user, pass);
        if (created) {
            sendSimpleResponse(dout, reqId, Protocol.STATUS_OK);
        } else {
            writeError(dout, reqId, Protocol.STATUS_ALREADY_EXISTS, "User already exists");
        }
    }

    private void handleLogin(int reqId, byte[] payload, DataOutputStream dout) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        String user = IOUtils.readString(in);
        String pass = IOUtils.readString(in);

        boolean ok = authManager.login(user, pass);
        if (ok) {
            stateLock.lock();
            try {
                this.authenticated = true;
                this.username = user;
            } finally {
                stateLock.unlock();
            }
            sendSimpleResponse(dout, reqId, Protocol.STATUS_OK);
        } else {
            writeError(dout, reqId, Protocol.STATUS_INVALID_CREDENTIALS, "Invalid credentials");
        }
    }

    private void handleAddEvent(int reqId, byte[] payload, DataOutputStream dout) throws IOException {
        if (!checkAuth(dout, reqId)) return;

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        Event e = new Event(IOUtils.readString(in), in.readInt(), in.readDouble(), in.readLong());
        dayManager.addEvent(e);

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream body = new DataOutputStream(bout);
        body.writeByte(Protocol.STATUS_OK);
        body.writeLong(System.currentTimeMillis());

        writeMessage(dout, reqId, Protocol.RESPONSE, bout.toByteArray());
    }

    private void handleAdvanceDay(int reqId, byte[] payload, DataOutputStream dout) throws IOException {
        if (!checkAuth(dout, reqId)) return;

        List<Event> toPersist = dayManager.closeCurrentDayAndStartNew();
        int closedDay = dayManager.getDayIndex() - 1;
        persistenceManager.persistDay(closedDay, toPersist);

        if (notificationManager != null) notificationManager.signalDayAdvanced();

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream body = new DataOutputStream(bout);
        body.writeByte(Protocol.STATUS_OK);
        body.writeInt(closedDay);

        writeMessage(dout, reqId, Protocol.RESPONSE, bout.toByteArray());
    }

    private void handleAggregation(int reqId, byte op, byte[] payload, DataOutputStream dout) throws IOException {
        if (!checkAuth(dout, reqId)) return;

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        String product = IOUtils.readString(in);
        int days = in.readInt();

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream body = new DataOutputStream(bout);
        body.writeByte(Protocol.STATUS_OK);

        if (op == Protocol.AGG_QUANTITY) body.writeInt(aggregationManager.aggregateQuantity(product, days));
        else if (op == Protocol.AGG_VOLUME) body.writeDouble(aggregationManager.aggregateVolume(product, days));
        else if (op == Protocol.AGG_AVG_PRICE) body.writeDouble(aggregationManager.aggregateAvgPrice(product, days));
        else if (op == Protocol.AGG_MAX_PRICE) body.writeDouble(aggregationManager.aggregateMaxPrice(product, days));

        writeMessage(dout, reqId, Protocol.RESPONSE, bout.toByteArray());
    }

    private void handleFilter(int reqId, byte op, byte[] payload, DataOutputStream dout) throws IOException {
        if (!checkAuth(dout, reqId)) return;

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        String product = IOUtils.readString(in);
        int days = in.readInt();

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream body = new DataOutputStream(bout);
        body.writeByte(Protocol.STATUS_OK);

        if (op == Protocol.AGG_QUANTITY) body.writeInt(aggregationManager.aggregateQuantity(product, days));
        else if (op == Protocol.AGG_VOLUME) body.writeDouble(aggregationManager.aggregateVolume(product, days));
        else if (op == Protocol.AGG_AVG_PRICE) body.writeDouble(aggregationManager.aggregateAvgPrice(product, days));
        else if (op == Protocol.AGG_MAX_PRICE) body.writeDouble(aggregationManager.aggregateMaxPrice(product, days));

        writeMessage(dout, reqId, Protocol.RESPONSE, bout.toByteArray());
    }

    private void handleWaitSimultaneous(int reqId, byte[] payload, DataOutputStream dout) throws IOException {
        if (!checkAuth(dout, reqId)) return;

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        try {
            boolean result = notificationManager.waitSimultaneous(IOUtils.readString(in), IOUtils.readString(in));
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream body = new DataOutputStream(bout);
            body.writeByte(Protocol.STATUS_OK);
            body.writeByte(result ? (byte)1 : (byte)0);
            writeMessage(dout, reqId, Protocol.RESPONSE, bout.toByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleWaitConsecutive(int reqId, byte[] payload, DataOutputStream dout) throws IOException {
        if (!checkAuth(dout, reqId)) return;

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        try {
            String res = notificationManager.waitConsecutive(in.readInt());
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream body = new DataOutputStream(bout);
            body.writeByte(Protocol.STATUS_OK);
            if (res != null) {
                body.writeByte((byte)1);
                IOUtils.writeString(body, res);
            } else body.writeByte((byte)0);
            writeMessage(dout, reqId, Protocol.RESPONSE, bout.toByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Auxiliares

    private boolean checkAuth(DataOutputStream dout, int reqId) throws IOException {
        stateLock.lock();
        try {
            if (authenticated) return true;
        } finally {
            stateLock.unlock();
        }
        writeError(dout, reqId, Protocol.STATUS_AUTH_REQUIRED, "Login required");
        return false;
    }

    private void sendSimpleResponse(DataOutputStream dout, int reqId, byte status) throws IOException {
        byte[] p = {status};
        writeMessage(dout, reqId, Protocol.RESPONSE, p);
    }

    private void writeError(DataOutputStream dout, int reqId, byte status, String msg) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream body = new DataOutputStream(bout);
        body.writeByte(status);
        IOUtils.writeString(body, msg);
        writeMessage(dout, reqId, Protocol.RESPONSE, bout.toByteArray());
    }

    private void writeMessage(DataOutputStream dout, int reqId, byte op, byte[] payload) throws IOException {
        Message m = new Message(reqId, op, payload);
        outLock.lock();
        try {
            m.writeTo(dout);
            dout.flush();
        } finally {
            outLock.unlock();
        }
    }
}