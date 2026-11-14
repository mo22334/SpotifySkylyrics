package fi.lyricsmod;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SettingsScreen extends Screen {
    private static final Pattern HEX = Pattern.compile("^#?([0-9a-fA-F]{6})$");

    private final LyricsConfig c = LyricsConfig.I;
    private Slider sr, sg, sb;
    private TextFieldWidget hex, clientId;
    private boolean updating;
    private List<Fonts.F> fonts;
    private int rightX, clientLabelY;

    public SettingsScreen() {
        super(Text.literal("Spotify Lyrics – asetukset"));
    }

    private void toggle(int x, int y, int w, Supplier<String> label, Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label.get()), b -> {
            action.run();
            b.setMessage(Text.literal(label.get()));
        }).dimensions(x, y, w, 20).build());
    }

    @Override
    protected void init() {
        fonts = Fonts.list();
        int L = width / 2 - 155, R = width / 2 + 5, W = 150;
        rightX = R;
        int y = 24, dy = 22;

        // ---------- vasen sarake
        toggle(L, y, W, () -> "Sanoitukset: " + (c.enabled ? "PÄÄLLÄ" : "POIS"), () -> c.enabled = !c.enabled);
        y += dy;
        toggle(L, y, W, () -> "Sijainti: " + (c.sky ? "TAIVAS" : "EDESSÄ"), () -> c.sky = !c.sky);
        y += dy;
        toggle(L, y, W, () -> "Fontti: " + Fonts.currentName(),
                () -> c.fontIndex = Math.floorMod(c.fontIndex + 1, fonts.size()));
        y += dy;
        addDrawableChild(new Slider(L, y, W, 20, "Koko", 0.5, 3.0, c.scale, false, v -> c.scale = (float) v));
        y += dy;
        sr = new Slider(L, y, W, 20, "Punainen", 0, 255, (c.color >> 16) & 255, true, v -> setChannel(16, (int) v));
        addDrawableChild(sr);
        y += dy;
        sg = new Slider(L, y, W, 20, "Vihreä", 0, 255, (c.color >> 8) & 255, true, v -> setChannel(8, (int) v));
        addDrawableChild(sg);
        y += dy;
        sb = new Slider(L, y, W, 20, "Sininen", 0, 255, c.color & 255, true, v -> setChannel(0, (int) v));
        addDrawableChild(sb);
        y += dy;
        hex = new TextFieldWidget(textRenderer, L, y, W, 20, Text.literal("Hex"));
        hex.setMaxLength(7);
        hex.setText(String.format("#%06X", c.color & 0xFFFFFF));
        hex.setChangedListener(s -> {
            if (updating) return;
            Matcher m = HEX.matcher(s.trim());
            if (m.matches()) {
                c.color = Integer.parseInt(m.group(1), 16);
                sr.set((c.color >> 16) & 255);
                sg.set((c.color >> 8) & 255);
                sb.set(c.color & 255);
            }
        });
        addDrawableChild(hex);

        // ---------- oikea sarake
        y = 24;
        toggle(R, y, W, () -> "Joulutila: " + (c.christmas ? "PÄÄLLÄ" : "POIS"), () -> c.christmas = !c.christmas);
        y += dy;
        toggle(R, y, W, () -> "Välkkyvät valot: " + (c.lights ? "PÄÄLLÄ" : "POIS"), () -> c.lights = !c.lights);
        y += dy;
        toggle(R, y, W, () -> "Sana kerrallaan: " + (c.wordReveal ? "PÄÄLLÄ" : "POIS"), () -> c.wordReveal = !c.wordReveal);
        y += dy;

        // viiveen säätö (sanoitusten ajoitus)
        ButtonWidget offLabel = ButtonWidget.builder(Text.literal(c.offsetMs + " ms"), b -> {}).dimensions(R + 43, y, 64, 20).build();
        offLabel.active = false;
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> {
            c.offsetMs -= 250;
            offLabel.setMessage(Text.literal(c.offsetMs + " ms"));
        }).dimensions(R, y, 40, 20).build());
        addDrawableChild(offLabel);
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> {
            c.offsetMs += 250;
            offLabel.setMessage(Text.literal(c.offsetMs + " ms"));
        }).dimensions(R + 110, y, 40, 20).build());
        y += dy;

        clientLabelY = y + 4;
        y += 14;
        clientId = new TextFieldWidget(textRenderer, R, y, W, 20, Text.literal("Client ID"));
        clientId.setMaxLength(64);
        clientId.setText(c.clientId);
        clientId.setChangedListener(s -> c.clientId = s.trim());
        addDrawableChild(clientId);
        y += dy;
        addDrawableChild(ButtonWidget.builder(Text.literal("Yhdistä Spotify"), b -> {
            c.clientId = clientId.getText().trim();
            LyricsConfig.save();
            Spotify.connect();
        }).dimensions(R, y, W, 20).build());
        y += dy;
        addDrawableChild(ButtonWidget.builder(Text.literal("Vaihda pikanäppäin"),
                b -> client.setScreen(new KeyCaptureScreen(this))).dimensions(R, y, W, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Valmis"), b -> close())
                .dimensions(width / 2 - 100, Math.min(204, height - 36), 200, 20).build());
    }

    private void setChannel(int shift, int v) {
        c.color = (c.color & ~(255 << shift)) | (v << shift);
        if (hex != null) {
            updating = true;
            hex.setText(String.format("#%06X", c.color & 0xFFFFFF));
            updating = false;
        }
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "Spotify Client ID:", rightX, clientLabelY, 0xAAAAAA);
        super.render(ctx, mx, my, delta);
        String now = Spotify.hasTrack() ? Spotify.artist + " – " + Spotify.title : "–";
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(Spotify.status + "  |  " + now),
                width / 2, height - 12, 0xAAAAAA);
        if (!Lyrics.state.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(Lyrics.state), width / 2, height - 24, 0xFFAA55);
        }
    }

    @Override
    public void removed() {
        LyricsConfig.save();
    }

    // ------------------------------------------------------------ liukusäädin

    private static class Slider extends SliderWidget {
        private final double min, max;
        private final String name;
        private final boolean integer;
        private final DoubleConsumer onChange;

        Slider(int x, int y, int w, int h, String name, double min, double max, double cur, boolean integer, DoubleConsumer on) {
            super(x, y, w, h, Text.empty(), (cur - min) / (max - min));
            this.min = min;
            this.max = max;
            this.name = name;
            this.integer = integer;
            this.onChange = on;
            updateMessage();
        }

        private double val() {
            return min + value * (max - min);
        }

        void set(double v) {
            value = (v - min) / (max - min);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(name + ": " + (integer
                    ? String.valueOf((int) Math.round(val()))
                    : String.format(Locale.ROOT, "%.1f", val()))));
        }

        @Override
        protected void applyValue() {
            onChange.accept(integer ? Math.round(val()) : val());
        }
    }
}
