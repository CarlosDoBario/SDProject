package client;

import common.Message;
import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ClientConnection implements Closeable {
    private final Socket socket;
    private final DataOutputStream out;
    private final DataInputStream in;

    private final ReentrantLock lock = new ReentrantLock();
    private final ReentrantLock writeLock = new ReentrantLock();
    private final Condition responseArrived = lock.newCondition();

    private final Map<Integer, Message> responses = new HashMap<>();
    private int requestCounter = 1;
    private boolean running = true;

    public ClientConnection(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

        Thread readerThread = new Thread(this::readerLoop);
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readerLoop() {
        try {
            while (running) {
                Message msg = Message.readFrom(in);
                if (msg == null) break;

                lock.lock();
                try {
                    responses.put(msg.getRequestId(), msg);
                    responseArrived.signalAll();
                } finally {
                    lock.unlock();
                }
            }
        } catch (IOException ignored) {
        } finally {
            closeSilently();
        }
    }

    public Message sendRequest(byte opCode, byte[] payload) throws IOException {
        int reqId;
        lock.lock();
        try {
            reqId = requestCounter++;
        } finally {
            lock.unlock();
        }

        Message req = new Message(reqId, opCode, payload);

        writeLock.lock();
        try {
            req.writeTo(out);
            out.flush();
        } finally {
            writeLock.unlock();
        }

        lock.lock();
        try {
            while (!responses.containsKey(reqId) && running) {
                responseArrived.await();
            }
            return responses.remove(reqId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted");
        } finally {
            lock.unlock();
        }
    }

    private void closeSilently() {
        lock.lock();
        try {
            running = false;
            responseArrived.signalAll();
            socket.close();
        } catch (IOException ignored) {}
        finally { lock.unlock(); }
    }

    @Override
    public void close() throws IOException {
        closeSilently();
    }
}