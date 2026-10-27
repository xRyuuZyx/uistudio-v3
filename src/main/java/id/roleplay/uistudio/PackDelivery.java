package id.roleplay.uistudio;

import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;

/** Mengirim pack ke pemain + mencatat status unduhan (untuk diagnosa). */
public class PackDelivery implements Listener {
    public static final UUID PACK_ID = UUID.nameUUIDFromBytes("uistudio-pack".getBytes(StandardCharsets.UTF_8));

    private final UIStudio plugin;
    private HttpServer server;
    private PackBuilder.Result result;
    private String mode = "NONE";
    private String url;
    private byte[] hash;
    private String note = "";
    private final Map<UUID, Deque<String>> statuses = new HashMap<>();

    public PackDelivery(UIStudio plugin) { this.plugin = plugin; }

    public String mode() { return mode; }
    public String url() { return url; }
    public String note() { return note; }
    public PackBuilder.Result result() { return result; }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public void setup(PackBuilder.Result r) {
        stop();
        this.result = r;
        this.hash = hex(r.sha1);
        this.url = null;
        this.note = "";
        this.mode = plugin.getConfig().getString("pack.delivery", "SELF_HOST").toUpperCase();

        switch (mode) {
            case "SELF_HOST" -> startHost();
            case "URL" -> {
                url = plugin.getConfig().getString("pack.url", "");
                if (url.isBlank()) {
                    url = null;
                    note = "pack.url masih kosong.";
                    plugin.getLogger().warning(note);
                }
                String s = plugin.getConfig().getString("pack.sha1", "");
                if (!s.isBlank()) hash = hex(s.trim());
            }
            case "NEXO" -> {
                File dir = new File(plugin.getDataFolder().getParentFile(), "Nexo/pack/external_packs");
                try {
                    dir.mkdirs();
                    Files.copy(r.zip.toPath(), new File(dir, "uistudio-pack.zip").toPath(), StandardCopyOption.REPLACE_EXISTING);
                    note = "Disalin ke " + dir.getPath() + " - jalankan /nexo reload all.";
                } catch (Exception ex) {
                    note = "Gagal menyalin ke Nexo: " + ex.getMessage();
                    plugin.getLogger().warning(note);
                }
            }
            default -> note = "Pengiriman pack dimatikan (delivery: NONE).";
        }
    }

    private void startHost() {
        int port = plugin.getConfig().getInt("pack.self-host.port", 8083);
        String host = plugin.getConfig().getString("pack.self-host.public-host", "127.0.0.1");
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", ex -> {
                byte[] data = Files.readAllBytes(result.zip.toPath());
                ex.getResponseHeaders().add("Content-Type", "application/zip");
                ex.sendResponseHeaders(200, data.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(data); }
            });
            server.setExecutor(Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "UIStudio-Pack");
                t.setDaemon(true);
                return t;
            }));
            server.start();
            url = "http://" + host + ":" + port + "/uistudio.zip?v=" + result.sha1.substring(0, 8);
            note = "Server pack berjalan di port " + port + ".";
            if (host.equals("127.0.0.1") || host.equals("localhost")) {
                note += " PERINGATAN: public-host masih 127.0.0.1, pemain tidak bisa mengunduh. Isi dengan IP/domain server.";
                plugin.getLogger().warning(note);
            }
        } catch (Exception ex) {
            note = "Gagal membuka port " + port + ": " + ex.getMessage()
                    + " (port belum dibuka / dipakai plugin lain). Pakai delivery: URL.";
            plugin.getLogger().severe(note);
        }
    }

    public void sendTo(Player p) {
        if (url == null || result == null) return;
        String prompt = LegacyComponentSerializer.legacySection().serialize(
                MiniMessage.miniMessage().deserialize(plugin.getConfig().getString("pack.prompt", "")));
        p.addResourcePack(PACK_ID, url, hash, prompt, plugin.getConfig().getBoolean("pack.required", false));
    }

    public void sendToAll() {
        for (Player p : Bukkit.getOnlinePlayers()) sendTo(p);
    }

    public List<String> statusOf(UUID id) {
        return new ArrayList<>(statuses.getOrDefault(id, new ArrayDeque<>()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) sendTo(p); }, 30L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { statuses.remove(e.getPlayer().getUniqueId()); }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        Deque<String> q = statuses.computeIfAbsent(e.getPlayer().getUniqueId(), k -> new ArrayDeque<>());
        String mine = e.getID().equals(PACK_ID) ? " (UIStudio)" : " (pack lain " + e.getID().toString().substring(0, 8) + ")";
        q.addFirst(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " " + e.getStatus() + mine);
        while (q.size() > 8) q.removeLast();
        if (e.getID().equals(PACK_ID)) {
            switch (e.getStatus()) {
                case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED ->
                        plugin.getLogger().warning(e.getPlayer().getName() + " gagal memuat pack UIStudio: " + e.getStatus()
                                + " | url=" + url);
                default -> { }
            }
        }
    }
}
