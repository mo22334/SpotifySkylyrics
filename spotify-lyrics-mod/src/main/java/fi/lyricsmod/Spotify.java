package fi.lyricsmod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Spotify Web API (PKCE-kirjautuminen) + "currently playing" -pollaus. */
public final class Spotify {
    static final String REDIRECT = "http://127.0.0.1:8888/callback";

    public static volatile String status = "Ei yhdistetty";
    public static volatile boolean playing;
    public static volatile String trackId = "", title = "", artist = "";
    public static volatile long durationMs, progressMs, sampleAt;

    private static volatile String accessToken;
    private static volatile long tokenExpiry, pauseUntil;

    public static void start() {
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lyricsmod-spotify");
            t.setDaemon(true);
            return t;
        });
        ex.scheduleWithFixedDelay(() -> {
            try { poll(); } catch (Throwable t) { status = "Virhe: " + t.getMessage(); }
        }, 2, 2, TimeUnit.SECONDS);
    }

    /** Nykyinen toistokohta millisekunteina (interpoloitu pollausten välillä). */
    public static long position() {
        long p = progressMs;
        if (playing) p += System.currentTimeMillis() - sampleAt;
        return durationMs > 0 ? Math.min(p, durationMs) : p;
    }

    public static boolean hasTrack() {
        return !trackId.isEmpty();
    }

    // ---------------------------------------------------------------- pollaus

    private static void poll() throws Exception {
        LyricsConfig c = LyricsConfig.I;
        if (c.clientId.isBlank() || c.refreshToken.isBlank()) return;
        if (System.currentTimeMillis() < pauseUntil) return;
        if (accessToken == null || System.currentTimeMillis() > tokenExpiry - 30_000) refresh();
        if (accessToken == null) return;

        long t0 = System.currentTimeMillis();
        Http.Res r = Http.send("GET", "https://api.spotify.com/v1/me/player/currently-playing",
                Map.of("Authorization", "Bearer " + accessToken), null);
        long t1 = System.currentTimeMillis();

        switch (r.code()) {
            case 204 -> {
                playing = false;
                status = "Yhdistetty – mikään ei soi";
            }
            case 200 -> parse(r.body(), (t0 + t1) / 2);
            case 401 -> accessToken = null;
            case 429 -> {
                long wait = 5;
                try { if (r.retryAfter() != null) wait = Long.parseLong(r.retryAfter().trim()); } catch (Exception ignored) {}
                pauseUntil = System.currentTimeMillis() + wait * 1000;
                status = "Spotify rajoitti pyyntöjä, odotetaan " + wait + " s";
            }
            default -> status = "Spotify-virhe " + r.code();
        }
    }

    private static void parse(String body, long sampleTime) {
        JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        JsonElement itemEl = o.get("item");
        if (itemEl == null || itemEl.isJsonNull()) {
            playing = false;
            status = "Yhdistetty – ei kappaletta (mainos?)";
            return;
        }
        JsonObject it = itemEl.getAsJsonObject();
        String name = it.get("name").getAsString();
        String art = it.getAsJsonArray("artists").size() > 0
                ? it.getAsJsonArray("artists").get(0).getAsJsonObject().get("name").getAsString() : "";
        JsonElement idEl = it.get("id");
        String id = (idEl != null && !idEl.isJsonNull()) ? idEl.getAsString() : art + "|" + name;
        long dur = it.get("duration_ms").getAsLong();
        boolean isPlaying = o.get("is_playing").getAsBoolean();
        long prog = o.get("progress_ms").getAsLong();

        boolean changed = !id.equals(trackId);
        title = name;
        artist = art;
        durationMs = dur;
        progressMs = prog;
        sampleAt = sampleTime;
        playing = isPlaying;
        status = "Yhdistetty";
        if (changed) {
            trackId = id;
            Lyrics.load(name, art, dur);
        }
    }

    // ---------------------------------------------------------- kirjautuminen

    public static void connect() {
        String cid = LyricsConfig.I.clientId.trim();
        if (cid.isEmpty()) {
            status = "Syötä ensin Client ID";
            return;
        }
        Thread t = new Thread(() -> {
            try { doAuth(cid); }
            catch (Exception e) { status = "Yhdistys epäonnistui: " + e.getMessage(); }
        }, "lyricsmod-auth");
        t.setDaemon(true);
        t.start();
    }

    private static void doAuth(String cid) throws Exception {
        SecureRandom rnd = new SecureRandom();
        byte[] vb = new byte[64];
        rnd.nextBytes(vb);
        String verifier = b64(vb);
        String challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        byte[] sb = new byte[16];
        rnd.nextBytes(sb);
        String state = b64(sb);

        String url = "https://accounts.spotify.com/authorize?response_type=code"
                + "&client_id=" + enc(cid)
                + "&scope=" + enc("user-read-currently-playing user-read-playback-state")
                + "&redirect_uri=" + enc(REDIRECT)
                + "&state=" + state
                + "&code_challenge_method=S256&code_challenge=" + challenge;

        status = "Odotetaan kirjautumista selaimessa...";
        String code = null;
        try (ServerSocket ss = new ServerSocket(8888, 5, InetAddress.getByName("127.0.0.1"))) {
            ss.setSoTimeout(180_000);
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> mc.keyboard.setClipboard(url)); // varalta: linkki leikepöydälle
            Util.getOperatingSystem().open(URI.create(url));

            long deadline = System.currentTimeMillis() + 180_000;
            while (code == null && System.currentTimeMillis() < deadline) {
                try (Socket s = ss.accept()) {
                    s.setSoTimeout(5000);
                    BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    String first = r.readLine();
                    if (first == null || !first.startsWith("GET /callback")) {
                        reply(s, 404, "Not found");
                        continue;
                    }
                    Map<String, String> q = query(first.split(" ")[1]);
                    reply(s, 200, "<html><body style='font-family:sans-serif;text-align:center;margin-top:20vh'>"
                            + "<h2>Valmis! Voit sulkea tämän välilehden ja palata Minecraftiin.</h2></body></html>");
                    if (!state.equals(q.get("state"))) throw new IOException("state ei täsmää");
                    if (q.containsKey("error")) throw new IOException(q.get("error"));
                    code = q.get("code");
                } catch (SocketTimeoutException ignored) {
                }
            }
        }
        if (code == null) throw new IOException("aikakatkaisu");

        Map<String, String> f = new LinkedHashMap<>();
        f.put("grant_type", "authorization_code");
        f.put("code", code);
        f.put("redirect_uri", REDIRECT);
        f.put("client_id", cid);
        f.put("code_verifier", verifier);
        tokenRequest(f);
        status = "Yhdistetty!";
    }

    private static void refresh() {
        try {
            Map<String, String> f = new LinkedHashMap<>();
            f.put("grant_type", "refresh_token");
            f.put("refresh_token", LyricsConfig.I.refreshToken);
            f.put("client_id", LyricsConfig.I.clientId.trim());
            tokenRequest(f);
        } catch (Exception e) {
            accessToken = null;
            status = "Token-virhe, yhdistä uudelleen: " + e.getMessage();
            if (String.valueOf(e.getMessage()).contains("invalid_grant")) {
                LyricsConfig.I.refreshToken = "";
                LyricsConfig.save();
            }
            pauseUntil = System.currentTimeMillis() + 10_000;
        }
    }

    private static void tokenRequest(Map<String, String> form) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        Http.Res r = Http.send("POST", "https://accounts.spotify.com/api/token",
                Map.of("Content-Type", "application/x-www-form-urlencoded"), sb.toString());
        if (r.code() != 200) throw new IOException("HTTP " + r.code() + " " + r.body());
        JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
        accessToken = o.get("access_token").getAsString();
        tokenExpiry = System.currentTimeMillis() + o.get("expires_in").getAsLong() * 1000;
        if (o.has("refresh_token") && !o.get("refresh_token").isJsonNull()) {
            LyricsConfig.I.refreshToken = o.get("refresh_token").getAsString();
            LyricsConfig.save();
        }
    }

    // ------------------------------------------------------------ apufunktiot

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static Map<String, String> query(String target) {
        Map<String, String> m = new HashMap<>();
        int i = target.indexOf('?');
        if (i < 0) return m;
        for (String part : target.substring(i + 1).split("&")) {
            int e = part.indexOf('=');
            if (e < 0) continue;
            m.put(URLDecoder.decode(part.substring(0, e), StandardCharsets.UTF_8),
                    URLDecoder.decode(part.substring(e + 1), StandardCharsets.UTF_8));
        }
        return m;
    }

    private static void reply(Socket s, int code, String html) throws IOException {
        byte[] b = html.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : " Not Found") + "\r\n"
                + "Content-Type: text/html; charset=utf-8\r\nConnection: close\r\nContent-Length: " + b.length + "\r\n\r\n";
        OutputStream o = s.getOutputStream();
        o.write(head.getBytes(StandardCharsets.UTF_8));
        o.write(b);
        o.flush();
    }
}
