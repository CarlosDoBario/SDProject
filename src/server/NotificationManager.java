package server;

import server.model.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class NotificationManager {
    private final ReentrantLock lock = new ReentrantLock();
    private int dayGeneration = 0;

    private final Set<String> seenProducts = new HashSet<>();
    private String lastProduct = null;
    private int streak = 0;

    private static final class SimWaiter {
        final String p1;
        final String p2;
        final Condition cond;
        boolean done = false;
        boolean result = false;

        SimWaiter(String p1, String p2, Condition cond) {
            this.p1 = p1;
            this.p2 = p2;
            this.cond = cond;
        }
    }

    private static final class ConsWaiter {
        final int n;
        final Condition cond;
        boolean done = false;
        String productResult = null;

        String baselineLastProduct;
        int baselineStreak;

        ConsWaiter(int n, Condition cond, String baselineLastProduct, int baselineStreak) {
            this.n = n;
            this.cond = cond;
            this.baselineLastProduct = baselineLastProduct;
            this.baselineStreak = baselineStreak;
        }
    }

    private final List<SimWaiter> simWaiters = new ArrayList<>();
    private final List<ConsWaiter> consWaiters = new ArrayList<>();

    public NotificationManager(DayManager dayManager) {
        if (dayManager != null) {
            dayManager.addEventListener(this::onEvent);
        }
    }

    public void onEvent(Event e) {
        lock.lock();
        try {
            String prod = e.getProductName();
            boolean wasNew = seenProducts.add(prod);

            if (prod.equals(lastProduct)) {
                streak++;
            } else {
                lastProduct = prod;
                streak = 1;
            }

            if (wasNew) {
                for (SimWaiter w : simWaiters) {
                    if (!w.done && seenProducts.contains(w.p1) && seenProducts.contains(w.p2)) {
                        w.result = true;
                        w.done = true;
                        w.cond.signalAll(); // Usar signalAll por segurança em SD
                    }
                }
            }

            for (ConsWaiter w : consWaiters) {
                if (w.done) continue;

                int effectiveStreak;
                if (lastProduct != null && lastProduct.equals(w.baselineLastProduct)) {
                    effectiveStreak = streak - w.baselineStreak;
                } else {
                    effectiveStreak = streak;
                }

                if (effectiveStreak >= w.n) {
                    w.productResult = lastProduct;
                    w.done = true;
                    w.cond.signalAll();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean waitSimultaneous(String product1, String product2) throws InterruptedException {
        lock.lock();
        try {
            if (seenProducts.contains(product1) && seenProducts.contains(product2)) return true;

            Condition cond = lock.newCondition();
            SimWaiter waiter = new SimWaiter(product1, product2, cond);
            simWaiters.add(waiter);

            int myGen = dayGeneration;
            try {
                // Loop de espera para evitar despertares espúrios
                while (!waiter.done && myGen == dayGeneration) {
                    cond.await();
                }
            } finally {
                simWaiters.remove(waiter);
            }
            return waiter.done && waiter.result;
        } finally {
            lock.unlock();
        }
    }

    public String waitConsecutive(int n) throws InterruptedException {
        if (n <= 0) throw new IllegalArgumentException("n must be >= 1");
        lock.lock();
        try {
            Condition cond = lock.newCondition();
            ConsWaiter waiter = new ConsWaiter(n, cond, lastProduct, streak);
            consWaiters.add(waiter);

            int myGen = dayGeneration;
            try {
                while (!waiter.done && myGen == dayGeneration) {
                    cond.await();
                }
            } finally {
                consWaiters.remove(waiter);
            }
            return waiter.done ? waiter.productResult : null;
        } finally {
            lock.unlock();
        }
    }

    public void signalDayAdvanced() {
        lock.lock();
        try {
            dayGeneration++;
            for (SimWaiter w : simWaiters) {
                w.cond.signalAll();
            }
            for (ConsWaiter w : consWaiters) {
                w.cond.signalAll();
            }
            seenProducts.clear();
            lastProduct = null;
            streak = 0;
        } finally {
            lock.unlock();
        }
    }
}