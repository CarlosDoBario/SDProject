package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 Servidor principal: aceita ligações e cria handlers
 Cria e liga os managers principais
 */
public class MainServer {
    private final int port;
    private final ExecutorService acceptPool = Executors.newCachedThreadPool();

    private final AuthManager authManager = new AuthManager();
    private final DayManager dayManager = new DayManager();
    private final PersistenceManager persistenceManager = new PersistenceManager();
    private final AggregationManager aggregationManager = new AggregationManager(dayManager, persistenceManager);
    private final NotificationManager notificationManager = new NotificationManager(dayManager);

    public MainServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        try (ServerSocket ss = new ServerSocket(port)) {
            System.out.println("Server started on port " + port);
            while (true) {
                Socket client = ss.accept();
                System.out.println("Accepted connection from " + client.getRemoteSocketAddress());
                ConnectionHandler handler = new ConnectionHandler(
                        client, authManager, dayManager, persistenceManager, aggregationManager, notificationManager);
                acceptPool.submit(handler);
            }
        } finally {
            acceptPool.shutdown();
        }
    }

    public static void main(String[] args) throws IOException {
        int port = 12345;
        if (args.length > 0) port = Integer.parseInt(args[0]);
        MainServer server = new MainServer(port);
        server.start();
    }
}