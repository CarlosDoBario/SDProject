package server;

import server.model.Event;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class AggregationManager {
    private final DayManager dayManager;
    private final PersistenceManager persistenceManager;

    private final int D;
    private final int S;

    private static final class PerDayAgg {
        int quantity = 0;
        double volume = 0.0;
        double maxPrice = 0.0;
        int countEvents = 0;

        void incorporate(Event e) {
            int q = e.getQuantity();
            double p = e.getPrice();
            quantity += q;
            volume += p * q;
            if (countEvents == 0 || p > maxPrice) maxPrice = p;
            countEvents++;
        }
    }

    private final ReentrantLock cacheLock = new ReentrantLock();
    private final Condition loadFinished = cacheLock.newCondition();

    private final LinkedHashMap<Integer, Map<String, PerDayAgg>> dayCache = new LinkedHashMap<>(16, 0.75f, true);
    private final java.util.HashSet<Integer> loadingDays = new java.util.HashSet<>();

    public AggregationManager(DayManager dayManager, PersistenceManager persistenceManager) {
        this(7, 3, dayManager, persistenceManager);
    }

    public AggregationManager(int D, int S, DayManager dayManager, PersistenceManager persistenceManager) {
        if (D <= 0) throw new IllegalArgumentException("D must be > 0");
        if (S < 0) throw new IllegalArgumentException("S must be >= 0");
        this.D = D;
        this.S = S;
        this.dayManager = dayManager;
        this.persistenceManager = persistenceManager;
    }

    private PerDayAgg getPerDayAggForProduct(int dayIndex, String product) throws IOException {
        cacheLock.lock();
        try {
            while (true) {
                Map<String, PerDayAgg> map = dayCache.get(dayIndex);
                if (map != null) {
                    PerDayAgg p = map.get(product);
                    if (p != null) return p;
                }

                if (!loadingDays.contains(dayIndex)) {
                    loadingDays.add(dayIndex);
                    break;
                } else {
                    try {
                        loadFinished.await();
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return new PerDayAgg();
                    }
                }
            }
        } finally {
            cacheLock.unlock();
        }

        PerDayAgg agg = new PerDayAgg();
        try {
            if (persistenceManager.dayExists(dayIndex)) {
                persistenceManager.streamDay(dayIndex, e -> {
                    if (product.equals(e.getProductName())) {
                        agg.incorporate(e);
                    }
                });
            }
        } finally {
            cacheLock.lock();
            try {
                loadingDays.remove(dayIndex);
                if (S > 0) {
                    Map<String, PerDayAgg> map = dayCache.get(dayIndex);
                    if (map == null) {
                        while (dayCache.size() >= S) {
                            Iterator<Integer> it = dayCache.keySet().iterator();
                            if (it.hasNext()) {
                                it.next();
                                it.remove();
                            } else break;
                        }
                        map = new HashMap<>();
                        dayCache.put(dayIndex, map);
                    }
                    map.put(product, agg);
                }
                loadFinished.signalAll();
            } finally {
                cacheLock.unlock();
            }
        }

        return agg;
    }

    private int[] targetDays(int d) {
        if (d <= 0) throw new IllegalArgumentException("d must be >= 1");
        if (d > D) d = D;
        int current = dayManager.getDayIndex();
        int actual = Math.min(d, current);
        int[] days = new int[actual];
        for (int i = 0; i < actual; i++) {
            days[i] = current - 1 - i;
        }
        return days;
    }

    public int aggregateQuantity(String productName, int d) throws IOException {
        int total = 0;
        for (int day : targetDays(d)) {
            total += getPerDayAggForProduct(day, productName).quantity;
        }
        return total;
    }

    public double aggregateVolume(String productName, int d) throws IOException {
        double total = 0.0;
        for (int day : targetDays(d)) {
            total += getPerDayAggForProduct(day, productName).volume;
        }
        return total;
    }

    public double aggregateAvgPrice(String productName, int d) throws IOException {
        long totalQty = 0;
        double totalVolume = 0.0;
        for (int day : targetDays(d)) {
            PerDayAgg p = getPerDayAggForProduct(day, productName);
            totalQty += p.quantity;
            totalVolume += p.volume;
        }
        return (totalQty == 0) ? 0.0 : totalVolume / totalQty;
    }

    public double aggregateMaxPrice(String productName, int d) throws IOException {
        double max = 0.0;
        boolean any = false;
        for (int day : targetDays(d)) {
            PerDayAgg p = getPerDayAggForProduct(day, productName);
            if (p.countEvents > 0) {
                if (!any || p.maxPrice > max) max = p.maxPrice;
                any = true;
            }
        }
        return any ? max : 0.0;
    }

    public void clearCache() {
        cacheLock.lock();
        try {
            dayCache.clear();
        } finally {
            cacheLock.unlock();
        }
    }
}