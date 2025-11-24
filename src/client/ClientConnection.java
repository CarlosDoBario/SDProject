package client;

import common.Message;
import common.Protocol;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 - Gerencia uma única ligação TCP ao servidor.
 - Suporta multiplexação de pedidos/respostas usando requestId (ConcurrentHashMap de pending futures).
 - Leitor dedicado (reader thread) que faz dispatch das respostas para os CompletableFutures registados.
 - Escritas sincronizadas via writeLock para evitar interleaving de bytes.
 */
public class ClientConnection implements Closeable {
    private final Socket socket;
    private final DataOutputStream out;
    private final DataInputStream in;
    private final Object writeLock = new Object();

    private final ConcurrentHashMap<Integer, CompletableFuture<Message>> pending = new ConcurrentHashMap<>();
    private final AtomicInteger requestCounter = new AtomicInteger(1);

    private final Thread readerThread;
    private volatile boolean running = true;

    // Push handler opcional para requestId == 0 messages
    private volatile Consumer<Message> pushHandler = null;

    public ClientConnection(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.out = new DataOutputStream(socket.getOutputStream());
        this.in = new DataInputStream(socket.getInputStream());

        this.readerThread = new Thread(this::readerLoop, "client-reader");
        this.readerThread.setDaemon(true);
        this.readerThread.start();
    }

    private void readerLoop() {
        try {
            while (running && !socket.isClosed()) {
                Message msg;
                try {
                    msg = Message.readFrom(in);
                } catch (IOException ioe) {
                    failAllPending(ioe);
                    break;
                }
                if (msg == null) {
                    // EOF
                    failAllPending(new IOException("Stream closed by server"));
                    break;
                }

                int reqId = msg.getRequestId();
                if (reqId == 0) {
                    Consumer<Message> h = pushHandler;
                    if (h != null) {
                        try { h.accept(msg); } catch (Throwable t) { /* swallow */ }
                    } else {
                        System.out.println("Push received: " + msg);
                    }
                } else {
                    CompletableFuture<Message> f = pending.remove(reqId);
                    if (f != null) {
                        f.complete(msg);
                    } else {
                        // mensagem inesperada: ignorar ou log
                        System.err.println("No pending request for requestId=" + reqId + ", op=" + msg.getOpCode());
                    }
                }
            }
        } finally {
            running = false;
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void failAllPending(Throwable cause) {
        running = false;
        for (var entry : pending.entrySet()) {
            entry.getValue().completeExceptionally(cause);
        }
        pending.clear();
    }

    // Envia um pedido e devolve um CompletableFuture que será completado quando a resposta chegar.
    public CompletableFuture<Message> sendRequest(byte opCode, byte[] payload) throws IOException {
        int reqId = nextRequestId();
        CompletableFuture<Message> future = new CompletableFuture<>();
        // regista antes de enviar para evitar race
        pending.put(reqId, future);

        Message msg = new Message(reqId, opCode, payload);
        try {
            synchronized (writeLock) {
                msg.writeTo(out);
            }
        } catch (IOException ioe) {
            // remove pending e propaga exceção
            pending.remove(reqId);
            future.completeExceptionally(ioe);
            throw ioe;
        }
        return future;
    }

    private int nextRequestId() {
        return requestCounter.getAndIncrement();
    }

    // Regista um handler para mensagens push (requestId == 0). Handler é chamado na thread do reader.
    public void setPushHandler(Consumer<Message> handler) {
        this.pushHandler = handler;
    }

    @Override
    public void close() throws IOException {
        running = false;
        try { socket.close(); } catch (IOException ignored) {}
        // complete pending with exception
        failAllPending(new IOException("Connection closed"));
        try {
            readerThread.join(200);
        } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}