package id.roleplay.uistudio;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Menu chest sederhana untuk editor. */
public class Menu implements InventoryHolder, Listener {
    private final Inventory inv;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>();

    /** Dipakai sebagai listener global (tanpa inventory). */
    public Menu() { this.inv = null; }

    public Menu(Component title, int rows) { this.inv = Bukkit.createInventory(this, rows * 9, title); }

    @Override public Inventory getInventory() { return inv; }

    public void clear() { inv.clear(); actions.clear(); }

    public void fill(Material pane) {
        ItemStack f = Items.make(pane, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, f);
    }

    public void set(int slot, ItemStack item, Consumer<Player> onClick) {
        if (slot < 0 || slot >= inv.getSize()) return;
        inv.setItem(slot, item);
        if (onClick != null) actions.put(slot, onClick); else actions.remove(slot);
    }

    public void open(Player p) { p.openInventory(inv); }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu m)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        Consumer<Player> a = m.actions.get(e.getRawSlot());
        if (a != null) a.accept(p);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
}
