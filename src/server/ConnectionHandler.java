package server;

import common.IOUtils;
import common.Message;
import common.Protocol;
import server.model.Event;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 Estratégia:
  - Loop principal lê mensagens do socket (Message.readFrom).
  - Cada pedido é enviado para um worker thread (executor) para processamento, permitindo que a thread de leitura
    continue a receber novas mensagens (suporta múltiplos pedidos em paralelo por conexão).
  - Escrita ao DataOutputStream é sincronizada usando outLock para evitar interleaving.
 */
public class ConnectionHandler implements Runnable {
    private final Socket socket;
    private final AuthManager authManager;
    private final DayManager dayManager;
    private final PersistenceManager persistenceManager;
    private final AggregationManager aggregationManager;
    private final NotificationManager notificationManager;

    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private String username = null;

    // executor para processar pedidos de forma concorrente por conexão
    private final ExecutorService workerPool = Executors.newFixedThreadPool(4);

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
        final Object outLock = new Object();
        try (DataInputStream din = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             DataOutputStream dout = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {

            while (!socket.isClosed()) {
                Message req = Message.readFrom(din);
                if (req == null) break; // EOF

                final int reqId = req.getRequestId();
                final byte op = req.getOpCode();
                final byte[] payload = req.getPayload();

                // handling para a worker pool para que o read loop possa continuar
                workerPool.submit(() -> {
                    try {
                        switch (op) {
                            case Protocol.REGISTER:
                                handleRegister(reqId, payload, dout, outLock);
                                break;
                            case Protocol.LOGIN:
                                handleLogin(reqId, payload, dout, outLock);
                                break;
                            case Protocol.ADD_EVENT:
                                handleAddEvent(reqId, payload, dout, outLock);
                                break;
                            case Protocol.ADVANCE_DAY:
                                handleAdvanceDay(reqId, payload, dout, outLock);
                                break;
                            case Protocol.AGG_QUANTITY:
                            case Protocol.AGG_VOLUME:
                            case Protocol.AGG_AVG_PRICE:
                            case Protocol.AGG_MAX_PRICE:
                                handleAggregation(reqId, op, payload, dout, outLock);
                                break;
                            case Protocol.WAIT_SIMULTANEOUS:
                                handleWaitSimultaneous(reqId, payload, dout, outLock);
                                break;
                            case Protocol.WAIT_CONSECUTIVE:
                                handleWaitConsecutive(reqId, payload, dout, outLock);
                                break;
                            default:
                                writeError(dout, outLock, reqId, Protocol.STATUS_INVALID_REQUEST, "OpCode not supported");
                                break;
                        }
                    } catch (IOException ioe) {
                        // Problema ao escrever resposta: provavelmente socket fechado; registamos e tentamos fechar recurso
                        System.err.println("I/O error handling request " + reqId + ": " + ioe.getMessage());
                        try {
                            socket.close();
                        } catch (IOException ignored) {}
                    } catch (Throwable t) {
                        System.err.println("Unexpected error handling request " + reqId + ": " + t.getMessage());
                        try {
                            writeError(dout, outLock, reqId, Protocol.STATUS_INTERNAL_ERROR, "Server error");
                        } catch (IOException ignored) {}
                    }
                });
            }
        } catch (IOException e) {
            System.err.println("Connection I/O error: " + e.getMessage());
        } finally {
            // "matar" workers para esta conexão e fechar socket
            try {
                workerPool.shutdownNow();
            } catch (Exception ignored) {}
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // Handlers

    private void handleRegister(int reqId, byte[] payload, DataOutputStream dout, Object outLock) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        String user = IOUtils.readString(in);
        String pass = IOUtils.readString(in);
        if (user == null || pass == null) {
            writeError(dout, outLock, reqId, Protocol.STATUS_INVALID_REQUEST, "username/password required");
            return;
        }
        boolean created = authManager.register(user, pass);
        if (created) {
            // OK
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream body = new DataOutputStream(bout);
            body.writeByte(Protocol.STATUS_OK);
            body.flush();
            synchronized (outLock) { new Message(reqId, Protocol.RESPONSE, bout.toByteArray()).writeTo(dout); }
        } else {
            writeError(dout, outLock, reqId, Protocol.STATUS_ALREADY_EXISTS, "User already exists");
        }
    }

    private void handleLogin(int reqId, byte[] payload, DataOutputStream dout, Object outLock) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        String user = IOUtils.readString(in);
        String pass = IOUtils.readString(in);
        if (user == null || pass == null) {
            writeError(dout, outLock, reqId, Protocol.STATUS_INVALID_REQUEST, "username/password required");
            return;
        }
        boolean ok = authManager.login(user, pass);
        if (ok) {
            authenticated.set(true);
            username = user;
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream body = new DataOutputStream(bout);
            body.writeByte(Protocol.STATUS_OK);
            IOUtils.writeString(body, "welcome");
            body.flush();
            synchronized (outLock) { new Message(reqId, Protocol.RESPONSE, bout.toByteArray()).writeTo(dout); }
        } else {
            writeError(dout, outLock, reqId, Protocol.STATUS_INVALID_CREDENTIALS, "Invalid username/password");
        }
    }

