package ae2nei.bridge;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

/** Check decompressed bytes and NBT lengths before Minecraft allocates tag contents. */
public final class BoundedNbt {
    public static final int STACK_BYTES = 64 * 1024;
    public static final int RECIPE_BYTES = 512 * 1024;
    public static final int SNAPSHOT_BYTES = 8 * 1024 * 1024;
    private final DataInputStream in;
    private int tags;
    private BoundedNbt(byte[] raw) { in = new DataInputStream(new ByteArrayInputStream(raw)); }
    public static byte[] unpack(byte[] compressed, int limit) throws IOException {
        if (compressed.length == 0 || compressed.length > BridgeNetwork.MAX_BYTES || limit <= 0)
            throw new IOException("Invalid compressed NBT size");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = gzip.read(buffer)) != -1) {
                if (n > limit - out.size()) throw new IOException("Decompressed NBT exceeds limit");
                out.write(buffer, 0, n);
            }
        }
        byte[] raw = out.toByteArray();
        BoundedNbt reader = new BoundedNbt(raw);
        if (reader.in.readUnsignedByte() != 10) throw new IOException("NBT root is not a compound");
        reader.in.readUTF();
        reader.payload(10, 0);
        if (reader.in.available() != 0) throw new IOException("Trailing NBT bytes");
        return raw;
    }
    private void payload(int type, int depth) throws IOException {
        if (depth > 32 || ++tags > 262144) throw new IOException("NBT structure exceeds limit");
        switch (type) {
            case 1: skip(1); break;
            case 2: skip(2); break;
            case 3: case 5: skip(4); break;
            case 4: case 6: skip(8); break;
            case 7: array(1); break;
            case 8: in.readUTF(); break;
            case 9:
                int child = in.readUnsignedByte(), count = in.readInt();
                if (count < 0 || count > 16384 || child > 11 || (count > 0 && child == 0))
                    throw new IOException("Invalid NBT list");
                for (int i = 0; i < count; i++) payload(child, depth + 1);
                break;
            case 10:
                int next;
                while ((next = in.readUnsignedByte()) != 0) {
                    in.readUTF();
                    payload(next, depth + 1);
                }
                break;
            case 11: array(4); break;
            default: throw new IOException("Invalid NBT tag type");
        }
    }
    private void array(int width) throws IOException {
        int count = in.readInt();
        if (count < 0 || (long) count * width > in.available()) throw new IOException("Invalid NBT array length");
        skip(count * width);
    }
    private void skip(int count) throws IOException {
        if (count > in.available() || in.skipBytes(count) != count) throw new IOException("Truncated NBT");
    }
}
