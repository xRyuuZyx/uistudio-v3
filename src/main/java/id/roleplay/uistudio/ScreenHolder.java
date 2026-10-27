package id.roleplay.uistudio;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public class ScreenHolder implements InventoryHolder {
    public final Screen screen;
    public final String[] args;
    public final boolean editing;
    public final Map<Integer, ConfigurationSection> buttons = new HashMap<>();
    public final Map<Integer, ConfigurationSection> animated = new HashMap<>();
    public final Map<Integer, Integer> lastFrame = new HashMap<>();
    public Inventory inv;

    public ScreenHolder(Screen screen, String[] args, boolean editing) {
        this.screen = screen;
        this.args = args;
        this.editing = editing;
    }

    @Override public Inventory getInventory() { return inv; }
}
