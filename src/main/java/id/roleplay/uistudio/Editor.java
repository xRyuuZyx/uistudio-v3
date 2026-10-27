package id.roleplay.uistudio;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/** Editor in-game: edit layar (pindah / buat / ubah tombol) dan edit layer HUD (geser, ubah, hapus). */
public class Editor implements Listener {
    private final UIStudio plugin;
    private final Map<UUID, String> selected = new HashMap<>();
    private final Map<UUID, Consumer<String>> prompts = new HashMap<>();

    public Editor(UIStudio plugin) { this.plugin = plugin; }

    // ================= Umum =================

    private void prompt(Player p, String msg, Consumer<String> c) {
        p.closeInventory();
        prompts.put(p.getUniqueId(), c);
        plugin.send(p, "<yellow>" + msg + " <gray>(ketik 'batal' untuk membatalkan)");
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Consumer<String> c = prompts.remove(e.getPlayer().getUniqueId());
        if (c == null) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (text.equalsIgnoreCase("batal")) { plugin.send(p, "<gray>Dibatalkan."); return; }
            c.accept(text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        prompts.remove(e.getPlayer().getUniqueId());
        selected.remove(e.getPlayer().getUniqueId());
    }

    public void openMain(Player p) {
        Menu m = new Menu(Items.c("<dark_gray>UIStudio Editor"), 4);
        m.fill(Material.BLACK_STAINED_GLASS_PANE);
        int slot = 0;
        for (String id : plugin.screens().ids()) {
            if (slot >= 18) break;
            m.set(slot++, Items.make(Material.PAPER, "<gold>Layar: <white>" + id, "<gray>Klik untuk mengedit"), pl -> editScreen(pl, id));
        }
        for (String id : plugin.huds().ids()) {
            if (slot >= 27) break;
            m.set(slot++, Items.make(Material.CLOCK, "<aqua>HUD: <white>" + id, "<gray>Klik untuk mengedit layer"), pl -> hudLayers(pl, id));
        }
        m.set(28, Items.make(Material.EMERALD, "<green>+ Layar baru"), pl -> prompt(pl, "Ketik id layar baru (huruf/angka/_).", t -> {
            String id = Images.sanitize(t);
            File f = new File(plugin.getDataFolder(), "screens/" + id + ".yml");
            if (f.exists()) { plugin.send(pl, "<red>Id sudah dipakai."); return; }
            YamlConfiguration y = new YamlConfiguration();
            y.set("rows", 3);
            y.set("title.layers", new ArrayList<>());
            y.createSection("buttons");
            try { y.save(f); } catch (IOException ex) { plugin.send(pl, "<red>Gagal menyimpan."); return; }
            plugin.screens().load();
            editScreen(pl, id);
        }));
        m.set(29, Items.make(Material.DIAMOND, "<green>+ HUD baru"), pl -> prompt(pl, "Ketik id HUD baru (huruf/angka/_).", t -> {
            String id = Images.sanitize(t);
            File f = new File(plugin.getDataFolder(), "huds/" + id + ".yml");
            if (f.exists()) { plugin.send(pl, "<red>Id sudah dipakai."); return; }
            YamlConfiguration y = new YamlConfiguration();
            y.set("span", 240);
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "bossbar");
            row.put("layers", new ArrayList<>());
            rows.add(row);
            y.set("rows", rows);
            try { y.save(f); } catch (IOException ex) { plugin.send(pl, "<red>Gagal menyimpan."); return; }
            plugin.huds().load();
            hudLayers(pl, id);
        }));
        m.set(30, Items.make(Material.ITEM_FRAME, "<gold>Ganti logo server", "<gray>Tempel link gambar (PNG/JPG)"),
                pl -> prompt(pl, "Tempel link langsung gambar logo (https://...).", t -> plugin.logoTool().setFromUrl(pl, t)));
        m.set(33, Items.make(Material.ANVIL, "<yellow>Bangun ulang pack", "<gray>Setelah menambah / mengubah gambar"), pl -> {
            pl.closeInventory();
            var r = plugin.rebuildPack();
            plugin.send(pl, r == null ? "<red>Gagal membangun pack." : "<green>Pack dibangun (" + r.images + " gambar). <gray>/uistudio pack send untuk mengirim ulang.");
        });
        m.set(34, Items.make(Material.ENDER_PEARL, "<yellow>Kirim ulang pack ke semua"), pl -> {
            plugin.delivery().sendToAll();
            plugin.send(pl, "<green>Pack dikirim ulang.");
        });
        m.open(p);
    }

