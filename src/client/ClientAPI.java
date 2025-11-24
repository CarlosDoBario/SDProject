package client;

import common.IOUtils;
import common.Message;
import common.Protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 ClientAPI: API síncrona sobre ClientConnection.
 */
public class ClientAPI implements AutoCloseable {
    private final ClientConnection conn;
    private final long defaultTimeoutMillis;

    public ClientAPI(ClientConnection conn) {
        this(conn, Duration.ofSeconds(30).toMillis());
    }

    public ClientAPI(ClientConnection conn, long defaultTimeoutMillis) {
        this.conn = conn;
        this.defaultTimeoutMillis = defaultTimeoutMillis;
    }

    private Message sendAndWait(byte opCode, byte[] payload, long timeoutMillis) throws IOException, TimeoutException, InterruptedException {
        try {
            CompletableFuture<Message> f = conn.sendRequest(opCode, payload);
            return f.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw new IOException("Execution error", cause);
        }
    }

    private Message sendAndWait(byte opCode, byte[] payload) throws IOException, TimeoutException, InterruptedException {
        return sendAndWait(opCode, payload, defaultTimeoutMillis);
    }

    private DataInputStream payloadStream(Message resp) {
        return new DataInputStream(new ByteArrayInputStream(resp.getPayload()));
    }

    private void ensureStatusOk(DataInputStream din) throws IOException {
        byte status = din.readByte();
        if (status != Protocol.STATUS_OK) {
            String msg = IOUtils.readString(din);
            throw new IOException("Server error status=" + status + " msg=" + msg);
        }
    }

    public boolean register(String username, String password) throws IOException, TimeoutException, InterruptedException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        IOUtils.writeString(dout, username);
        IOUtils.writeString(dout, password);
        dout.flush();

        Message resp = sendAndWait(Protocol.REGISTER, bout.toByteArray());
        DataInputStream din = payloadStream(resp);
        byte status = din.readByte();
        if (status == Protocol.STATUS_OK) return true;
        String msg = IOUtils.readString(din);
        throw new IOException("Register failed: status=" + status + " msg=" + msg);
    }

    public boolean login(String username, String password) throws IOException, TimeoutException, InterruptedException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        IOUtils.writeString(dout, username);
        IOUtils.writeString(dout, password);
        dout.flush();

        Message resp = sendAndWait(Protocol.LOGIN, bout.toByteArray());
        DataInputStream din = payloadStream(resp);
        ensureStatusOk(din);
        // String welcome = IOUtils.readString(din);
        return true;
    }

    public long addEvent(String product, int qty, double price, long timestamp) throws IOException, TimeoutException, InterruptedException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        IOUtils.writeString(dout, product);
        dout.writeInt(qty);
        dout.writeDouble(price);
        dout.writeLong(timestamp);
        dout.flush();

        Message resp = sendAndWait(Protocol.ADD_EVENT, bout.toByteArray());
        DataInputStream din = payloadStream(resp);
        ensureStatusOk(din);
        long ackTime = din.readLong();
        return ackTime;
    }

    public int advanceDay() throws IOException, TimeoutException, InterruptedException {
        Message resp = sendAndWait(Protocol.ADVANCE_DAY, new byte[0]);
        DataInputStream din = payloadStream(resp);
        ensureStatusOk(din);
        int newDayIndex = din.readInt();
        // long ts = din.readLong();
        return newDayIndex;
    }

    public int aggregateQuantity(String product, int d) throws IOException, TimeoutException, InterruptedException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        IOUtils.writeString(dout, product);
        dout.writeInt(d);
        dout.flush();

        Message resp = sendAndWait(Protocol.AGG_QUANTITY, bout.toByteArray());
        DataInputStream din = payloadStream(resp);
        ensureStatusOk(din);
        return din.readInt();
    }

    public double aggregateVolume(String product, int d) throws IOException, TimeoutException, InterruptedException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        IOUtils.writeString(dout, product);
        dout.writeInt(d);
        dout.flush();

        Message resp = sendAndWait(Protocol.AGG_VOLUME, bout.toByteArray());
        DataInputStream din = payloadStream(resp);
        ensureStatusOk(din);
        return din.readDouble();
    }

    public double aggregateAvgPrice(String product, int d) throws IOException, TimeoutException, InterruptedException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        IOUtils.writeString(dout, product);
        dout.writeInt(d);
        dout.flush();

        Message resp = sendAndWait(Protocol.AGG_AVG_PRICE, bout.toByteArray());
        DataInputStream din = payloadStream(resp);
        ensureStatusOk(din);
        return din.readDouble();
    }

    public double aggregateMaxPrice(String product, int d) throws IOException, TimeoutException, InterruptedException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        IOUtils.writeString(dout, product);
        dout.writeInt(d);
        dout.flush();

        Message resp = sendAndWait(Protocol.AGG_MAX_PRICE, bout.toByteArray());
        DataInputStream din = payloadStream(resp);
        ensureStatusOk(din);
        return din.readDouble();
    }

    public boolean waitSimultaneous(String p1, String p2, long timeoutMillis) throws IOException, TimeoutException, InterruptedException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        IOUtils.writeString(dout, p1);
        IOUtils.writeString(dout, p2);
        dout.flush();

        Message resp = sendAndWait(Protocol.WAIT_SIMULTANEOUS, bout.toByteArray(), timeoutMillis);
        DataInputStream din = payloadStream(resp);
        ensureStatusOk(din);
        byte result = din.readByte();
        return result == 1;
    }

    public boolean waitSimultaneous(String p1, String p2) throws IOException, TimeoutException, InterruptedException {
        return waitSimultaneous(p1, p2, defaultTimeoutMillis);
    }

    public String waitConsecutive(int n, long timeoutMillis) throws IOException, TimeoutException, InterruptedException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        dout.writeInt(n);
        dout.flush();

        Message resp = sendAndWait(Protocol.WAIT_CONSECUTIVE, bout.toByteArray(), timeoutMillis);
        DataInputStream din = payloadStream(resp);
        ensureStatusOk(din);
        byte occurred = din.readByte();
        if (occurred == 1) {
            return IOUtils.readString(din);
        } else {
            return null;
        }
    }

    public String waitConsecutive(int n) throws IOException, TimeoutException, InterruptedException {
        return waitConsecutive(n, defaultTimeoutMillis);
    }

    @Override
    public void close() throws Exception {
        conn.close();
    }
}