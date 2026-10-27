package id.roleplay.uistudio;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;

/** Dipanggil hanya kalau plugin 'voicechat' terpasang (supaya kelas API tidak dimuat tanpa itu). */
public final class VoiceBridge {
    private VoiceBridge() {}

    public static void register(UIStudio plugin) {
        BukkitVoicechatService service = plugin.getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service != null) {
            service.registerPlugin(new VoiceHook());
            Voice.hooked = true;
            plugin.getLogger().info("Simple Voice Chat terdeteksi: ikon mikrofon aktif.");
        }
    }
}
