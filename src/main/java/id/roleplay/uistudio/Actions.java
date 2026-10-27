package id.roleplay.uistudio;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/** [command] [console] [close] [open] [message] [actionbar] [title] [sound] [hud] */
public class Actions {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final UIStudio plugin;

    public Actions(UIStudio plugin) { this.plugin = plugin; }

    public void run(Player p, String[] args, List<String> actions) {
        for (String line : actions) {
            String a = line.trim();
            String tag = "";
            String arg = a;
            if (a.startsWith("[")) {
                int i = a.indexOf(']');
                if (i > 0) {
                    tag = a.substring(1, i).toLowerCase();
                    arg = a.substring(i + 1).trim();
                }
            }
            String f = plugin.placeholders().apply(p, arg, args);
            switch (tag) {
                case "command" -> p.performCommand(f.startsWith("/") ? f.substring(1) : f);
                case "console" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), f.startsWith("/") ? f.substring(1) : f);
                case "close" -> p.closeInventory();
                case "open" -> {
                    String[] parts = f.split("\\s+");
                    if (parts.length > 0 && !parts[0].isBlank()) {
                        plugin.screens().open(p, parts[0], Arrays.copyOfRange(parts, 1, parts.length));
                    }
                }
                case "hud" -> {
                    String[] parts = f.split("\\s+");
                    if (parts.length >= 2) {
                        switch (parts[0].toLowerCase()) {
                            case "show" -> plugin.huds().show(p, parts[1]);
                            case "hide" -> plugin.huds().hide(p, parts[1]);
                            case "toggle" -> plugin.huds().toggle(p, parts[1]);
                            default -> { }
                        }
                    }
                }
                case "message" -> p.sendMessage(MM.deserialize(f));
                case "actionbar" -> p.sendActionBar(MM.deserialize(f));
                case "title" -> {
                    String[] t = f.split("\\|", 2);
                    p.showTitle(Title.title(MM.deserialize(t[0]), MM.deserialize(t.length > 1 ? t[1] : ""),
                            Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500))));
                }
                case "sound" -> {
                    String[] s = f.split("\\s+");
                    float vol = 1f, pitch = 1f;
                    try {
                        if (s.length > 1) vol = Float.parseFloat(s[1]);
                        if (s.length > 2) pitch = Float.parseFloat(s[2]);
                    } catch (NumberFormatException ignored) { }
                    p.playSound(p.getLocation(), s[0], vol, pitch);
                }
                default -> { }
            }
        }
    }
}
