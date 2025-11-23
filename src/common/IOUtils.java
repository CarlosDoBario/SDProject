import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilitários simples para serializar/deserializar tipos usados pelo protocolo.
 * Formato de string: [len:int][utf8-bytes], com len == -1 significando null.
 */
public final class IOUtils {
    private IOUtils() {}

    // Escreve uma string em UTF-8 com prefixo de comprimento em bytes (int). Se s == null, escreve -1
    public static void writeString(DataOutputStream out, String s) throws IOException {
        if (s == null) {
            out.writeInt(-1);
            return;
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    // Lê uma string escrita por writeString. Retorna null se o comprimento for -1.
    public static String readString(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len < 0) return null;
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // Escreve uma lista de strings. Lista null é escrita como count = -1.
    // Caso contrário escreve count:int seguido de cada string com writeString.
    public static void writeStringList(DataOutputStream out, List<String> list) throws IOException {
        if (list == null) {
            out.writeInt(-1);
            return;
        }
        out.writeInt(list.size());
        for (String s : list) {
            writeString(out, s);
        }
    }

    // Lê uma lista de strings escrita por writeStringList. Retorna null se count == -1.
    public static List<String> readStringList(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0) return null;
        List<String> list = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            list.add(readString(in));
        }
        return list;
    }

    // Escreve um array de inteiros com prefixo count:int. Null é representado por -1.
    public static void writeIntArray(DataOutputStream out, int[] arr) throws IOException {
        if (arr == null) {
            out.writeInt(-1);
            return;
        }
        out.writeInt(arr.length);
        for (int v : arr) out.writeInt(v);
    }

    // Lê um array de ints escrito por writeIntArray. Retorna null se count == -1.
    public static int[] readIntArray(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0) return null;
        int[] arr = new int[count];
        for (int i = 0; i < count; i++) arr[i] = in.readInt();
        return arr;
    }
}