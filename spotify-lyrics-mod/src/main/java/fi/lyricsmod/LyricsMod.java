package fi.lyricsmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class LyricsMod implements ClientModInitializer {
    public static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        LyricsConfig.load();
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("lyricsmod", "main"));
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lyricsmod.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, category));
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

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.of("lyricsmod", "lyrics"), LyricsRenderer::hud);
    }
}