    // ================= Editor layar =================

    public void editScreen(Player p, String id) {
        selected.remove(p.getUniqueId());
        plugin.screens().open(p, id, new String[0], true);
        plugin.send(p, "<gold>Mode edit layar <white>" + id + "</white>: <gray>klik tombol = pilih lalu klik slot tujuan untuk memindah; "
                + "klik kanan/shift = opsi tombol; pegang item di kursor lalu klik slot kosong = buat tombol.");
    }

    private void saveScreen(Player p, Screen sc) {
        try {
            sc.cfg.save(sc.file);
        } catch (IOException ex) {
            plugin.send(p, "<red>Gagal menyimpan " + sc.file.getName());
            return;
        }
        plugin.screens().load();
        plugin.screens().open(p, sc.id, new String[0], true);
    }

    public void onScreenClick(Player p, ScreenHolder h, InventoryClickEvent e) {
        int slot = e.getRawSlot();
        Screen sc = h.screen;
        UUID u = p.getUniqueId();
        ConfigurationSection btn = h.buttons.get(slot);
        String sel = selected.get(u);

        if (sel != null) {
            ConfigurationSection src = sc.cfg.getConfigurationSection("buttons." + sel);
            selected.remove(u);
            if (src == null) return;
            if (btn == src) { plugin.send(p, "<yellow>Pilihan dibatalkan."); return; }
            if (src.contains("slots") || (btn != null && btn.contains("slots"))) {
                plugin.send(p, "<red>Tombol dengan banyak slot tidak bisa dipindah di editor (ubah 'slots' di file).");
                return;
            }
            if (btn != null) btn.set("slot", src.getInt("slot"));
            src.set("slot", slot);
            saveScreen(p, sc);
            return;
        }

        if (btn != null) {
            if (e.getClick().isRightClick() || e.getClick().isShiftClick()) {
                buttonMenu(p, sc, btn.getName());
            } else {
                selected.put(u, btn.getName());
                plugin.send(p, "<green>Dipilih: <white>" + btn.getName() + "</white>. <gray>Klik slot tujuan.");
            }
            return;
        }

        ItemStack cur = e.getCursor();
        if (cur != null && !cur.getType().isAir()) {
            int n = 1;
            while (sc.cfg.contains("buttons.btn" + n)) n++;
            String key = "btn" + n;
            sc.cfg.set("buttons." + key + ".slot", slot);
            sc.cfg.set("buttons." + key + ".material", cur.getType().name());
            sc.cfg.set("buttons." + key + ".name", "<white>Tombol " + n);
            sc.cfg.set("buttons." + key + ".actions", new ArrayList<String>());
            saveScreen(p, sc);
        } else {
            plugin.send(p, "<gray>Pegang item di kursor lalu klik slot kosong untuk membuat tombol.");
        }
    }

