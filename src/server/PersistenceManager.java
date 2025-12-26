package server;

import server.model.Event;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * PersistenceManager: grava e lê séries de eventos por dia no disco.
 * Mantido simples, pois a coordenação de acesso a ficheiros é gerida pelos Managers superiores.
 */
public class PersistenceManager {
    private final File baseDir;

    public PersistenceManager() {
        this("data");
    }

    public PersistenceManager(String baseDirPath) {
        this.baseDir = new File(baseDirPath);
        if (!this.baseDir.exists()) {
            this.baseDir.mkdirs();
        }
    }

    private File dayFile(int dayIndex) {
        return new File(baseDir, "day-" + dayIndex + ".bin");
    }

    /**
     * Persiste a lista de eventos para o dia especificado.
     * Implementação robusta com ficheiro temporário para evitar corrupção em caso de falha.
     */
    public void persistDay(int dayIndex, List<Event> events) throws IOException {
        File target = dayFile(dayIndex);
        File tmp = new File(baseDir, "day-" + dayIndex + ".bin.tmp");

        // Escrita utilizando DataOutputStream (API permitida)
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
            out.writeInt(events.size());
            for (Event e : events) {
                e.writeTo(out);
            }
            out.flush();
        }

        // Tenta renomear o ficheiro (operação atómica no SO)
        if (!tmp.renameTo(target)) {
            // Caso o rename falhe (comum em alguns sistemas se o ficheiro já existir), faz cópia manual
            try (InputStream in = new BufferedInputStream(new FileInputStream(tmp));
                 OutputStream os = new BufferedOutputStream(new FileOutputStream(target))) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) != -1) {
                    os.write(buf, 0, r);
                }
                os.flush();
            } finally {
                tmp.delete();
            }
        }
    }

    public List<Event> readDay(int dayIndex) throws IOException {
        File f = dayFile(dayIndex);
        if (!f.exists()) throw new FileNotFoundException("Day file not found: " + f.getAbsolutePath());

        List<Event> result = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)))) {
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                Event e = Event.readFrom(in);
                result.add(e);
            }
        }
        return result;
    }

    public interface EventHandler {
        void handle(Event e) throws IOException;
    }

    /**
     * Processamento em streaming para evitar carregar dias inteiros em memória.
     */
    public void streamDay(int dayIndex, EventHandler handler) throws IOException {
        File f = dayFile(dayIndex);
        if (!f.exists()) throw new FileNotFoundException("Day file not found: " + f.getAbsolutePath());

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)))) {
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                Event e = Event.readFrom(in);
                handler.handle(e);
            }
        }
    }

    public boolean dayExists(int dayIndex) {
        return dayFile(dayIndex).exists();
    }

    public boolean deleteDay(int dayIndex) {
        File f = dayFile(dayIndex);
        return f.exists() && f.delete();
    }

    public List<Integer> listPersistedDays() {
        List<Integer> res = new ArrayList<>();
        File[] files = baseDir.listFiles();
        if (files == null) return res;
        for (File f : files) {
            String name = f.getName();
            if (name.startsWith("day-") && name.endsWith(".bin")) {
                try {
                    String num = name.substring(4, name.length() - 4);
                    int idx = Integer.parseInt(num);
                    res.add(idx);
                } catch (NumberFormatException ignored) {}
            }
        }
        return res;
    }
}