import com.yagay.ListCleaner.ui.DiagnosticBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

/** Host-side test: no Android SDK or downloaded dependencies required. */
public final class DiagnosticBufferCheck {
    public static void main(String[] args) {
        Random random = new Random(71);
        for (int capacity : new int[] {4, 11, 64, 4096}) {
            for (int size : new int[] {0, 1, capacity - 1, capacity, capacity + 1, capacity * 5}) {
                byte[] bytes = new byte[size];
                for (int i = 0; i < size; i++) bytes[i] = (byte) ('a' + i % 26);
                DiagnosticBuffer buffer = new DiagnosticBuffer(capacity);
                int offset = 0;
                while (offset < size) {
                    int count = Math.min(size - offset, 1 + random.nextInt(capacity * 2));
                    buffer.append(Arrays.copyOfRange(bytes, offset, offset + count), count);
                    offset += count;
                }
                if (buffer.totalBytes() != size || buffer.truncated() != (size > capacity))
                    throw new AssertionError("counter mismatch");
                byte[] result = buffer.snapshot();
                if (size <= capacity) {
                    if (!Arrays.equals(bytes, result)) throw new AssertionError("untruncated mismatch");
                } else {
                    int head = Math.min(capacity / 4, 256 * 1024);
                    String expected = new String(bytes, 0, head, StandardCharsets.UTF_8)
                        + "\n[... omitted " + (size - capacity) + " bytes ...]\n"
                        + new String(bytes, size - (capacity - head), capacity - head, StandardCharsets.UTF_8);
                    if (!expected.equals(new String(result, StandardCharsets.UTF_8)))
                        throw new AssertionError("head/tail mismatch capacity=" + capacity + " size=" + size);
                }
            }
        }
        System.out.println("PASS: 24 bounded capture cases, including wraparound and large chunks");
    }
}
