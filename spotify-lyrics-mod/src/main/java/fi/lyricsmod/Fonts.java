package fi.lyricsmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class Fonts {
    public record F(String name, Identifier id) {}

    private static final String[][] BUILTIN = {
            {"Oletus", "minecraft:default"},
            {"Uniform", "minecraft:uniform"},
            {"Taikapöytä", "minecraft:alt"},
            {"Illager-riimut", "minecraft:illageralt"},
    };

    /** {tiedostonimi ilman .ttf/.json, näyttönimi} – pitää täsmätä download_fonts.py:n listaan. */
    private static final String[][] FILES = {
            {"pacifico", "Pacifico"}, {"lobster", "Lobster"}, {"bangers", "Bangers"},
            {"permanentmarker", "Permanent Marker"}, {"satisfy", "Satisfy"}, {"indieflower", "Indie Flower"},
            {"specialelite", "Special Elite"}, {"creepster", "Creepster"}, {"pressstart2p", "Press Start 2P"},
            {"righteous", "Righteous"}, {"chewy", "Chewy"}, {"kaushanscript", "Kaushan Script"},
            {"abrilfatface", "Abril Fatface"}, {"bebasneue", "Bebas Neue"}, {"staatliches", "Staatliches"},
            {"monoton", "Monoton"}, {"rocksalt", "Rock Salt"}, {"amaticsc", "Amatic SC"},
            {"greatvibes", "Great Vibes"}, {"sacramento", "Sacramento"}, {"russoone", "Russo One"},
            {"vt323", "VT323"}, {"silkscreen", "Silkscreen"}, {"lilitaone", "Lilita One"},
            {"luckiestguy", "Luckiest Guy"}, {"pirataone", "Pirata One"},
            {"mountainsofchristmas", "Mountains of Christmas"},
    };

    private static List<F> cache;

    public static List<F> list() {
        if (cache == null) {
            List<F> out = new ArrayList<>();
            for (String[] b : BUILTIN) out.add(new F(b[0], Identifier.of(b[1])));
            ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
            for (String[] f : FILES) {
                Identifier json = Identifier.of("lyricsmod", "font/" + f[0] + ".json");
                Identifier ttf = Identifier.of("lyricsmod", "font/" + f[0] + ".ttf");
                if (rm.getResource(json).isPresent() && rm.getResource(ttf).isPresent()) {
                    out.add(new F(f[1], Identifier.of("lyricsmod", f[0])));
                }
            }
            cache = out;
        }
        return cache;
    }

    private static F cur() {
        List<F> l = list();
        return l.get(Math.floorMod(LyricsConfig.I.fontIndex, l.size()));
    }

    public static Identifier current() { return cur().id(); }

    public static String currentName() {
        return cur().name() + " (" + (Math.floorMod(LyricsConfig.I.fontIndex, list().size()) + 1) + "/" + list().size() + ")";
    }
}
