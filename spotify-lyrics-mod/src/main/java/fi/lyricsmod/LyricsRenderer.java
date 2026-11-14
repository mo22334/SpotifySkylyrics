package fi.lyricsmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
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
        StyleSpriteSource font = new StyleSpriteSource.Font(Fonts.current());
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

    // ------------------------------------------------------------ HUD (edessä / taivaalla)

    public static void hud(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        LyricsConfig c = LyricsConfig.I;
        if (mc.options.hudHidden || mc.player == null) return;
        Cur cur = current();
        if (cur == null) return;
        Text[] t = build(cur);
        TextRenderer tr = mc.textRenderer;
        int fullW = tr.getWidth(t[0]);

        float w = ctx.getScaledWindowWidth();
        float h = ctx.getScaledWindowHeight();
        float cy;
        float s;
        if (c.sky) {
            // "Taivas": teksti on kiinnitetty 30 asteen korkeuteen horisontin yläpuolelle,
            // joten se liukuu ruudulla kun katsot ylös/alas.
            double fov = mc.options.getFov().getValue();
            float focal = (h / 2f) / (float) Math.tan(Math.toRadians(fov / 2.0));
            double ang = Math.max(-80, Math.min(80, 30 + mc.player.getPitch()));
            cy = h / 2f - (float) Math.tan(Math.toRadians(ang)) * focal;
            if (cy < -30 || cy > h + 30) return;
            s = 1.3f * c.scale;
        } else {
            cy = h / 2f + 40;
            s = 1.6f * c.scale;
        }

        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(w / 2f, cy);
        m.scale(s, s);
        ctx.drawText(tr, t[1], -fullW / 2, -4, 0xFFFFFFFF, true);
        m.popMatrix();
    }
}
