package fi.lyricsmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class LyricsMod implements ClientModInitializer {
    public static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        LyricsConfig.load();
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lyricsmod.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, "category.lyricsmod"));
        Spotify.start();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) return;
            // Ensimmäisellä käynnistyksellä kysytään pikanäppäin
            if (!LyricsConfig.I.keyAsked && mc.currentScreen == null) {
                mc.setScreen(new KeyCaptureScreen(null));
                return;
            }
            while (openKey.wasPressed()) {
                if (mc.currentScreen == null) mc.setScreen(new SettingsScreen());
            }
        });

        HudRenderCallback.EVENT.register(LyricsRenderer::hud);
        WorldRenderEvents.LAST.register(LyricsRenderer::sky);
    }
}