    private void handleAddEvent(int reqId, byte[] payload, DataOutputStream dout, Object outLock) throws IOException {
        if (!authenticated.get()) {
            writeError(dout, outLock, reqId, Protocol.STATUS_AUTH_REQUIRED, "Authenticate first");
            return;
        }
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        String product = IOUtils.readString(in);
        int qty = in.readInt();
        double price = in.readDouble();
        long ts = in.readLong();
        Event e = new Event(product, qty, price, ts);
        dayManager.addEvent(e);

        // responde OK com ackTime
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream body = new DataOutputStream(bout);
        body.writeByte(Protocol.STATUS_OK);
        body.writeLong(System.currentTimeMillis());
        body.flush();
        synchronized (outLock) { new Message(reqId, Protocol.RESPONSE, bout.toByteArray()).writeTo(dout); }
    }

    private void handleAdvanceDay(int reqId, byte[] payload, DataOutputStream dout, Object outLock) throws IOException {
        if (!authenticated.get()) {
            writeError(dout, outLock, reqId, Protocol.STATUS_AUTH_REQUIRED, "Authenticate first");
            return;
        }
        // fecha o dia útil e grava/persiste
        List<Event> toPersist = dayManager.closeCurrentDayAndStartNew();
        int closedDayIndex = dayManager.getDayIndex() - 1; // index of the day we just closed
        try {
            persistenceManager.persistDay(closedDayIndex, toPersist);
        } catch (IOException ioe) {
            writeError(dout, outLock, reqId, Protocol.STATUS_INTERNAL_ERROR, "Persist failed: " + ioe.getMessage());
            return;
        }
        // notificar os waiters
        if (notificationManager != null) notificationManager.signalDayAdvanced();

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream body = new DataOutputStream(bout);
        body.writeByte(Protocol.STATUS_OK);
        body.writeInt(closedDayIndex);
        body.writeLong(System.currentTimeMillis());
        body.flush();
        synchronized (outLock) { new Message(reqId, Protocol.RESPONSE, bout.toByteArray()).writeTo(dout); }
    }

