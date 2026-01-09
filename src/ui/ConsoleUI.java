package ui;

import client.ClientAPI;
import client.ClientConnection;

import java.time.Instant;
import java.util.Scanner;

public class ConsoleUI {
    private final String host;
    private final int port;

    public ConsoleUI(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void run() {
        try (Scanner sc = new Scanner(System.in)) {
            // ClientConnection e ClientAPI agora gerem a comunicação de forma simples e síncrona
            try (ClientConnection conn = new ClientConnection(host, port);
                 ClientAPI api = new ClientAPI(conn)) {

                System.out.println("Connected to server " + host + ":" + port);

                boolean running = true;
                while (running) {
                    printMenu();
                    System.out.print("> ");
                    String line = sc.nextLine().trim();
                    if (line.isEmpty()) continue;

                    switch (line) {
                        case "1": // register
                            System.out.print("username: ");
                            String ru = sc.nextLine().trim();
                            System.out.print("password: ");
                            String rp = sc.nextLine().trim();
                            try {
                                boolean ok = api.register(ru, rp);
                                System.out.println("Register status: " + ok);
                            } catch (Exception e) {
                                System.err.println("Register failed: " + e.getMessage());
                            }
                            break;
                        case "2": // login
                            System.out.print("username: ");
                            String lu = sc.nextLine().trim();
                            System.out.print("password: ");
                            String lp = sc.nextLine().trim();
                            try {
                                boolean ok = api.login(lu, lp);
                                System.out.println("Login successful: " + ok);
                            } catch (Exception e) {
                                System.err.println("Login failed: " + e.getMessage());
                            }
                            break;
                        case "3": // add event
                            System.out.print("product: ");
                            String p = sc.nextLine().trim();
                            System.out.print("quantity: ");
                            int q = Integer.parseInt(sc.nextLine().trim());
                            System.out.print("price: ");
                            double price = Double.parseDouble(sc.nextLine().trim());
                            long ts = Instant.now().toEpochMilli();
                            try {
                                long ack = api.addEvent(p, q, price, ts);
                                System.out.println("Event added, server ackTime=" + ack);
                            } catch (Exception e) {
                                System.err.println("addEvent failed: " + e.getMessage());
                            }
                            break;
                        case "4": // advance day
                            try {
                                int idx = api.advanceDay();
                                System.out.println("Day advanced. Closed day index: " + idx);
                            } catch (Exception e) {
                                System.err.println("advanceDay failed: " + e.getMessage());
                            }
                            break;
                        case "5": // agg quantity
                            System.out.print("product: ");
                            String ap = sc.nextLine().trim();
                            System.out.print("d (days): ");
                            int ad = Integer.parseInt(sc.nextLine().trim());
                            try {
                                int res = api.aggregateQuantity(ap, ad);
                                System.out.println("Total Quantity (last " + ad + " days): " + res);
                            } catch (Exception e) {
                                System.err.println("Aggregation failed: " + e.getMessage());
                            }
                            break;
                        case "6": // agg volume
                            System.out.print("product: ");
                            String avp = sc.nextLine().trim();
                            System.out.print("d (days): ");
                            int avd = Integer.parseInt(sc.nextLine().trim());
                            try {
                                double res = api.aggregateVolume(avp, avd);
                                System.out.println("Total Volume (last " + avd + " days): " + res);
                            } catch (Exception e) {
                                System.err.println("Aggregation failed: " + e.getMessage());
                            }
                            break;
                        case "7": // agg avg price
                            System.out.print("product: ");
                            String aap = sc.nextLine().trim();
                            System.out.print("d (days): ");
                            int aad = Integer.parseInt(sc.nextLine().trim());
                            try {
                                double res = api.aggregateAvgPrice(aap, aad);
                                System.out.println("Average Price (last " + aad + " days): " + res);
                            } catch (Exception e) {
                                System.err.println("Aggregation failed: " + e.getMessage());
                            }
                            break;
                        case "8": // agg max price
                            System.out.print("product: ");
                            String amp = sc.nextLine().trim();
                            System.out.print("d (days): ");
                            int amd = Integer.parseInt(sc.nextLine().trim());
                            try {
                                double res = api.aggregateMaxPrice(amp, amd);
                                System.out.println("Maximum Price (last " + amd + " days): " + res);
                            } catch (Exception e) {
                                System.err.println("Aggregation failed: " + e.getMessage());
                            }
                            break;
                        case "9": // filter events on day d
                            System.out.print("How many products: ");
                            int filn = Integer.parseInt(sc.nextLine().trim());
                            System.out.print("products: ");
                            String filp = sc.nextLine().trim();
                            System.out.print("d (day): ");
                            int fild = Integer.parseInt(sc.nextLine().trim());
                            try {
                                String res = api.filterByDay(filn, filp, fild);
                                System.out.println("Events mentioning " + filp +" on day " + fild + ":\n" + res);
                            } catch (Exception e) {
                                System.err.println("Filter failed: " + e.getMessage());
                            }
                            break;
                        case "10": // wait simultaneous
                            System.out.print("product1: ");
                            String w1 = sc.nextLine().trim();
                            System.out.print("product2: ");
                            String w2 = sc.nextLine().trim();
                            try {
                                System.out.println("Waiting for simultaneous events...");
                                boolean res = api.waitSimultaneous(w1, w2);
                                System.out.println("waitSimultaneous occurred: " + res);
                            } catch (Exception e) {
                                System.err.println("waitSimultaneous failed: " + e.getMessage());
                            }
                            break;
                        case "11": // wait consecutive
                            System.out.print("n (consecutive sales): ");
                            int n = Integer.parseInt(sc.nextLine().trim());
                            try {
                                System.out.println("Waiting for consecutive sales...");
                                String prod = api.waitConsecutive(n);
                                if (prod != null) System.out.println("Occurred for product: " + prod);
                                else System.out.println("Did not occur before day end.");
                            } catch (Exception e) {
                                System.err.println("waitConsecutive failed: " + e.getMessage());
                            }
                            break;
                        case "x":
                        case "exit":
                            running = false;
                            break;
                        default:
                            System.out.println("Unknown option");
                    }
                }
            } catch (Exception e) {
                System.err.println("Connection error: " + e.getMessage());
            }
        }
    }

    private void printMenu() {
        System.out.println("\n=== TSDB CONSOLE UI ===");
        System.out.println("1) Register          2) Login");
        System.out.println("3) Add Event         4) Advance Day");
        System.out.println("5) Agg: Quantity     6) Agg: Volume");
        System.out.println("7) Agg: Avg Price    8) Agg: Max Price");
        System.out.println("9) Filter Events     10) Wait Simultaneous");
        System.out.println("11) Wait Consecutive x) Exit");
    }

    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 12345;
        if (args.length >= 1) host = args[0];
        if (args.length >= 2) port = Integer.parseInt(args[1]);
        new ConsoleUI(host, port).run();
    }
}