package pelt.skin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import pelt.source.MineSkinSource;

/**
 * Turns user input (a player name or an image URL) into a "textures" property.
 * Names go through the configured sources in order; URLs go to MineSkin.
 */
public final class SkinResolver {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final int MAX_URL = 512;

    private final List<SkinSource> nameSources;
    private final MineSkinSource mineSkin;
    private final boolean rehostNonMojang;

    /**
     * @param nameSources     ordered sources for player names
     * @param mineSkin        MineSkin client (may be unconfigured)
     * @param rehostNonMojang re-upload skins whose texture is not on textures.minecraft.net through MineSkin
     */
    public SkinResolver(List<SkinSource> nameSources, MineSkinSource mineSkin, boolean rehostNonMojang) {
        this.nameSources = new ArrayList<>(nameSources);
        this.mineSkin = mineSkin;
        this.rehostNonMojang = rehostNonMojang;
    }

    public static boolean isValidName(String s) {
        return s != null && NAME.matcher(s).matches();
    }

    public static boolean isUrl(String s) {
        if (s == null || s.length() > MAX_URL) return false;
        String l = s.toLowerCase(Locale.ROOT);
        return (l.startsWith("https://") || l.startsWith("http://")) && s.indexOf(' ') < 0 && s.length() > 10;
    }

    public SkinResult resolve(String input, boolean slimHint) {
        List<String> errors = new ArrayList<>();
        if (isUrl(input)) {
            if (mineSkin == null || !mineSkin.isConfigured()) {
                errors.add("url skins need mineskinApiKey to be set");
                return new SkinResult(null, null, errors);
            }
            try {
                return new SkinResult(mineSkin.generateFromUrl(input, slimHint), "mineskin", errors);
            } catch (IOException e) {
                errors.add(e.getMessage());
                return new SkinResult(null, null, errors);
            }
        }
        if (!isValidName(input)) {
            errors.add("not a valid player name or http(s) URL");
            return new SkinResult(null, null, errors);
        }
        for (SkinSource source : nameSources) {
            SkinData skin;
            try {
                skin = source.fetch(input);
            } catch (IOException | RuntimeException e) {
                errors.add(source.id() + ": " + e.getMessage());
                continue;
            }
            if (skin == null) continue;
            String url = skin.skinUrl();
            if (rehostNonMojang && url != null && !SkinData.isMojangHosted(url)
                    && mineSkin != null && mineSkin.isConfigured()) {
                try {
                    return new SkinResult(mineSkin.generateFromUrl(url, skin.isSlim()), source.id() + "+mineskin", errors);
                } catch (IOException e) {
                    // Fall back to the original (unsigned-for-Mojang) texture.
                    errors.add("mineskin rehost: " + e.getMessage());
                }
            }
            return new SkinResult(skin, source.id(), errors);
        }
        return new SkinResult(null, null, errors);
    }
}
