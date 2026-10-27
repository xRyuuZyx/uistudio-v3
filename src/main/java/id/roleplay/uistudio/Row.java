package id.roleplay.uistudio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Menyusun satu baris teks + gambar dengan posisi horizontal tepat (piksel). */
public final class Row {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final Images images;
    private final TextComponent.Builder b = Component.text();
    private int pos;

    public Row(Images images, int startPos) {
        this.images = images;
        this.pos = startPos;
    }

    public int pos() { return pos; }

    public Row moveTo(int x) {
        int d = x - pos;
        if (d != 0) {
            b.append(Component.text(Ui.shift(d)).font(Ui.FONT));
            pos = x;
        }
        return this;
    }

    public Row image(String id, TextColor tint, int x) {
        Images.Img img = images.get(id);
        if (img == null) return this;
        moveTo(x);
        b.append(Component.text(String.valueOf(img.ch)).font(Ui.FONT).color(tint == null ? NamedTextColor.WHITE : tint));
        pos += img.advance;
        return this;
    }

    public Row text(String mini, int x) {
        moveTo(x);
        Component c = MM.deserialize(mini);
        b.append(c);
        pos += Ui.textWidth(PlainTextComponentSerializer.plainText().serialize(c));
        return this;
    }

    public Component build() { return b.build(); }

    public Component finish(int endPos) {
        moveTo(endPos);
        return b.build();
    }
}
