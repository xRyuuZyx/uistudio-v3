package id.roleplay.uistudio;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Membuat resource pack (zip) dari images/, icons/, models/. Hasil selalu sama kalau isinya sama. */
public class PackBuilder {
    public static class Result {
        public File zip;
        public String sha1;
        public int images, icons, models;
    }

    private final UIStudio plugin;

    public PackBuilder(UIStudio plugin) { this.plugin = plugin; }

    private static byte[] transparentPng(int w, int h) throws IOException {
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(bi, "png", out);
        return out.toByteArray();
    }

    private static String esc(char c) { return String.format("\\u%04x", (int) c); }

    private static String itemDef(String id) {
        return "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"uistudio:item/" + id + "\"}}";
    }

    private static String itemModel(String id) {
        return "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"uistudio:item/" + id + "\"}}";
    }

    public Result build() throws IOException {
        Map<String, byte[]> e = new TreeMap<>();
        Result r = new Result();
        int format = plugin.getConfig().getInt("pack.format", 75);
        e.put("pack.mcmeta", ("{\"pack\":{\"pack_format\":" + format + ",\"description\":\"UIStudio\"}}")
                .getBytes(StandardCharsets.UTF_8));

        // --- font: gambar + karakter geser
        StringBuilder font = new StringBuilder("{\"providers\":[");
        boolean first = true;
        for (Images.Img i : plugin.images().all()) {
            if (!first) font.append(',');
            first = false;
            font.append("{\"type\":\"bitmap\",\"file\":\"uistudio:ui/").append(i.id).append(".png\",\"ascent\":")
                    .append(i.ascent).append(",\"height\":").append(i.height)
                    .append(",\"chars\":[\"").append(esc(i.ch)).append("\"]}");
            e.put("assets/uistudio/textures/ui/" + i.id + ".png", Files.readAllBytes(i.file.toPath()));
            r.images++;
        }
        if (!first) font.append(',');
        font.append("{\"type\":\"space\",\"advances\":{");
        for (int bit = 0; bit < 10; bit++) {
            if (bit > 0) font.append(',');
            font.append('"').append(esc(Ui.shiftChar(true, bit))).append("\":").append(1 << bit).append(',');
            font.append('"').append(esc(Ui.shiftChar(false, bit))).append("\":").append(-(1 << bit));
        }
        font.append("}}]}");
        e.put("assets/uistudio/font/ui.json", font.toString().getBytes(StandardCharsets.UTF_8));

        // --- item polos (tak terlihat)
        e.put("assets/uistudio/textures/item/empty.png", transparentPng(16, 16));
        e.put("assets/uistudio/models/item/empty.json", itemModel("empty").getBytes(StandardCharsets.UTF_8));
        e.put("assets/uistudio/items/empty.json", itemDef("empty").getBytes(StandardCharsets.UTF_8));

        // --- ikon item (icons/*.png)
        File[] icons = new File(plugin.getDataFolder(), "icons").listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
        if (icons != null) {
            Arrays.sort(icons);
            for (File f : icons) {
                String id = Images.sanitize(f.getName().substring(0, f.getName().length() - 4));
                e.put("assets/uistudio/textures/item/" + id + ".png", Files.readAllBytes(f.toPath()));
                e.put("assets/uistudio/models/item/" + id + ".json", itemModel(id).getBytes(StandardCharsets.UTF_8));
                e.put("assets/uistudio/items/" + id + ".json", itemDef(id).getBytes(StandardCharsets.UTF_8));
                r.icons++;
            }
        }

        // --- model 3D (models/*.json dari Blockbench) + models/textures/*.png
        File mdir = new File(plugin.getDataFolder(), "models");
        File[] models = mdir.listFiles((d, n) -> n.toLowerCase().endsWith(".json"));
        if (models != null) {
            Arrays.sort(models);
            for (File f : models) {
                String id = Images.sanitize(f.getName().substring(0, f.getName().length() - 5));
                e.put("assets/uistudio/models/item/" + id + ".json", Files.readAllBytes(f.toPath()));
                e.put("assets/uistudio/items/" + id + ".json", itemDef(id).getBytes(StandardCharsets.UTF_8));
                r.models++;
            }
        }
        File[] mtex = new File(mdir, "textures").listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
        if (mtex != null) {
            Arrays.sort(mtex);
            for (File f : mtex) {
                String id = Images.sanitize(f.getName().substring(0, f.getName().length() - 4));
                e.put("assets/uistudio/textures/item/" + id + ".png", Files.readAllBytes(f.toPath()));
            }
        }

        // --- sembunyikan bar boss bawaan
        if (plugin.getConfig().getBoolean("pack.hide-bossbar-sprites", true)) {
            byte[] t = transparentPng(182, 5);
            for (String c : new String[]{"pink", "blue", "red", "green", "yellow", "purple", "white"}) {
                e.put("assets/minecraft/textures/gui/sprites/boss_bar/" + c + "_background.png", t);
                e.put("assets/minecraft/textures/gui/sprites/boss_bar/" + c + "_progress.png", t);
            }
        }

        // --- sembunyikan ikon hati / lapar / armor bawaan (opsional)
        if (plugin.getConfig().getBoolean("pack.hide-vanilla-hud", false)) {
            byte[] t9 = transparentPng(9, 9);
            String[] kinds = {"", "poisoned_", "withered_", "absorbing_", "frozen_"};
            for (String k : kinds) {
                for (String h : new String[]{"full", "half", "hardcore_full", "hardcore_half"}) {
                    e.put("assets/minecraft/textures/gui/sprites/hud/heart/" + k + h + ".png", t9);
                    e.put("assets/minecraft/textures/gui/sprites/hud/heart/" + k + h + "_blinking.png", t9);
                }
            }
            for (String h : new String[]{"container", "container_blinking", "container_hardcore", "container_hardcore_blinking"}) {
                e.put("assets/minecraft/textures/gui/sprites/hud/heart/" + h + ".png", t9);
            }
            for (String f : new String[]{"food_empty", "food_empty_hunger", "food_full", "food_full_hunger", "food_half",
                    "food_half_hunger", "armor_empty", "armor_full", "armor_half", "air", "air_bursting", "air_empty"}) {
                e.put("assets/minecraft/textures/gui/sprites/hud/" + f + ".png", t9);
            }
        }

        // --- sembunyikan bar XP bawaan (opsional)
        if (plugin.getConfig().getBoolean("pack.hide-vanilla-xp", false)) {
            byte[] tx = transparentPng(182, 5);
            e.put("assets/minecraft/textures/gui/sprites/hud/experience_bar_background.png", tx);
            e.put("assets/minecraft/textures/gui/sprites/hud/experience_bar_progress.png", tx);
        }

        // --- tulis zip (urutan & waktu tetap supaya hash stabil)
        File out = new File(plugin.getDataFolder(), "pack/uistudio-pack.zip");
        out.getParentFile().mkdirs();
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(out.toPath()))) {
            zos.setLevel(9);
            for (Map.Entry<String, byte[]> en : e.entrySet()) {
                ZipEntry ze = new ZipEntry(en.getKey());
                ze.setTimeLocal(LocalDateTime.of(2020, 1, 1, 0, 0));
                zos.putNextEntry(ze);
                zos.write(en.getValue());
                zos.closeEntry();
            }
        }

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest(Files.readAllBytes(out.toPath()))) sb.append(String.format("%02x", b));
            r.sha1 = sb.toString();
        } catch (Exception ex) {
            throw new IOException(ex);
        }
        r.zip = out;
        return r;
    }
}
