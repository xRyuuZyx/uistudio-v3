package id.roleplay.uistudio;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** Placeholder bawaan + PlaceholderAPI (opsional). Hasilnya masih berupa teks MiniMessage. */
public class Placeholders {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final UIStudio plugin;
    private Method papiSet;

    public Placeholders(UIStudio plugin) {
        this.plugin = plugin;
        try {
            papiSet = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
                    .getMethod("setPlaceholders", OfflinePlayer.class, String.class);
        } catch (Exception e) {
            papiSet = null;
        }
    }

    public boolean hasPapi() {
        return papiSet != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public String apply(Player p, String text, String[] args) {
        if (text == null) return "";
        String r = text;
        if (r.indexOf('%') >= 0) {
            r = r.replace("%player%", MM.escapeTags(p.getName()));
            if (r.contains("%money%")) r = r.replace("%money%", String.format(Locale.US, "%,.0f", balance(p)));
            if (r.contains("%bank%")) r = r.replace("%bank%", bank(p));
            if (r.contains("%job%")) r = r.replace("%job%", job(p));
            if (r.contains("%gang%")) r = r.replace("%gang%", gang(p));
            if (r.contains("%health%")) r = r.replace("%health%", String.valueOf((int) Math.ceil(p.getHealth())));
            if (r.contains("%food%")) r = r.replace("%food%", String.valueOf(p.getFoodLevel()));
            if (r.contains("%armor%")) {
                AttributeInstance a = p.getAttribute(Attribute.ARMOR);
                r = r.replace("%armor%", String.valueOf(a == null ? 0 : (int) a.getValue()));
            }
            if (r.contains("%air%")) {
                int max = p.getMaximumAir();
                r = r.replace("%air%", String.valueOf(max <= 0 ? 100 : Math.max(0, p.getRemainingAir()) * 100 / max));
            }
            if (r.contains("%saturation%")) r = r.replace("%saturation%", String.valueOf((int) Math.ceil(p.getSaturation())));
            if (r.contains("%voice_")) {
                r = r.replace("%voice_color%", Voice.color(p.getUniqueId()))
                        .replace("%voice_icon%", Voice.icon(p.getUniqueId()))
                        .replace("%voice_value%", "100");
            }
            r = r.replace("%ping%", String.valueOf(p.getPing()))
                    .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
            if (r.contains("%time%")) {
                r = r.replace("%time%", ZonedDateTime.now(ZoneId.of("Asia/Makassar")).format(DateTimeFormatter.ofPattern("HH:mm")));
            }
            for (int i = 0; i < 9; i++) {
                r = r.replace("%arg" + (i + 1) + "%", args != null && i < args.length ? MM.escapeTags(args[i]) : "");
            }
            if (hasPapi()) {
                try {
                    r = (String) papiSet.invoke(null, p, r);
                } catch (Exception ignored) { }
            }
        }
        return r;
    }

    private String bank(Player p) {
        String expr = plugin.getConfig().getString("hud.bank-placeholder", "0");
        String v = expr;
        if (expr.indexOf('%') >= 0 && hasPapi()) {
            try {
                v = (String) papiSet.invoke(null, p, expr);
            } catch (Exception e) {
                v = "0";
            }
        }
        v = v == null ? "0" : v.trim().replace(",", "");
        try {
            return String.format(Locale.US, "%,.0f", Double.parseDouble(v));
        } catch (NumberFormatException e) {
            return MM.escapeTags(v);
        }
    }

    private double balance(Player p) {
        try {
            Class<?> eco = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(eco);
            if (rsp == null) return 0;
            Method m = eco.getMethod("getBalance", OfflinePlayer.class);
            return ((Number) m.invoke(rsp.getProvider(), p)).doubleValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private String job(Player p) {
        try {
            Plugin rj = Bukkit.getPluginManager().getPlugin("RoleplayJobs");
            if (rj == null || !rj.isEnabled()) return "-";
            Object jm = rj.getClass().getMethod("jobs").invoke(rj);
            Object job = jm.getClass().getMethod("getJob", UUID.class).invoke(jm, p.getUniqueId());
            if (job == null) return "Pengangguran";
            return String.valueOf(job.getClass().getMethod("display").invoke(job));
        } catch (Exception e) {
            return "-";
        }
    }

    private String gang(Player p) {
        try {
            Plugin hud = Bukkit.getPluginManager().getPlugin("RoleplayHUD");
            if (hud == null || !hud.isEnabled()) return "-";
            Object gm = hud.getClass().getMethod("gangs").invoke(hud);
            Object g = gm.getClass().getMethod("byPlayer", UUID.class).invoke(gm, p.getUniqueId());
            if (g == null) return "-";
            return MM.escapeTags(String.valueOf(g.getClass().getField("name").get(g)));
        } catch (Exception e) {
            return "-";
        }
    }
}