    private void handleAggregation(int reqId, byte op, byte[] payload, DataOutputStream dout, Object outLock) throws IOException {
        if (!authenticated.get()) {
            writeError(dout, outLock, reqId, Protocol.STATUS_AUTH_REQUIRED, "Authenticate first");
            return;
        }
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        String product = IOUtils.readString(in);
        int d = in.readInt();
        try {
            if (op == Protocol.AGG_QUANTITY) {
                int q = aggregationManager.aggregateQuantity(product, d);
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                DataOutputStream body = new DataOutputStream(bout);
                body.writeByte(Protocol.STATUS_OK);
                body.writeInt(q);
                body.flush();
                synchronized (outLock) { new Message(reqId, Protocol.RESPONSE, bout.toByteArray()).writeTo(dout); }
            } else if (op == Protocol.AGG_VOLUME) {
                double v = aggregationManager.aggregateVolume(product, d);
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                DataOutputStream body = new DataOutputStream(bout);
                body.writeByte(Protocol.STATUS_OK);
                body.writeDouble(v);
                body.flush();
                synchronized (outLock) { new Message(reqId, Protocol.RESPONSE, bout.toByteArray()).writeTo(dout); }
            } else if (op == Protocol.AGG_AVG_PRICE) {
                double avg = aggregationManager.aggregateAvgPrice(product, d);
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                DataOutputStream body = new DataOutputStream(bout);
                body.writeByte(Protocol.STATUS_OK);
                body.writeDouble(avg);
                body.flush();
                synchronized (outLock) { new Message(reqId, Protocol.RESPONSE, bout.toByteArray()).writeTo(dout); }
            } else if (op == Protocol.AGG_MAX_PRICE) {
                double max = aggregationManager.aggregateMaxPrice(product, d);
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                DataOutputStream body = new DataOutputStream(bout);
                body.writeByte(Protocol.STATUS_OK);
                body.writeDouble(max);
                body.flush();
                synchronized (outLock) { new Message(reqId, Protocol.RESPONSE, bout.toByteArray()).writeTo(dout); }
            }
        } catch (IOException ioe) {
            writeError(dout, outLock, reqId, Protocol.STATUS_INTERNAL_ERROR, "Aggregation IO error: " + ioe.getMessage());
        }
    }

    private void handleWaitSimultaneous(int reqId, byte[] payload, DataOutputStream dout, Object outLock) throws IOException {
        if (!authenticated.get()) {
            writeError(dout, outLock, reqId, Protocol.STATUS_AUTH_REQUIRED, "Authenticate first");
            return;
        }
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        String p1 = IOUtils.readString(in);
        String p2 = IOUtils.readString(in);
        boolean result = false;
        try {
            result = notificationManager.waitSimultaneous(p1, p2);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            writeError(dout, outLock, reqId, Protocol.STATUS_INTERNAL_ERROR, "Interrupted");
            return;
        }
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream body = new DataOutputStream(bout);
        body.writeByte(Protocol.STATUS_OK);
        body.writeByte(result ? (byte)1 : (byte)0);
        body.flush();
        synchronized (outLock) { new Message(reqId, Protocol.RESPONSE, bout.toByteArray()).writeTo(dout); }
    }

    private void handleWaitConsecutive(int reqId, byte[] payload, DataOutputStream dout, Object outLock) throws IOException {
        if (!authenticated.get()) {
            writeError(dout, outLock, reqId, Protocol.STATUS_AUTH_REQUIRED, "Authenticate first");
            return;
        }
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        int n = in.readInt();
        String productResult = null;
        try {
            productResult = notificationManager.waitConsecutive(n);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            writeError(dout, outLock, reqId, Protocol.STATUS_INTERNAL_ERROR, "Interrupted");
            return;
        }
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream body = new DataOutputStream(bout);
        body.writeByte(Protocol.STATUS_OK);
        if (productResult != null) {
            body.writeByte((byte)1);
            IOUtils.writeString(body, productResult);
        } else {
            body.writeByte((byte)0);
        }
        body.flush();
        synchronized (outLock) { new Message(reqId, Protocol.RESPONSE, bout.toByteArray()).writeTo(dout); }
    }

    private void writeError(DataOutputStream dout, Object outLock, int reqId, byte status, String msg) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream body = new DataOutputStream(bout);
        body.writeByte(status);
        IOUtils.writeString(body, msg);
        body.flush();
        synchronized (outLock) { new Message(reqId, Protocol.RESPONSE, bout.toByteArray()).writeTo(dout); }
    }
}