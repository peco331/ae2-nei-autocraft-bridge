package ae2nei.bridge;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Normal requests are silent; repeated error categories are rate-limited. */
public final class BridgeLog {
    public static final boolean DEBUG = Boolean.getBoolean("ae2nei.debug");
    private static final Map<String, Long> LAST = new HashMap<String, Long>();
    private BridgeLog() {}
    public static synchronized void warning(String category, String text, Exception error) {
        long now = System.nanoTime();
        Long last = LAST.get(category);
        if (!DEBUG && last != null && now - last < TimeUnit.SECONDS.toNanos(5)) return;
        LAST.put(category, now);
        System.err.println("[AE2-NEI-Bridge] " + text);
        if (error != null) error.printStackTrace();
    }
}
