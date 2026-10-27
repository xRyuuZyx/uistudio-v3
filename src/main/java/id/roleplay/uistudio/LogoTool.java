package id.roleplay.uistudio;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/** Mengganti logo server dengan mudah: lewat link gambar, ukuran, atau reset ke bawaan. */
public class LogoTool {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int MAX_BYTES = 8_000_000;
    private final UIStudio plugin;

    public LogoTool(UIStudio plugin) { this.plugin = plugin; }

    private File file() { return new File(plugin.getDataFolder(), "images/logo.png"); }

    private void editYml(Consumer<YamlConfiguration> c) {
        File f = new File(plugin.getDataFolder(), "images.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        c.accept(y);
        try {
            y.save(f);
        } catch (IOException e) {
            plugin.getLogger().warning("Gagal menyimpan images.yml: " + e.getMessage());
        }
    }

    private int currentHeight() {
        File f = new File(plugin.getDataFolder(), "images.yml");
        return YamlConfiguration.loadConfiguration(f).getInt("images.logo.height", 28);
    }

    /** Unduh gambar dari link, kecilkan otomatis, jadikan logo. */
    public void setFromUrl(CommandSender s, String link) {
        String url = link.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            plugin.send(s, "<red>Link harus diawali http:// atau https://");
            return;
        }
        plugin.send(s, "<gray>Mengunduh logo...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(15000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "UIStudio");
                if (c.getResponseCode() != 200) throw new IOException("server menjawab HTTP " + c.getResponseCode());
                byte[] data;
                try (InputStream in = c.getInputStream()) {
                    data = in.readNBytes(MAX_BYTES + 1);
                }
                if (data.length > MAX_BYTES) throw new IOException("file terlalu besar (maks 8 MB)");
                BufferedImage src = ImageIO.read(new ByteArrayInputStream(data));
                if (src == null) throw new IOException("bukan gambar PNG/JPG yang valid (pakai link LANGSUNG ke file gambar)");
                Bukkit.getScheduler().runTask(plugin, () -> apply(s, src));
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.send(s, "<red>Gagal mengambil logo: " + MM.escapeTags(String.valueOf(e.getMessage()))));
            }
        });
    }

    private void apply(CommandSender s, BufferedImage src) {
        int h = Math.max(8, Math.min(64, currentHeight()));
        int th = h * 2; // tekstur 2x supaya tajam
        int tw = Math.max(2, (int) Math.round(src.getWidth() * (double) th / src.getHeight()));
        int maxW = 256;
        if (tw > maxW) {
            tw = maxW;
            th = Math.max(2, (int) Math.round(src.getHeight() * (double) tw / src.getWidth()));
        }
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src.getScaledInstance(tw, th, Image.SCALE_AREA_AVERAGING), 0, 0, null);
        g.dispose();
        try {
            ImageIO.write(out, "png", file());
        } catch (IOException e) {
            plugin.send(s, "<red>Gagal menyimpan logo: " + MM.escapeTags(String.valueOf(e.getMessage())));
            return;
        }
        int display = Math.max(4, th / 2);
        editYml(y -> {
            y.set("images.logo.height", display);
            y.set("images.logo.ascent", Math.min(display, display / 2 + 3));
        });
        finish(s, "ukuran " + (tw / 2) + "x" + display + " px");
    }

    public void resize(CommandSender s, int height) {
        if (height < 8 || height > 64) {
            plugin.send(s, "<red>Tinggi harus antara 8 dan 64.");
            return;
        }
        if (!file().exists()) {
            plugin.send(s, "<red>Belum ada logo.png.");
            return;
        }
        editYml(y -> {
            y.set("images.logo.height", height);
            y.set("images.logo.ascent", Math.min(height, height / 2 + 3));
        });
        finish(s, "tinggi " + height + " px");
    }

    public void reset(CommandSender s) {
        try {
            byte[] data = plugin.defaultFile("images/logo.png");
            if (data == null) throw new IOException("logo bawaan tidak ditemukan");
            Files.write(file().toPath(), data);
        } catch (IOException e) {
            plugin.send(s, "<red>Gagal: " + MM.escapeTags(String.valueOf(e.getMessage())));
            return;
        }
        editYml(y -> {
            y.set("images.logo.height", null);
            y.set("images.logo.ascent", null);
        });
        finish(s, "logo bawaan");
    }

    private void finish(CommandSender s, String info) {
        PackBuilder.Result r = plugin.rebuildPack();
        if (r == null) {
            plugin.send(s, "<red>Logo tersimpan, tapi pack gagal dibangun. Cek console.");
            return;
        }
        plugin.send(s, "<green>Logo diperbarui (" + info + "). Pack dibangun ulang.");
        switch (plugin.delivery().mode()) {
            case "SELF_HOST" -> {
                plugin.delivery().sendToAll();
                plugin.send(s, "<gray>Pack dikirim ulang ke semua pemain.");
            }
            case "URL" -> plugin.send(s, "<yellow>Mode URL: download <white>plugins/UIStudio/pack/uistudio-pack.zip</white>, "
                    + "upload ke GitHub (timpa file lama), lalu ketik <white>/uistudio pack send</white>. "
                    + "<gray>Sebelum file di GitHub diganti, pemain yang join akan gagal memuat pack.");
            case "NEXO" -> plugin.send(s, "<yellow>Jalankan <white>/nexo reload all</white> supaya Nexo mengirim pack yang baru.");
            default -> { }
        }
    }
}
