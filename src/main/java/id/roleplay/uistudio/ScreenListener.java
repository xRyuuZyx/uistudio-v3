package id.roleplay.uistudio;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ScreenListener implements Listener {
    private final UIStudio plugin;
    private final Map<UUID, Long> last = new HashMap<>();

    public ScreenListener(UIStudio plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof ScreenHolder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;

        if (h.editing) {
            plugin.editor().onScreenClick(p, h, e);
            return;
        }

        ConfigurationSection b = h.buttons.get(e.getRawSlot());
        if (b == null) return;

        long now = System.currentTimeMillis();
        if (now - last.getOrDefault(p.getUniqueId(), 0L) < plugin.getConfig().getLong("click-cooldown-ms", 150)) return;
        last.put(p.getUniqueId(), now);

        ClickType t = e.getClick();
        List<String> acts;
        if (t == ClickType.SHIFT_LEFT && b.contains("shift-left-actions")) acts = b.getStringList("shift-left-actions");
        else if (t.isRightClick() && b.contains("right-actions")) acts = b.getStringList("right-actions");
        else if (t.isLeftClick() && b.contains("left-actions")) acts = b.getStringList("left-actions");
        else acts = b.getStringList("actions");
        if (acts.isEmpty()) return;

        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.actions().run(p, h.args, acts));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof ScreenHolder) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { last.remove(e.getPlayer().getUniqueId()); }
}
