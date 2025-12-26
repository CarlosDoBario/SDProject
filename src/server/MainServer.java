package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

public class MainServer {
    private final int port;

    public MainServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        PersistenceManager pm = new PersistenceManager("data");
        AuthManager auth = new AuthManager("data/users.bin");

        // Lógica para recuperar o dia atual:
        // Verifica todos os ficheiros day-X.bin e escolhe o índice seguinte ao maior encontrado
        List<Integer> persistedDays = pm.listPersistedDays();
        int lastDay = -1;
        for (int d : persistedDays) {
            if (d > lastDay) lastDay = d;
        }
        int nextDayIndex = lastDay + 1;

        DayManager dm = new DayManager(nextDayIndex);
        AggregationManager am = new AggregationManager(dm, pm);
        NotificationManager nm = new NotificationManager(dm);

        System.out.println("Servidor iniciado no porto " + port + ". Dia atual: " + nextDayIndex);

        try (ServerSocket ss = new ServerSocket(port)) {
            while (true) {
                Socket client = ss.accept();
                ConnectionHandler handler = new ConnectionHandler(client, auth, dm, pm, am, nm);
                new Thread(handler).start(); // Thread manual por conexão
            }
        }
    }

    public static void main(String[] args) throws IOException {
        new MainServer(12345).start();
    }
}