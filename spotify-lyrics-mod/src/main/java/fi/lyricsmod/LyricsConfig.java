package fi.lyricsmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class LyricsConfig {
    public static LyricsConfig I = new LyricsConfig();

    public boolean enabled = true;
    public boolean sky = false;          // false = edessä, true = taivaalla
    public int color = 0xFFFFFF;
    public int fontIndex = 0;
    public float scale = 1.0f;
    public boolean christmas = false;
    public boolean lights = true;
    public boolean wordReveal = true;
    public int offsetMs = 0;
    public boolean keyAsked = false;
    public String clientId = "";
    public String refreshToken = "";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("spotify_lyrics.json");
    }

    public static void load() {
        try {
            if (Files.exists(path())) {
                LyricsConfig c = GSON.fromJson(Files.readString(path()), LyricsConfig.class);
                if (c != null) I = c;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized void save() {
        try {
            Files.writeString(path(), GSON.toJson(I));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
