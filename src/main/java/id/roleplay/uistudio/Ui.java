package id.roleplay.uistudio;

import net.kyori.adventure.key.Key;

public final class Ui {
    /** Font milik pack UIStudio: berisi gambar + karakter geser (space). */
    public static final Key FONT = Key.key("uistudio", "ui");

    private Ui() {}

    public static char shiftChar(boolean positive, int bit) {
        return (char) ((positive ? 0xF820 : 0xF830) + bit);
    }

    /** Karakter spasi yang menggeser kursor teks sejauh n piksel (boleh negatif). */
    public static String shift(int n) {
        if (n == 0) return "";
        boolean pos = n > 0;
        int a = Math.abs(n);
        StringBuilder sb = new StringBuilder();
        while (a > 0) {
            int chunk = Math.min(a, 1023);
            for (int i = 0; i < 10; i++) if ((chunk & (1 << i)) != 0) sb.append(shiftChar(pos, i));
            a -= chunk;
        }
        return sb.toString();
    }

    /** Perkiraan lebar teks font default Minecraft (piksel GUI, termasuk jarak antar huruf). */
    public static int textWidth(String s) {
        int w = 0;
        for (char ch : s.toCharArray()) {
            w += switch (ch) {
                case ' ' -> 4;
                case '!', '.', ',', ':', ';', 'i', '|' -> 2;
                case '\'', 'l', '`' -> 3;
                case 'I', 't', '[', ']', '*' -> 4;
                case '(', ')', '{', '}', '<', '>', 'f', 'k', '"' -> 5;
                case '@', '~' -> 7;
                default -> 6;
            };
        }
        return w;
    }
}
