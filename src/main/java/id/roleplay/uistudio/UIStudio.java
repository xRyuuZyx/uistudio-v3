package id.roleplay.uistudio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class UIStudio extends JavaPlugin implements TabExecutor {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final List<String> ICONS = List.of("coin_0", "coin_1", "coin_2", "coin_3");

    private static List<String> defaultImages() {
        List<String> l = new ArrayList<>(List.of("card", "seg", "ic_heart", "ic_armor", "ic_food", "ic_money", "ic_job",
                "ic_gang", "gui_profile", "gui_shop", "coin_0", "coin_1", "coin_2", "coin_3", "logo",
                "ico_food", "ico_water", "ico_run", "ico_mic", "ico_mic_off",
                "num_comma", "num_dot", "num_dollar", "num_minus", "ring_bg", "hpbar_bg", "arbar_bg"));
        for (int d = 0; d < 10; d++) l.add("num_" + d);
        for (int k = 1; k <= 20; k++) {
            l.add("ring_" + k);
            l.add("hpbar_" + k);
            l.add("arbar_" + k);
        }
        return l;
    }

    private Placeholders placeholders;
    private Images images;
    private PackBuilder packBuilder;
    private PackDelivery delivery;
    private ScreenManager screens;
    private HudManager huds;
    private Actions actions;
    private Editor editor;
    private LogoTool logoTool;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        copyDefaults("images", defaultImages());
        copyDefaults("icons", ICONS);
        new File(getDataFolder(), "models/textures").mkdirs();

        placeholders = new Placeholders(this);
        images = new Images(this);
        packBuilder = new PackBuilder(this);
        delivery = new PackDelivery(this);
        screens = new ScreenManager(this);
        huds = new HudManager(this);
        actions = new Actions(this);
        editor = new Editor(this);
        logoTool = new LogoTool(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(delivery, this);
        pm.registerEvents(new ScreenListener(this), this);
        pm.registerEvents(new Menu(), this);
        pm.registerEvents(editor, this);
        pm.registerEvents(huds, this);

        getCommand("uistudio").setExecutor(this);
        getCommand("uistudio").setTabCompleter(this);

        screens.load();
        huds.load();
        huds.start();
        if (getServer().getPluginManager().getPlugin("voicechat") != null) {
            try {
                VoiceBridge.register(this);
            } catch (Throwable t) {
                getLogger().warning("Gagal menghubungkan Simple Voice Chat: " + t);
            }
        }
        rebuildPack();
    }

    @Override
    public void onDisable() {
        if (huds != null) huds.shutdown();
        if (delivery != null) delivery.stop();
    }

    /** Isi satu file dari defaults.zip (gambar & ikon bawaan), atau null kalau tidak ada. */
    public byte[] defaultFile(String path) {
        try (InputStream raw = getResource("defaults.zip")) {
            if (raw == null) return null;
            try (ZipInputStream zin = new ZipInputStream(raw)) {
                ZipEntry e;
                while ((e = zin.getNextEntry()) != null) {
                    if (e.getName().equals(path)) return zin.readAllBytes();
                }
            }
        } catch (IOException ignored) { }
        return null;
    }

    /** Salin gambar bawaan yang belum ada dari defaults.zip (gambar milikmu tidak ditimpa). */
    private void copyDefaults(String folder, List<String> names) {
        File dir = new File(getDataFolder(), folder);
        dir.mkdirs();
        try (InputStream raw = getResource("defaults.zip")) {
            if (raw == null) return;
            try (ZipInputStream zin = new ZipInputStream(raw)) {
                ZipEntry e;
                while ((e = zin.getNextEntry()) != null) {
                    String n = e.getName();
                    if (e.isDirectory() || !n.startsWith(folder + "/") || !n.endsWith(".png")) continue;
                    File out = new File(dir, n.substring(folder.length() + 1));
                    if (!out.exists()) Files.write(out.toPath(), zin.readAllBytes());
                }
            }
        } catch (IOException ignored) { }
    }

    public PackBuilder.Result rebuildPack() {
        images.load();
        try {
            PackBuilder.Result r = packBuilder.build();
            delivery.setup(r);
            return r;
        } catch (IOException e) {
            getLogger().severe("Gagal membangun pack: " + e.getMessage());
            return null;
        }
    }

    public Placeholders placeholders() { return placeholders; }
    public Images images() { return images; }
    public PackDelivery delivery() { return delivery; }
    public ScreenManager screens() { return screens; }
    public HudManager huds() { return huds; }
    public Actions actions() { return actions; }
    public Editor editor() { return editor; }
    public LogoTool logoTool() { return logoTool; }

    public void send(CommandSender to, String mini) {
        to.sendMessage(MM.deserialize(getConfig().getString("prefix", "") + mini));
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("uistudio.admin")) return true;
        send(s, "<red>Tidak punya izin.");
        return false;
    }

    // ---------------- Perintah ----------------

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String sub = a.length > 0 ? a[0].toLowerCase() : "";
        switch (sub) {
            case "open" -> {
                if (!(s instanceof Player p)) { send(s, "<red>Hanya untuk pemain."); return true; }
                if (a.length < 2) { send(s, "<gray>Pakai: <white>/uistudio open <layar> [argumen...]"); return true; }
                screens.open(p, a[1], Arrays.copyOfRange(a, 2, a.length));
            }
            case "list" -> send(s, "<yellow>Layar:</yellow> <white>" + String.join(", ", screens.ids())
                    + "\n<yellow>HUD:</yellow> <white>" + String.join(", ", huds.ids())
                    + "\n<yellow>Gambar:</yellow> <white>" + images.all().size());
            case "reload" -> {
                if (!admin(s)) return true;
                reloadConfig();
                screens.load();
                huds.load();
                huds.start();
                PackBuilder.Result r = rebuildPack();
                delivery.sendToAll();
                send(s, r == null ? "<red>Gagal membangun pack, cek console." : "<green>Dimuat ulang. Pack: " + r.images + " gambar.");
            }
            case "editor" -> {
                if (!admin(s)) return true;
                if (s instanceof Player p) editor.openMain(p); else send(s, "<red>Hanya untuk pemain.");
            }
            case "hud" -> hud(s, a);
            case "logo" -> logo(s, a);
            case "pack" -> pack(s, a);
            case "preview" -> preview(s, a);
            default -> send(s, "<gray>/uistudio <open|list|reload|editor|hud|logo|pack|preview>");
        }
        return true;
    }

    private void hud(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { send(s, "<red>Hanya untuk pemain."); return; }
        String act = a.length > 1 ? a[1].toLowerCase() : "";
        String id = a.length > 2 ? a[2] : "main";
        switch (act) {
            case "show" -> send(p, huds.show(p, id) ? "<green>HUD <white>" + id + "</white> ditampilkan." : "<red>HUD tidak ditemukan.");
            case "hide" -> { huds.hide(p, id); send(p, "<yellow>HUD disembunyikan."); }
            case "set" -> send(p, huds.only(p, id) ? "<green>Sekarang hanya HUD <white>" + id + "</white> yang tampil." : "<red>HUD tidak ditemukan.");
            case "toggle" -> { huds.toggle(p, id); send(p, "<gray>HUD di-toggle."); }
            case "edit" -> { if (admin(s)) editor.hudLayers(p, id); }
            case "reset" -> {
                if (!admin(s)) return;
                if (!List.of("main", "demo", "fivem").contains(id.toLowerCase())) {
                    send(p, "<red>Hanya HUD bawaan (main, demo, fivem) yang bisa di-reset.");
                    return;
                }
                saveResource("huds/" + id.toLowerCase() + ".yml", true);
                huds.load();
                send(p, "<green>HUD <white>" + id + "</white> dikembalikan ke bawaan.");
            }
            case "list" -> send(p, "<yellow>HUD:</yellow> <white>" + String.join(", ", huds.ids())
                    + " <gray>| aktif: <white>" + String.join(", ", huds.activeOf(p)));
            default -> send(p, "<gray>/uistudio hud <show|hide|set|toggle|edit|reset|list> [id]");
        }
    }

    private void logo(CommandSender s, String[] a) {
        if (!admin(s)) return;
        String act = a.length > 1 ? a[1].toLowerCase() : "";
        switch (act) {
            case "set" -> {
                if (a.length < 3) { send(s, "<gray>Pakai: <white>/uistudio logo set <link-langsung-gambar>"); return; }
                logoTool.setFromUrl(s, a[2]);
            }
            case "size" -> {
                if (a.length < 3) { send(s, "<gray>Pakai: <white>/uistudio logo size <8-64>"); return; }
                try {
                    logoTool.resize(s, Integer.parseInt(a[2]));
                } catch (NumberFormatException e) {
                    send(s, "<red>Tinggi harus angka.");
                }
            }
            case "reset" -> logoTool.reset(s);
            default -> {
                Images.Img img = images.get("logo");
                send(s, "<yellow>Logo server</yellow>"
                        + "\n<white>1)</white> Lewat link: <white>/uistudio logo set <link-langsung-gambar>"
                        + "\n<white>2)</white> Lewat file: taruh <white>plugins/UIStudio/images/logo.png</white>, lalu <white>/uistudio pack rebuild"
                        + "\n<white>Ukuran tampil:</white> /uistudio logo size <8-64> <gray>(sekarang "
                        + (img == null ? "-" : img.height + " px") + ")"
                        + "\n<white>Kembali ke bawaan:</white> /uistudio logo reset"
                        + "\n<gray>Setelah logo diganti, pack harus dikirim ulang ke pemain (lihat petunjuk yang muncul).");
            }
        }
    }

    private void pack(CommandSender s, String[] a) {
        String act = a.length > 1 ? a[1].toLowerCase() : "info";
        switch (act) {
            case "rebuild" -> {
                if (!admin(s)) return;
                PackBuilder.Result r = rebuildPack();
                send(s, r == null ? "<red>Gagal, cek console." : "<green>Pack dibangun ulang. SHA-1: <white>" + r.sha1);
            }
            case "send" -> {
                if (!admin(s)) return;
                if (a.length > 2) {
                    Player t = Bukkit.getPlayerExact(a[2]);
                    if (t == null) { send(s, "<red>Pemain tidak online."); return; }
                    delivery.sendTo(t);
                } else delivery.sendToAll();
                send(s, "<green>Pack dikirim." + (delivery.url() == null ? " <red>(URL pack belum ada, cek /uistudio pack info)" : ""));
            }
            case "status" -> {
                Player t = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : (s instanceof Player p ? p : null);
                if (t == null) { send(s, "<red>Pemain tidak ditemukan."); return; }
                List<String> st = delivery.statusOf(t.getUniqueId());
                if (st.isEmpty()) {
                    send(s, "<yellow>" + t.getName() + ": belum ada status pack sama sekali. <gray>Artinya server belum mengirim pack, "
                            + "atau client tidak pernah merespons (cek /uistudio pack info).");
                } else {
                    send(s, "<yellow>Status pack " + t.getName() + ":");
                    for (String line : st) s.sendMessage(MM.deserialize("<gray> - <white>" + line));
                }
            }
            default -> {
                PackBuilder.Result r = delivery.result();
                if (r == null) { send(s, "<red>Pack belum dibangun."); return; }
                send(s, "<yellow>Mode:</yellow> <white>" + delivery.mode()
                        + "\n<yellow>URL:</yellow> <white>" + (delivery.url() == null ? "(belum ada)" : delivery.url())
                        + "\n<yellow>SHA-1:</yellow> <white>" + r.sha1
                        + "\n<yellow>File:</yellow> <white>" + r.zip.getPath() + " (" + (r.zip.length() / 1024) + " KB)"
                        + "\n<yellow>Isi:</yellow> <white>" + r.images + " gambar, " + r.icons + " ikon, " + r.models + " model"
                        + (delivery.note().isEmpty() ? "" : "\n<yellow>Catatan:</yellow> <white>" + delivery.note()));
            }
        }
    }

    private void preview(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { send(s, "<red>Hanya untuk pemain."); return; }
        if (a.length < 2) { send(s, "<gray>Pakai: <white>/uistudio preview <id-gambar>"); return; }
        Images.Img img = images.get(a[1]);
        if (img == null) { send(s, "<red>Gambar tidak ada. <gray>Tersedia: <white>" + images.all().stream().map(i -> i.id).toList()); return; }
        Component c = new Row(images, 0).image(img.id, null, 0).build();
        for (int i = 0; i < 5; i++) Bukkit.getScheduler().runTaskLater(this, () -> p.sendActionBar(c), i * 20L);
        send(s, "<gray>Preview tampil 5 detik di atas hotbar. Kalau yang tampil <white>kotak-kotak</white>, resource pack belum termuat: "
                + "cek <white>/uistudio pack status</white>.");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(List.of("open", "list", "reload", "editor", "hud", "logo", "pack", "preview"));
        else if (a.length == 2) {
            switch (a[0].toLowerCase()) {
                case "open" -> out.addAll(screens.ids());
                case "hud" -> out.addAll(List.of("show", "hide", "set", "toggle", "edit", "reset", "list"));
                case "logo" -> out.addAll(List.of("set", "size", "reset"));
                case "pack" -> out.addAll(List.of("info", "rebuild", "send", "status"));
                case "preview" -> images.all().forEach(i -> out.add(i.id));
                default -> { }
            }
        } else if (a.length == 3) {
            switch (a[0].toLowerCase()) {
                case "hud" -> out.addAll(huds.ids());
                case "pack" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                default -> { }
            }
        }
        String last = a.length > 0 ? a[a.length - 1].toLowerCase() : "";
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }
}