    private void buttonMenu(Player p, Screen sc, String key) {
        String base = "buttons." + key + ".";
        Menu m = new Menu(Items.c("<dark_gray>Tombol: " + key), 3);
        m.fill(Material.BLACK_STAINED_GLASS_PANE);

        m.set(10, Items.make(Material.NAME_TAG, "<yellow>Ganti nama", "<gray>Sekarang: <white>" + sc.cfg.getString(base + "name", "-")),
                pl -> prompt(pl, "Ketik nama baru (boleh MiniMessage).", t -> { sc.cfg.set(base + "name", t); saveScreen(pl, sc); }));
        m.set(11, Items.make(Material.ITEM_FRAME, "<yellow>Ganti material", "<gray>Pegang item di tangan lalu klik"), pl -> {
            ItemStack hand = pl.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) { plugin.send(pl, "<red>Tanganmu kosong."); return; }
            sc.cfg.set(base + "material", hand.getType().name());
            saveScreen(pl, sc);
        });
        m.set(12, Items.make(Material.PAPER, "<yellow>Tambah baris lore"),
                pl -> prompt(pl, "Ketik baris lore.", t -> {
                    List<String> l = new ArrayList<>(sc.cfg.getStringList(base + "lore"));
                    l.add(t);
                    sc.cfg.set(base + "lore", l);
                    saveScreen(pl, sc);
                }));
        m.set(13, Items.make(Material.COMMAND_BLOCK, "<yellow>Tambah aksi",
                        "<gray>Contoh: <white>[command] spawn", "<gray>[console] [message] [open] [close] [sound] [title] [hud]"),
                pl -> prompt(pl, "Ketik aksi, contoh: [command] spawn", t -> {
                    List<String> l = new ArrayList<>(sc.cfg.getStringList(base + "actions"));
                    l.add(t);
                    sc.cfg.set(base + "actions", l);
                    saveScreen(pl, sc);
                }));
        m.set(14, Items.make(Material.HOPPER, "<yellow>Hapus semua aksi"), pl -> {
            sc.cfg.set(base + "actions", new ArrayList<String>());
            saveScreen(pl, sc);
        });
        m.set(16, Items.make(Material.TNT, "<red>Hapus tombol"), pl -> {
            sc.cfg.set("buttons." + key, null);
            saveScreen(pl, sc);
        });
        m.set(22, Items.make(Material.ARROW, "<yellow>← Kembali"), pl -> plugin.screens().open(pl, sc.id, new String[0], true));
        m.open(p);
    }

    // ================= Editor HUD =================

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rowsOf(HudManager.Def d) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<?, ?> m : d.cfg.getMapList("rows")) {
            Map<String, Object> row = new LinkedHashMap<>((Map<String, Object>) m);
            List<Map<String, Object>> layers = new ArrayList<>();
            if (row.get("layers") instanceof List<?> l) {
                for (Object o : l) if (o instanceof Map<?, ?> lm) layers.add(new LinkedHashMap<>((Map<String, Object>) lm));
            }
            row.put("layers", layers);
            out.add(row);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> layersOf(Map<String, Object> row) {
        return (List<Map<String, Object>>) row.get("layers");
    }

    private void saveHud(HudManager.Def d, List<Map<String, Object>> rows) {
        d.cfg.set("rows", rows);
        try {
            d.cfg.save(d.file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Gagal menyimpan " + d.file.getName());
        }
    }

    private interface LayerEdit { void apply(Map<String, Object> layer); }

    private boolean updateLayer(String id, int r, int l, LayerEdit edit) {
        HudManager.Def d = plugin.huds().get(id);
        if (d == null) return false;
        List<Map<String, Object>> rows = rowsOf(d);
        if (r >= rows.size() || l >= layersOf(rows.get(r)).size()) return false;
        edit.apply(layersOf(rows.get(r)).get(l));
        saveHud(d, rows);
        return true;
    }

    public void hudLayers(Player p, String id) {
        HudManager.Def d = plugin.huds().get(id);
        if (d == null) return;
        plugin.huds().show(p, id);
        List<Map<String, Object>> rows = rowsOf(d);
        Menu m = new Menu(Items.c("<dark_gray>HUD: " + id), 6);
        m.fill(Material.BLACK_STAINED_GLASS_PANE);

        int slot = 0;
        for (int r = 0; r < rows.size(); r++) {
            String rowType = String.valueOf(rows.get(r).get("type"));
            List<Map<String, Object>> layers = layersOf(rows.get(r));
            for (int l = 0; l < layers.size(); l++) {
                if (slot >= 45) break;
                Map<String, Object> ly = layers.get(l);
                String type = String.valueOf(ly.get("type"));
                Material mat = switch (type) {
                    case "image" -> Material.PAINTING;
                    case "text" -> Material.NAME_TAG;
                    case "bar" -> Material.LIME_DYE;
                    default -> Material.CLOCK;
                };
                String what = ly.get("id") != null ? String.valueOf(ly.get("id"))
                        : ly.get("text") != null ? String.valueOf(ly.get("text")) : type;
                final int rr = r, ll = l;
                m.set(slot++, Items.make(mat, "<white>Baris " + (r + 1) + " (" + rowType + ") · Layer " + (l + 1),
                                "<gray>Tipe: <white>" + type, "<gray>x = <white>" + ly.get("x"), "<gray>" + what.replace("<", "‹")),
                        pl -> layerMenu(pl, id, rr, ll));
            }
        }
        m.set(45, Items.make(Material.ARROW, "<yellow>← Kembali"), this::openMain);
        m.set(47, Items.make(Material.NAME_TAG, "<green>+ Layer teks"), pl -> prompt(pl, "Ketik teks (boleh MiniMessage dan %placeholder%).", t -> {
            List<Map<String, Object>> rs = rowsOf(d);
            if (rs.isEmpty()) rs.add(newRow());
            Map<String, Object> ly = new LinkedHashMap<>();
            ly.put("type", "text"); ly.put("text", t); ly.put("x", 0);
            layersOf(rs.get(rs.size() - 1)).add(ly);
            saveHud(d, rs);
            hudLayers(pl, id);
        }));
        m.set(48, Items.make(Material.PAINTING, "<green>+ Layer gambar"), pl -> {
            String ids = String.join(", ", plugin.images().all().stream().map(i -> i.id).toList());
            plugin.send(pl, "<gray>Gambar tersedia: <white>" + ids);
            prompt(pl, "Ketik id gambar.", t -> {
                List<Map<String, Object>> rs = rowsOf(d);
                if (rs.isEmpty()) rs.add(newRow());
                Map<String, Object> ly = new LinkedHashMap<>();
                ly.put("type", "image"); ly.put("id", t.toLowerCase()); ly.put("x", 0);
                layersOf(rs.get(rs.size() - 1)).add(ly);
                saveHud(d, rs);
                hudLayers(pl, id);
            });
        });
        m.set(50, Items.make(Material.BLAZE_POWDER, "<green>+ Baris boss bar"), pl -> {
            List<Map<String, Object>> rs = rowsOf(d);
            rs.add(newRow());
            saveHud(d, rs);
            hudLayers(pl, id);
        });
        m.set(53, Items.make(Material.SPYGLASS, "<aqua>Tampilkan HUD ini", "<gray>Lihat hasilnya langsung"), pl -> {
            plugin.huds().show(pl, id);
            plugin.send(pl, "<green>HUD ditampilkan. <gray>Layer baru ditambahkan ke baris terakhir.");
        });
        m.open(p);
    }

    private Map<String, Object> newRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "bossbar");
        row.put("layers", new ArrayList<Map<String, Object>>());
        return row;
    }

    private void layerMenu(Player p, String id, int r, int l) {
        Menu m = new Menu(Items.c("<dark_gray>Layer " + (r + 1) + "." + (l + 1) + " - " + id), 3);
        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            HudManager.Def d = plugin.huds().get(id);
            List<Map<String, Object>> rows = d == null ? List.of() : rowsOf(d);
            if (r >= rows.size() || l >= layersOf(rows.get(r)).size()) { hudLayers(p, id); return; }
            Map<String, Object> ly = layersOf(rows.get(r)).get(l);
            String type = String.valueOf(ly.get("type"));
            int x = ly.get("x") instanceof Number n ? n.intValue() : 0;

            m.clear();
            m.fill(Material.BLACK_STAINED_GLASS_PANE);
            int[] deltas = {-10, -1, 1, 10};
            int[] slots = {10, 11, 15, 16};
            for (int i = 0; i < 4; i++) {
                final int dx = deltas[i];
                m.set(slots[i], Items.make(Material.ARROW, (dx < 0 ? "<red>◀ " : "<green>▶ ") + Math.abs(dx) + " px"), pl -> {
                    updateLayer(id, r, l, layer -> layer.put("x", x + dx));
                    plugin.huds().show(pl, id);
                    render[0].run();
                });
            }
            m.set(13, Items.make(Material.PAPER, "<white>x = " + x, "<gray>Tipe: <white>" + type,
                    "<gray>x = piksel dari tengah layar", "<gray>(HUD tampil langsung)"), null);

            String field = switch (type) {
                case "text" -> "text";
                case "image" -> "id";
                case "bar" -> "value";
                default -> "frames";
            };
            m.set(19, Items.make(Material.WRITABLE_BOOK, "<yellow>Ubah " + field), pl -> prompt(pl,
                    field.equals("frames") ? "Ketik id frame dipisah koma (contoh: coin_0,coin_1,coin_2)." : "Ketik nilai baru untuk " + field + ".",
                    t -> {
                        updateLayer(id, r, l, layer -> {
                            if (field.equals("frames")) {
                                List<String> fr = new ArrayList<>();
                                for (String s : t.split(",")) if (!s.isBlank()) fr.add(s.trim().toLowerCase());
                                layer.put("frames", fr);
                            } else layer.put(field, field.equals("id") ? t.toLowerCase() : t);
                        });
                        plugin.huds().show(pl, id);
                        layerMenu(pl, id, r, l);
                    }));
            m.set(23, Items.make(Material.TNT, "<red>Hapus layer"), pl -> {
                HudManager.Def dd = plugin.huds().get(id);
                if (dd != null) {
                    List<Map<String, Object>> rs = rowsOf(dd);
                    if (r < rs.size() && l < layersOf(rs.get(r)).size()) layersOf(rs.get(r)).remove(l);
                    saveHud(dd, rs);
                }
                hudLayers(pl, id);
            });
            m.set(25, Items.make(Material.ARROW, "<yellow>← Kembali"), pl -> hudLayers(pl, id));
        };
        render[0].run();
        m.open(p);
    }
}
