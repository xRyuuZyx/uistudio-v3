package id.roleplay.uistudio;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Status mikrofon pemain dari Simple Voice Chat (diisi oleh VoiceHook). Tidak memakai API voice chat. */
public final class Voice {
    public static volatile boolean hooked = false;
    private static final Map<UUID, Long> lastSpoke = new ConcurrentHashMap<>();
    private static final Set<UUID> connected = ConcurrentHashMap.newKeySet();

    private Voice() {}

    static void spoke(UUID id) {
        connected.add(id);
        lastSpoke.put(id, System.currentTimeMillis());
    }

    static void setConnected(UUID id, boolean c) {
        if (c) {
            connected.add(id);
        } else {
            connected.remove(id);
            lastSpoke.remove(id);
        }
    }

    public static boolean speaking(UUID id) {
        Long t = lastSpoke.get(id);
        return t != null && System.currentTimeMillis() - t < 400;
    }

    private static boolean ok(UUID id) { return hooked && connected.contains(id); }

    /** merah = tidak terhubung, abu = diam, hijau = sedang bicara */
    public static String color(UUID id) {
        if (!ok(id)) return "#ff4d4d";
        return speaking(id) ? "#3ddc84" : "#e6e6e6";
    }

    public static String icon(UUID id) { return ok(id) ? "ico_mic" : "ico_mic_off"; }
}
