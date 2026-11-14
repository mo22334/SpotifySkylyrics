package fi.lyricsmod;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public final class LyricsRenderer {
    private record Cur(Lyrics.Line line, Lyrics.Line next, long pos) {}

    private static Cur current() {
        LyricsConfig c = LyricsConfig.I;
        if (!c.enabled || !Spotify.playing || !Spotify.hasTrack()) return null;
        List<Lyrics.Line> ls = Lyrics.lines;
        if (ls.isEmpty()) return null;
        long pos = Spotify.position() + c.offsetMs;
        int idx = -1;
        for (int i = 0; i < ls.size(); i++) {
            if (ls.get(i).timeMs() <= pos) idx = i; else break;
        }
        if (idx < 0) return null;
        Lyrics.Line line = ls.get(idx);
        if (line.text().isBlank()) return null;
        return new Cur(line, idx + 1 < ls.size() ? ls.get(idx + 1) : null, pos);
    }

    // ------------------------------------------------------------ värit

    private static int color(int word, int ch, double t) {
        LyricsConfig c = LyricsConfig.I;
        if (!c.christmas) return c.color & 0xFFFFFF;
        boolean red = ((word + (c.lights ? (int) (t * 2) : 0)) & 1) == 0;
        int base = red ? 0xFF2D2D : 0xFFFFFF;
        if (!c.lights) return base;
        double f = 0.6 + 0.4 * Math.sin(t * 7 + ch * 0.8); // välkkyminen kirjain kerrallaan
        int r = (int) (((base >> 16) & 255) * f);
        int g = (int) (((base >> 8) & 255) * f);
        int b = (int) ((base & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    private static Text styled(String[] words, int n) {
        MutableText out = Text.empty();
        double t = (System.currentTimeMillis() % 1_000_000L) / 1000.0;
        Identifier font = Fonts.current();
        int ci = 0;
        for (int w = 0; w < n; w++) {
            String word = words[w] + (w < n - 1 ? " " : "");
            for (int i = 0; i < word.length(); ) {
                int cp = word.codePointAt(i);
                i += Character.charCount(cp);
                out.append(Text.literal(new String(Character.toChars(cp)))
                        .setStyle(Style.EMPTY.withColor(color(w, ci++, t)).withFont(font)));
            }
        }
        return out;
    }

    /** Palauttaa {koko rivi, näkyvä osa} – koko rivin leveys keskittää tekstin vaikka sanat ilmestyvät yksi kerrallaan. */
    private static Text[] build(Cur cur) {
        LyricsConfig c = LyricsConfig.I;
        String[] words = cur.line().text().trim().split("\\s+");
        int shown = words.length;
        if (c.wordReveal) {
            long end = cur.next() != null ? cur.next().timeMs() : cur.line().timeMs() + 5000;
            double span = Math.max(600, Math.min(end - cur.line().timeMs(), 7000) * 0.8);
            double p = (cur.pos() - cur.line().timeMs()) / span;
            shown = (int) Math.max(1, Math.min(words.length, Math.ceil(p * words.length)));
        }
        return new Text[]{styled(words, words.length), styled(words, shown)};
    }

    // ------------------------------------------------------------ HUD (edessä)

    public static void hud(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        LyricsConfig c = LyricsConfig.I;
        if (c.sky || mc.options.hudHidden || mc.player == null) return;
        Cur cur = current();
        if (cur == null) return;
        Text[] t = build(cur);
        TextRenderer tr = mc.textRenderer;
        int fullW = tr.getWidth(t[0]);
        float s = 1.6f * c.scale;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(ctx.getScaledWindowWidth() / 2f, ctx.getScaledWindowHeight() / 2f + 40, 0);
        ctx.getMatrices().scale(s, s, 1f);
        ctx.drawText(tr, t[1], -fullW / 2, 0, 0xFFFFFFFF, true);
        ctx.getMatrices().pop();
    }

    // ------------------------------------------------------------ taivas

    public static void sky(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        LyricsConfig c = LyricsConfig.I;
        if (!c.sky || mc.player == null) return;
        Cur cur = current();
        if (cur == null) return;
        Text[] t = build(cur);
        TextRenderer tr = mc.textRenderer;
        int fullW = tr.getWidth(t[0]);

        Camera cam = ctx.camera();
        MatrixStack m = ctx.matrixStack();
        double yaw = Math.toRadians(cam.getYaw());
        double dist = 50;
        m.push();
        m.translate(-Math.sin(yaw) * dist, 24, Math.cos(yaw) * dist); // kameran suhteen: eteen ja korkealle
        m.multiply(cam.getRotation());                                 // billboard
        float s = 0.25f * c.scale;
        m.scale(-s, -s, s);

        VertexConsumerProvider.Immediate vc = mc.getBufferBuilders().getEntityVertexConsumers();
        tr.draw(t[1], -fullW / 2f, 0, 0xFFFFFFFF, true, m.peek().getPositionMatrix(), vc,
                TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
        vc.draw();
        m.pop();
    }
}
