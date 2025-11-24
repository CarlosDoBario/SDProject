package server;

import server.model.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * NotificationManager
 */
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

        // Baseline state recorded at the moment of registration to implement "from now on" semantics
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

    @SuppressWarnings("unused")
    public NotificationManager() {
        this(null);
    }

    // Chamado pelo DayManager quando um evento é adicionado.
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

            // acorda simultaneous waiters se satisfeitos (mantemos re-check pattern para evitar missed-signal)
            if (wasNew) {
                for (SimWaiter w : simWaiters) {
                    if (!w.done && seenProducts.contains(w.p1) && seenProducts.contains(w.p2)) {
                        w.result = true;
                        w.done = true;
                        w.cond.signal();
                    }
                }
            }

            // acorda consecutive waiters com semântica "a partir do registo"
            for (ConsWaiter w : consWaiters) {
                if (w.done) continue;

                int effectiveStreak;
                if (lastProduct != null && lastProduct.equals(w.baselineLastProduct)) {
                    // se o produto atual é o mesmo que no registo, só contamos o incremento ocorrido depois do registo
                    effectiveStreak = streak - w.baselineStreak;
                } else {
                    // se o produto mudou desde o registo, a streak actual corresponde a eventos ocorridos após o registo
                    effectiveStreak = streak;
                }

                if (effectiveStreak >= w.n) {
                    w.productResult = lastProduct;
                    w.done = true;
                    w.cond.signal();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    // Espera até ambos os produtos serem vendidos no dia corrente, ou até o dia terminar.
    // Retorna true se aconteceu antes do fim do dia, false caso contrário.
    public boolean waitSimultaneous(String product1, String product2) throws InterruptedException {
        lock.lock();
        try {
            // não satisfazir imediatamente aqui - manter a recheck pattern: se já estiverem ambos, devolvemos true
            if (seenProducts.contains(product1) && seenProducts.contains(product2)) return true;
            Condition cond = lock.newCondition();
            SimWaiter waiter = new SimWaiter(product1, product2, cond);
            simWaiters.add(waiter);
            // re-check imediata para evitar missed-signal
            if (seenProducts.contains(product1) && seenProducts.contains(product2)) {
                waiter.done = true;
                waiter.result = true;
                simWaiters.remove(waiter);
                return true;
            }
            int myGen = dayGeneration;
            try {
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

    // Espera até ocorrer n vendas consecutivas do mesmo produto no dia corrente,
    // contando apenas vendas que ocorram depois do registo do waiter.
    public String waitConsecutive(int n) throws InterruptedException {
        if (n <= 0) throw new IllegalArgumentException("n must be >= 1");
        lock.lock();
        try {
            Condition cond = lock.newCondition();
            // Regista baseline para "a partir do agora" semantics
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

    // Chamado quando o dia avança: incrementa geração, acorda todos os waiters e reinicia estado diário.
    public void signalDayAdvanced() {
        lock.lock();
        try {
            dayGeneration++;
            for (SimWaiter w : simWaiters) {
                w.cond.signal();
            }
            for (ConsWaiter w : consWaiters) {
                w.cond.signal();
            }
            seenProducts.clear();
            lastProduct = null;
            streak = 0;
        } finally {
            lock.unlock();
        }
    }
}