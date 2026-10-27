package id.roleplay.uistudio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.util.*;

/** Layar GUI: gambar latar (font UIStudio) + tombol + animasi item. */
public class ScreenManager {
    private final UIStudio plugin;
    private final Map<String, Screen> screens = new LinkedHashMap<>();
    private final Set<String> registeredCommands = new HashSet<>();

    public ScreenManager(UIStudio plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::animate, 20L, 2L);
    }

    public Set<String> ids() { return screens.keySet(); }

    public Screen get(String id) { return screens.get(id.toLowerCase()); }

    public void load() {
        screens.clear();
        File dir = new File(plugin.getDataFolder(), "screens");
        if (!dir.exists()) {
            dir.mkdirs();
            plugin.saveResource("screens/profile.yml", false);
            plugin.saveResource("screens/shop.yml", false);
        }
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        Arrays.sort(files);
        for (File f : files) {
            String id = f.getName().substring(0, f.getName().length() - 4).toLowerCase();
            Screen sc = new Screen(id, f, YamlConfiguration.loadConfiguration(f));
            screens.put(id, sc);
            String cmd = sc.cfg.getString("open-command", "");
            if (!cmd.isBlank() && registeredCommands.add(cmd.toLowerCase())) {
                Bukkit.getCommandMap().register("uistudio", new ScreenCommand(cmd.toLowerCase(), id));
            }
        }
        plugin.getLogger().info(screens.size() + " layar dimuat.");
    }

    private static int num(Object o, int def) { return o instanceof Number n ? n.intValue() : def; }

    // ---------------- Judul ----------------

    private Component title(Player p, Screen sc, String[] args) {
        Row row = new Row(plugin.images(), 8); // judul inventory dimulai 8 px dari tepi kiri
        String bg = sc.cfg.getString("title.background", "");
        if (!bg.isBlank()) row.image(bg, null, 0);
        for (Map<?, ?> l : sc.cfg.getMapList("title.layers")) {
            int x = num(l.get("x"), 8);
            String type = l.get("type") == null ? "text" : String.valueOf(l.get("type")).toLowerCase();
            if (type.equals("image")) {
                row.image(String.valueOf(l.get("id")), null, x);
            } else {
                row.text(plugin.placeholders().apply(p, String.valueOf(l.get("text")), args), x);
            }
        }
        return row.build();
    }

    // ---------------- Tombol ----------------

    private static List<Integer> slots(ConfigurationSection b, int max) {
        List<Integer> out = new ArrayList<>();
        if (b.contains("slot")) out.add(b.getInt("slot"));
        for (Object o : b.getList("slots", List.of())) {
            String s = String.valueOf(o);
            try {
                if (s.contains("-")) {
                    String[] p = s.split("-");
                    for (int i = Integer.parseInt(p[0].trim()); i <= Integer.parseInt(p[1].trim()); i++) out.add(i);
                } else out.add(Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignored) { }
        }
        out.removeIf(i -> i < 0 || i >= max);
        return out;
    }

    private ItemStack item(Player p, ConfigurationSection b, String[] args, int frame) {
        boolean invisible = b.getBoolean("invisible", false);
        Material m = Material.matchMaterial(b.getString("material", invisible ? "PAPER" : "STONE"));
        if (m == null || m.isAir()) m = Material.PAPER;
        ItemStack it = new ItemStack(m, Math.max(1, Math.min(64, b.getInt("amount", 1))));
        ItemMeta meta = it.getItemMeta();
        Placeholders ph = plugin.placeholders();

        String name = b.getString("name");
        if (name != null) meta.displayName(Items.c(ph.apply(p, name, args)));
        List<String> lore = b.getStringList("lore");
        if (!lore.isEmpty()) meta.lore(lore.stream().map(l -> Items.c(ph.apply(p, l, args))).toList());

        String model = b.getString("item-model", invisible ? "uistudio:empty" : "");
        List<String> frames = b.getStringList("frames");
        if (!frames.isEmpty()) model = "uistudio:" + frames.get(Math.floorMod(frame, frames.size()));
        if (!model.isBlank()) {
            NamespacedKey key = NamespacedKey.fromString(model);
            if (key != null) meta.setItemModel(key);
        }
        if (invisible && name == null) meta.setHideTooltip(true);
        if (b.getBoolean("glow", false)) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (meta instanceof SkullMeta sm && b.contains("head")) {
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(ph.apply(p, b.getString("head"), args)));
        }
        it.setItemMeta(meta);
        return it;
    }

    // ---------------- Buka layar ----------------

    public void open(Player p, String id, String[] args) { open(p, id, args, false); }

    public void open(Player p, String id, String[] args, boolean editing) {
        Screen sc = screens.get(id.toLowerCase());
        if (sc == null) {
            plugin.send(p, plugin.getConfig().getString("messages.no-screen", "").replace("%screen%", id));
            return;
        }
        String perm = sc.cfg.getString("permission", "");
        if (!editing && !perm.isBlank() && !p.hasPermission(perm)) {
            plugin.send(p, plugin.getConfig().getString("messages.no-permission", ""));
            return;
        }

        int rows = Math.max(1, Math.min(6, sc.cfg.getInt("rows", 3)));
        ScreenHolder holder = new ScreenHolder(sc, args, editing);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title(p, sc, args));
        holder.inv = inv;

        ConfigurationSection bs = sc.cfg.getConfigurationSection("buttons");
        if (bs != null) {
            for (String key : bs.getKeys(false)) {
                ConfigurationSection b = bs.getConfigurationSection(key);
                if (b == null) continue;
                ItemStack it = item(p, b, args, 0);
                boolean anim = !b.getStringList("frames").isEmpty();
                for (int slot : slots(b, rows * 9)) {
                    inv.setItem(slot, it);
                    holder.buttons.put(slot, b);
                    if (anim) holder.animated.put(slot, b);
                }
            }
        }
        p.openInventory(inv);
        String snd = sc.cfg.getString("open-sound", "");
        if (!editing && !snd.isBlank()) p.playSound(p.getLocation(), snd, 1f, 1f);
    }

    /** Animasi item: ganti model tiap beberapa tick. */
    private void animate() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof ScreenHolder h)) continue;
            if (h.animated.isEmpty()) continue;
            for (Map.Entry<Integer, ConfigurationSection> e : h.animated.entrySet()) {
                ConfigurationSection b = e.getValue();
                int ticks = Math.max(1, b.getInt("frame-ticks", 4));
                int frame = (int) ((now / 50) / ticks);
                int n = b.getStringList("frames").size();
                int idx = Math.floorMod(frame, Math.max(1, n));
                if (h.lastFrame.getOrDefault(e.getKey(), -1) != idx) {
                    h.lastFrame.put(e.getKey(), idx);
                    h.inv.setItem(e.getKey(), item(p, b, h.args, idx));
                }
            }
        }
    }

    private class ScreenCommand extends Command {
        private final String screenId;

        ScreenCommand(String name, String screenId) {
            super(name);
            this.screenId = screenId;
            setDescription("Buka layar " + screenId);
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (sender instanceof Player p) open(p, screenId, args);
            return true;
        }
    }
}
