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

/**
 * ClientAPI: API síncrona sobre ClientConnection.
 * Adaptada para utilizar a nova interface síncrona da ClientConnection sem CompletableFuture.
 */
public class ClientAPI implements AutoCloseable {
    private final ClientConnection conn;

    public ClientAPI(ClientConnection conn) {
        this.conn = conn;
    }

    // O método sendRequest da ClientConnection agora é síncrono e bloqueante.
    // O suporte a timeouts manuais com estas primitivas simples exigiria o uso de awaitNanos,
    // mas seguindo a lógica de simplificação para SD, o envio passa a ser direto.
    private Message sendAndWait(byte opCode, byte[] payload) throws IOException {
        return conn.sendRequest(opCode, payload);
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

    public boolean register(String username, String password) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        IOUtils.writeString(dout, username);
        IOUtils.writeString(dout, password);
        dout.flush();

        Message resp = sendAndWait(Protocol.REGISTER, bout.toByteArray());
        if (resp == null) throw new IOException("No response from server");

        DataInputStream din = payloadStream(resp);
        byte status = din.readByte();
        if (status == Protocol.STATUS_OK) return true;

        String msg = IOUtils.readString(din);
        throw new IOException("Register failed: status=" + status + " msg=" + msg);
    }

    public boolean login(String username, String password) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        IOUtils.writeString(dout, username);
        IOUtils.writeString(dout, password);
        dout.flush();

        Message resp = sendAndWait(Protocol.LOGIN, bout.toByteArray());
        if (resp == null) throw new IOException("No response from server");

        DataInputStream din = payloadStream(resp);
        ensureStatusOk(din);
        return true;
    }

    public long addEvent(String product, int qty, double price, long timestamp) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        IOUtils.writeString(dout, product);
        dout.writeInt(qty);
        dout.writeDouble(price);
        dout.writeLong(timestamp);
        dout.flush();

        Message resp = sendAndWait(Protocol.ADD_EVENT, bout.toByteArray());
        if (resp == null) throw new IOException("No response from server");

        DataInputStream din = payloadStream(resp);
        ensureStatusOk(din);
        return din.readLong();
    }

    public int advanceDay() throws IOException {
        Message resp = sendAndWait(Protocol.ADVANCE_DAY, new byte[0]);
        if (resp == null) throw new IOException("No response from server");

        DataInputStream din = payloadStream(resp);
        ensureStatusOk(din);
        return din.readInt();
    }

    public int aggregateQuantity(String product, int d) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        IOUtils.writeString(dout, product);
        dout.writeInt(d);
        dout.flush();

        Message resp = sendAndWait(Protocol.AGG_QUANTITY, bout.toByteArray());
        if (resp == null) throw new IOException("No response from server");

        DataInputStream din = payloadStream(resp);
        ensureStatusOk(din);
        return din.readInt();
    }

    public double aggregateVolume(String product, int d) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        IOUtils.writeString(dout, product);
        dout.writeInt(d);
        dout.flush();

        Message resp = sendAndWait(Protocol.AGG_VOLUME, bout.toByteArray());
        if (resp == null) throw new IOException("No response from server");

        DataInputStream din = payloadStream(resp);
        ensureStatusOk(din);
        return din.readDouble();
    }

    public double aggregateAvgPrice(String product, int d) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        IOUtils.writeString(dout, product);
        dout.writeInt(d);
        dout.flush();

        Message resp = sendAndWait(Protocol.AGG_AVG_PRICE, bout.toByteArray());
        if (resp == null) throw new IOException("No response from server");

        DataInputStream din = payloadStream(resp);
        ensureStatusOk(din);
        return din.readDouble();
    }

    public double aggregateMaxPrice(String product, int d) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        IOUtils.writeString(dout, product);
        dout.writeInt(d);
        dout.flush();

        Message resp = sendAndWait(Protocol.AGG_MAX_PRICE, bout.toByteArray());
        if (resp == null) throw new IOException("No response from server");

        DataInputStream din = payloadStream(resp);
        ensureStatusOk(din);
        return din.readDouble();
    }

    public boolean waitSimultaneous(String p1, String p2) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        IOUtils.writeString(dout, p1);
        IOUtils.writeString(dout, p2);
        dout.flush();

        Message resp = sendAndWait(Protocol.WAIT_SIMULTANEOUS, bout.toByteArray());
        if (resp == null) throw new IOException("No response from server");

        DataInputStream din = payloadStream(resp);
        ensureStatusOk(din);
        return din.readByte() == 1;
    }

    public String waitConsecutive(int n) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        dout.writeInt(n);
        dout.flush();

        Message resp = sendAndWait(Protocol.WAIT_CONSECUTIVE, bout.toByteArray());
        if (resp == null) throw new IOException("No response from server");

        DataInputStream din = payloadStream(resp);
        ensureStatusOk(din);
        byte occurred = din.readByte();
        return (occurred == 1) ? IOUtils.readString(din) : null;
    }

    @Override
    public void close() throws Exception {
        conn.close();
    }
}