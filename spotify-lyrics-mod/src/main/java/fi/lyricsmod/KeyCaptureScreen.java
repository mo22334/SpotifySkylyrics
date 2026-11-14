package fi.lyricsmod;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class KeyCaptureScreen extends Screen {
    private final Screen parent;

    public KeyCaptureScreen(Screen parent) {
        super(Text.literal("Valitse pikanäppäin"));
        this.parent = parent;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
            LyricsMod.openKey.setBoundKey(InputUtil.fromKeyCode(keyCode, scanCode));
            KeyBinding.updateKeysByCode();
            client.options.write();
        }
        LyricsConfig.I.keyAsked = true;
        LyricsConfig.save();
        client.setScreen(parent != null ? parent : new SettingsScreen());
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Spotify Lyrics"), width / 2, height / 2 - 30, 0x55FF55);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Paina näppäintä, jolla asetusvalikko aukeaa"), width / 2, height / 2 - 10, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("(ESC = käytä oletusta L)"), width / 2, height / 2 + 6, 0xAAAAAA);
        super.render(ctx, mx, my, delta);
    }
}
