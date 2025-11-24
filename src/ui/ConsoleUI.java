package ui;

import client.ClientAPI;
import client.ClientConnection;

import java.time.Instant;
import java.util.Scanner;
import java.util.concurrent.TimeoutException;

/**
 ConsoleUI - interface de consola simples para testar o servidor via ClientAPI.
 */
public class ConsoleUI {
    private final String host;
    private final int port;

    public ConsoleUI(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void run() {
        try (Scanner sc = new Scanner(System.in)) {
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
                                System.out.println("Register: " + ok);
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
                                System.out.println("Login: " + ok);
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
                                System.out.println("Event added, ackTime=" + ack);
                            } catch (Exception e) {
                                System.err.println("addEvent failed: " + e.getMessage());
                            }
                            break;
                        case "4": // advance day
                            try {
                                int idx = api.advanceDay();
                                System.out.println("Advanced. Closed day index: " + idx);
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
                                System.out.println("Quantity(last " + ad + "): " + res);
                            } catch (Exception e) {
                                System.err.println("aggregateQuantity failed: " + e.getMessage());
                            }
                            break;
                        case "6": // agg volume
                            System.out.print("product: ");
                            String avp = sc.nextLine().trim();
                            System.out.print("d (days): ");
                            int avd = Integer.parseInt(sc.nextLine().trim());
                            try {
                                double res = api.aggregateVolume(avp, avd);
                                System.out.println("Volume(last " + avd + "): " + res);
                            } catch (Exception e) {
                                System.err.println("aggregateVolume failed: " + e.getMessage());
                            }
                            break;
                        case "7": // agg avg price
                            System.out.print("product: ");
                            String aap = sc.nextLine().trim();
                            System.out.print("d (days): ");
                            int aad = Integer.parseInt(sc.nextLine().trim());
                            try {
                                double res = api.aggregateAvgPrice(aap, aad);
                                System.out.println("AvgPrice(last " + aad + "): " + res);
                            } catch (Exception e) {
                                System.err.println("aggregateAvgPrice failed: " + e.getMessage());
                            }
                            break;
                        case "8": // agg max price
                            System.out.print("product: ");
                            String amp = sc.nextLine().trim();
                            System.out.print("d (days): ");
                            int amd = Integer.parseInt(sc.nextLine().trim());
                            try {
                                double res = api.aggregateMaxPrice(amp, amd);
                                System.out.println("MaxPrice(last " + amd + "): " + res);
                            } catch (Exception e) {
                                System.err.println("aggregateMaxPrice failed: " + e.getMessage());
                            }
                            break;
                        case "9": // wait simultaneous
                            System.out.print("product1: ");
                            String w1 = sc.nextLine().trim();
                            System.out.print("product2: ");
                            String w2 = sc.nextLine().trim();
                            System.out.print("timeoutSeconds (0 = default): ");
                            long wt = Long.parseLong(sc.nextLine().trim());
                            try {
                                boolean res;
                                if (wt <= 0) res = api.waitSimultaneous(w1, w2);
                                else res = api.waitSimultaneous(w1, w2, wt * 1000);
                                System.out.println("waitSimultaneous result: " + res);
                            } catch (TimeoutException te) {
                                System.err.println("Timeout waiting: " + te.getMessage());
                            } catch (Exception e) {
                                System.err.println("waitSimultaneous failed: " + e.getMessage());
                            }
                            break;
                        case "10": // wait consecutive
                            System.out.print("n: ");
                            int n = Integer.parseInt(sc.nextLine().trim());
                            System.out.print("timeoutSeconds (0 = default): ");
                            long cto = Long.parseLong(sc.nextLine().trim());
                            try {
                                String prod;
                                if (cto <= 0) prod = api.waitConsecutive(n);
                                else prod = api.waitConsecutive(n, cto * 1000);
                                if (prod != null) System.out.println("Occurred for product: " + prod);
                                else System.out.println("Did not occur before day end");
                            } catch (TimeoutException te) {
                                System.err.println("Timeout waiting: " + te.getMessage());
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
        System.out.println();
        System.out.println("=== Console UI ===");
        System.out.println("1) Register");
        System.out.println("2) Login");
        System.out.println("3) Add event");
        System.out.println("4) Advance day");
        System.out.println("5) Aggregate - Quantity");
        System.out.println("6) Aggregate - Volume");
        System.out.println("7) Aggregate - AvgPrice");
        System.out.println("8) Aggregate - MaxPrice");
        System.out.println("9) Wait Simultaneous");
        System.out.println("10) Wait Consecutive");
        System.out.println("x) Exit");
    }

    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 12345;
        if (args.length >= 1) host = args[0];
        if (args.length >= 2) port = Integer.parseInt(args[1]);
        new ConsoleUI(host, port).run();
    }
}