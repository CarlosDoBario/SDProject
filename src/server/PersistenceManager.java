package server;

import server.model.Event;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 PersistenceManager: grava e lê séries de eventos por dia no disco.
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

    // Persiste a lista de eventos para o dia especificado.
    // Escreve primeiro para um ficheiro temporário e depois faz rename para reduzir risco de ficheiro corrupto.
    public void persistDay(int dayIndex, List<Event> events) throws IOException {
        File target = dayFile(dayIndex);
        File tmp = new File(baseDir, "day-" + dayIndex + ".bin.tmp");

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
            out.writeInt(events.size());
            for (Event e : events) {
                e.writeTo(out);
            }
            out.flush();
        }

        // tenta renomear (se falhar, faz cópia e remove tmp)
        if (!tmp.renameTo(target)) {
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

    // Lê a série completa do dia para memória. Usar apenas se a série couber em memória.
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

    // Interface para processamento de eventos durante leitura em streaming.
    public interface EventHandler {
        void handle(Event e) throws IOException;
    }

    // Lê o ficheiro do dia e processa cada evento invocando o handler.
    // Esta operação processa evento-a-evento e não carrega a série inteira em memória.
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

    // Verifica se existe ficheiro persistido para o dia.
    public boolean dayExists(int dayIndex) {
        return dayFile(dayIndex).exists();
    }

    // Elimina ficheiro do dia (útil para limpeza em cenários de teste).
    public boolean deleteDay(int dayIndex) {
        File f = dayFile(dayIndex);
        return f.exists() && f.delete();
    }

    // Lista os índices dos dias que têm ficheiros persistidos (ordem não garantida).
    public List<Integer> listPersistedDays() {
        List<Integer> res = new ArrayList<>();
        File[] files = baseDir.listFiles();
        if (files == null) return res;
        for (File f : files) {
            String name = f.getName();
            if (name.startsWith("day-") && name.endsWith(".bin")) {
                try {
                    String num = name.substring(4, name.length() - 4); // remove "day-" e ".bin"
                    int idx = Integer.parseInt(num);
                    res.add(idx);
                } catch (NumberFormatException ignored) {}
            }
        }
        return res;
    }
}