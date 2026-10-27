package id.roleplay.uistudio;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class Screen {
    public final String id;
    public final File file;
    public final YamlConfiguration cfg;

    public Screen(String id, File file, YamlConfiguration cfg) {
        this.id = id;
        this.file = file;
        this.cfg = cfg;
    }
}
