package sd.timeseries.common;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;

/**
 Representação simples de uma mensagem do protocolo com utilitários de leitura/escrita.

 Formato do protocolo:
   - totalLength: int (4)  -> nº de bytes seguintes (requestId + opCode + payload)
   - requestId:   int (4)
   - opCode:      byte (1)
   - payload:     bytes restantes (totalLength - 5)
 */
public final class Message {
    private final int requestId;
    private final byte opCode;
    private final byte[] payload;

    public Message(int requestId, byte opCode, byte[] payload) {
        this.requestId = requestId;
        this.opCode = opCode;
        this.payload = payload == null ? new byte[0] : payload;
    }

    public int getRequestId() {
        return requestId;
    }

    public byte getOpCode() {
        return opCode;
    }

    public byte[] getPayload() {
        return payload;
    }

    /**
     * Lê uma mensagem completa do stream seguindo o envelope definido.
     * Retorna null se EOF for encontrado antes de ler totalLength (caller decide comportamento).
     */
    public static Message readFrom(DataInputStream in) throws IOException {
        int totalLength;
        try {
            totalLength = in.readInt();
        } catch (EOFException eof) {
            return null;
        }
        // mínimo: requestId(4) + opCode(1) = 5 bytes
        if (totalLength < 5) {
            throw new IOException("Invalid message totalLength: " + totalLength);
        }
        int requestId = in.readInt();
        byte opCode = in.readByte();
        int payloadLen = totalLength - 5;
        byte[] payload = new byte[Math.max(0, payloadLen)];
        if (payloadLen > 0) {
            in.readFully(payload);
        }
        return new Message(requestId, opCode, payload);
    }

    /**
     * Escreve a mensagem completa para o stream (framing incluído).
     * Síncrono: caller é responsável por sincronizar escritas no OutputStream se houver concorrência.
     */
    public void writeTo(DataOutputStream out) throws IOException {
        int bodyLen = payload == null ? 0 : payload.length;
        int totalLen = 4 + 1 + bodyLen; // requestId + opCode + payload
        out.writeInt(totalLen);
        out.writeInt(requestId);
        out.writeByte(opCode);
        if (bodyLen > 0) out.write(payload);
        out.flush();
    }

    @Override
    public String toString() {
        return "Message{requestId=" + requestId + ", opCode=0x" + String.format("%02X", opCode)
                + ", payloadLen=" + (payload == null ? 0 : payload.length) + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Message)) return false;
        Message m = (Message) o;
        return requestId == m.requestId && opCode == m.opCode && Arrays.equals(payload, m.payload);
    }

    @Override
    public int hashCode() {
        int h = Integer.hashCode(requestId);
        h = 31 * h + Byte.hashCode(opCode);
        h = 31 * h + Arrays.hashCode(payload);
        return h;
    }
}