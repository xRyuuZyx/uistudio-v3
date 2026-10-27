package id.roleplay.uistudio;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerConnectedEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;

/** Menghubungkan UIStudio ke plugin Simple Voice Chat. Hanya dimuat kalau plugin itu ada. */
public class VoiceHook implements VoicechatPlugin {
    @Override
    public String getPluginId() { return "uistudio"; }

    @Override
    public void initialize(VoicechatApi api) { }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, e -> {
            VoicechatConnection c = e.getSenderConnection();
            if (c != null && c.getPlayer() != null) Voice.spoke(c.getPlayer().getUuid());
        });
        registration.registerEvent(PlayerConnectedEvent.class, e -> {
            if (e.getConnection() != null && e.getConnection().getPlayer() != null) {
                Voice.setConnected(e.getConnection().getPlayer().getUuid(), true);
            }
        });
        registration.registerEvent(PlayerDisconnectedEvent.class, e -> Voice.setConnected(e.getPlayerUuid(), false));
    }
}
