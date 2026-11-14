package com.example.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpotifySkylyricsClient implements ClientModInitializer {
    private int tickCounter = 0;
    private boolean keyWasDown = false;
    private int joinDelayTicks = -1;

    @Override
    public void onInitializeClient() {
        // 1. Keybind-GUI ponnahtaa ruudulle heti multiplayer-serverille liittyessä
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!Config.keybindChosen) {
                joinDelayTicks = 20;
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (joinDelayTicks > 0) {
                joinDelayTicks--;
                if (joinDelayTicks == 0 && client.currentScreen == null) {
                    client.setScreen(new KeybindGui());
                }
            }

            // 2. Valitun näppäimen kuuntelu asetusvalikolle
            if (client.player != null && client.currentScreen == null) {
                boolean isDown = InputUtil.isKeyPressed(client.getWindow().getHandle(), Config.customKeyCode);
                if (isDown && !keyWasDown) {
                    client.setScreen(new SettingsGui());
                }
                keyWasDown = isDown;
            }

            // 3. Spotify-biisin tarkistus ja synkkaus 0.5 sekunnin välein
            if (Config.enabled && client.world != null) {
                tickCounter++;
                if (tickCounter >= 10) {
                    tickCounter = 0;
                    Spotify.updateSong();
                }
            }
        });

        // 4. Sanojen piirto TAIVAALLE (3D-maailmaan)
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (!Config.enabled || !Config.inSkyMode) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) return;

            String currentLine = Spotify.getCurrentLine();
            if (currentLine == null || currentLine.isEmpty()) return;

            MatrixStack matrices = context.matrixStack();
            Camera camera = context.camera();
            Vec3d camPos = camera.getPos();

            matrices.push();

            Vec3d lookVec = client.player.getRotationVec(1.0F);
            double targetX = client.player.getX() + lookVec.x * 25.0;
            double targetY = client.player.getY() + 12.0;
            double targetZ = client.player.getZ() + lookVec.z * 25.0;

            matrices.translate(targetX - camPos.x, targetY - camPos.y, targetZ - camPos.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

            float scale = 0.16f;
            matrices.scale(-scale, -scale, scale);

            Text formattedText = formatText(currentLine);
            TextRenderer textRenderer = client.textRenderer;
            float width = textRenderer.getWidth(formattedText);

            Matrix4f matrix = matrices.peek().getPositionMatrix();
            VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();

            textRenderer.draw(
                    formattedText,
                    -width / 2.0f,
                    0,
                    getActiveColor(),
                    true,
                    matrix,
                    immediate,
                    TextRenderer.TextLayerType.NORMAL,
                    0,
                    15728880
            );

            immediate.draw();
            matrices.pop();
        });

        // 5. Sanojen piirto NAAMAN ETEEN (Ruudulle HUDina)
        HudRenderCallback.EVENT.register((drawContext, renderTickCounter) -> {
            if (!Config.enabled || Config.inSkyMode) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            String currentLine = Spotify.getCurrentLine();
            if (currentLine == null || currentLine.isEmpty()) return;

            Text formattedText = formatText(currentLine);
            TextRenderer textRenderer = client.textRenderer;

            int screenWidth = client.getWindow().getScaledWidth();
            int screenHeight = client.getWindow().getScaledHeight();
            int textWidth = textRenderer.getWidth(formattedText);

            int x = (screenWidth - textWidth) / 2;
            int y = (screenHeight / 2) - 35;

            drawContext.drawTextWithShadow(textRenderer, formattedText, x, y, getActiveColor());
        });
    }

    public static int getActiveColor() {
        if (Config.christmasMode) {
            long time = System.currentTimeMillis() / 400;
            if (Config.christmasGlow) {
                return (time % 2 == 0) ? 0xFFFF2222 : 0xFFFFFFFF;
            }
            return 0xFFFF3333;
        }
        return Config.COLOR_VALUES[Config.colorIndex % Config.COLOR_VALUES.length];
    }

    public static Text formatText(String rawText) {
        MutableText text = Text.literal(rawText);
        Style style = Style.EMPTY;
        int fontId = Config.fontIndex;

        if (fontId % 2 == 1) style = style.withBold(true);
        if (fontId % 3 == 0) style = style.withItalic(true);
        if (fontId % 4 == 0) style = style.withUnderline(true);
        if (fontId >= 10 && fontId < 15) {
            text = Text.literal(rawText.toUpperCase());
        } else if (fontId >= 15) {
            text = Text.literal("★ " + rawText + " ★");
        }

        return text.setStyle(style);
    }

    // --- ASETUKSET, 20 FONTTIA JA VÄRIT ---
    public static class Config {
        public static boolean enabled = true;
        public static boolean inSkyMode = true;
        public static long syncOffsetMs = 0;
        public static int colorIndex = 0;
        public static int fontIndex = 0;
        public static boolean christmasMode = false;
        public static boolean christmasGlow = true;
        public static int customKeyCode = GLFW.GLFW_KEY_M;
        public static boolean keybindChosen = false;

        public static final String[] COLOR_NAMES = {
                "Valkoinen", "Punainen", "Sininen", "Vihreä", "Keltainen",
                "Violetti", "Syaani", "Oranssi", "Pinkki", "Kulta",
                "Limenvihreä", "Turkoosi", "Magenta", "Hopea", "Taivaansininen"
        };

        public static final int[] COLOR_VALUES = {
                0xFFFFFFFF, 0xFFFF3333, 0xFF3388FF, 0xFF33FF55, 0xFFFFFF33,
                0xFFAA33FF, 0xFF33FFFF, 0xFFFF8800, 0xFFFF66CC, 0xFFFFD700,
                0xFF88FF00, 0xFF00CED1, 0xFFFF00FF, 0xFFC0C0C0, 0xFF87CEEB
        };

        public static final String[] FONT_NAMES = {
                "1. Normaali", "2. Lihavoitu", "3. Kursivoitu", "4. Alleviivattu", "5. Lihavoitu + Kursiivi",
                "6. Lihavoitu + Alleviivattu", "7. Kursiivi + Alleviivattu", "8. Kaikki tyylit", "9. Ohut", "10. Klassinen",
                "11. ISOT KIRJAIMET", "12. ISOT + Lihavoitu", "13. ISOT + Kursiivi", "14. ISOT + Alleviivattu", "15. ISOT + Korostus",
                "16. Tähtikehys", "17. Tähtikehys + Lihavoitu", "18. Tähtikehys + Kursiivi", "19. Tähtikehys + Alleviivattu", "20. Juhlatyyli"
        };
    }

    // --- MULTIPLAYER-LIITTYMISEN KEYBIND-GUI ---
    public static class KeybindGui extends Screen {
        public KeybindGui() {
            super(Text.literal("Spotify Sky Lyrics - Valitse Keybind"));
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int centerY = this.height / 2;

            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("Käytä oletusta (M-näppäin)"),
                    button -> {
                        Config.customKeyCode = GLFW.GLFW_KEY_M;
                        Config.keybindChosen = true;
                        this.close();
                    }).dimensions(centerX - 100, centerY + 30, 200, 20).build());
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                Config.customKeyCode = keyCode;
                Config.keybindChosen = true;
                this.close();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
