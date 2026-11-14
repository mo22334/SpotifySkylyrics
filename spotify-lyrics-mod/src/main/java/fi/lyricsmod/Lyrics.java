package fi.lyricsmod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Hakee aikaleimatut sanoitukset LRCLIB-palvelusta (lrclib.net, ilmainen, ei avainta). */
public final class Lyrics {
    public record Line(long timeMs, String text) {}

    public static volatile List<Line> lines = List.of();
    public static volatile String state = "";
    private static volatile String loadingFor = "";

    private static final Pattern LRC = Pattern.compile("^\\[(\\d+):(\\d+)(?:[.:](\\d+))?\\]\\s*(.*)$");

    public static void load(String title, String artist, long durationMs) {
        String key = artist + "|" + title;
        loadingFor = key;
        lines = List.of();
        state = "Haetaan sanoituksia...";
        CompletableFuture.runAsync(() -> {
            try {
                String lrc = fetch(title, artist, durationMs);
                if (!key.equals(loadingFor)) return;
                if (lrc == null) {
                    state = "Sanoituksia ei löytynyt";
                    lines = List.of();
                } else {
                    lines = parse(lrc);
                    state = lines.isEmpty() ? "Sanoituksia ei löytynyt" : "";
                }
            } catch (Exception e) {
                state = "Sanoitushaku epäonnistui";
                e.printStackTrace();
            }
        });
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String fetch(String title, String artist, long durationMs) throws Exception {
        String url = "https://lrclib.net/api/get?track_name=" + enc(title) + "&artist_name=" + enc(artist)
                + "&duration=" + Math.round(durationMs / 1000.0);
        Http.Res r = Http.send("GET", url, Map.of(), null);
        if (r.code() == 200) {
            JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
            JsonElement s = o.get("syncedLyrics");
            if (s != null && !s.isJsonNull()) return s.getAsString();
        }
        // Varahaku: siivotaan nimestä esim. "- Remastered 2011" ja "(feat. ...)"
        String clean = title.replaceAll("\\s*[-(\\[].*$", "").trim();
        Http.Res s = Http.send("GET", "https://lrclib.net/api/search?q=" + enc(artist + " " + clean), Map.of(), null);
        if (s.code() == 200) {
            JsonArray arr = JsonParser.parseString(s.body()).getAsJsonArray();
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                JsonElement sl = o.get("syncedLyrics");
                if (sl != null && !sl.isJsonNull() && !sl.getAsString().isBlank()) return sl.getAsString();
            }
        }
        return null;
    }

    static List<Line> parse(String lrc) {
        List<Line> out = new ArrayList<>();
        for (String raw : lrc.split("\\r?\\n")) {
            Matcher m = LRC.matcher(raw.trim());
            if (!m.matches()) continue;
            long min = Long.parseLong(m.group(1));
            long sec = Long.parseLong(m.group(2));
            long ms = 0;
            String frac = m.group(3);
            if (frac != null) {
                ms = Long.parseLong(frac);
                if (frac.length() == 1) ms *= 100;
                else if (frac.length() == 2) ms *= 10;
            }
            out.add(new Line(min * 60000 + sec * 1000 + ms, m.group(4).trim()));
        }
        out.sort(Comparator.comparingLong(Line::timeMs));
        return Collections.unmodifiableList(out);
    }
}
