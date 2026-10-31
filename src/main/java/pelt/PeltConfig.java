package pelt;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraftforge.common.config.Configuration;

/** config/pelt.cfg. Re-read from disk on every /skin reload. */
public final class PeltConfig {

    private static final String CAT = Configuration.CATEGORY_GENERAL;

    public final List<String> sources;
    public final int cacheTtlHours;
    public final int missTtlMinutes;
    public final int httpTimeoutMs;
    public final String mineskinApiKey;
    public final boolean useUsernameByDefault;
    public final int setCooldownSeconds;
    public final boolean allowUrlSkins;
    public final boolean rehostNonMojangSkins;
    public final boolean debug;

    private PeltConfig(Configuration c) {
        String[] src = c.getStringList("sources", CAT, new String[] {"mojang", "ely"},
                "Ordered skin sources tried for player names. Known: mojang, ely");
        List<String> s = new ArrayList<>();
        for (String x : src) {
            String id = x.trim().toLowerCase(Locale.ROOT);
            if (!id.isEmpty() && !s.contains(id)) s.add(id);
        }
        sources = Collections.unmodifiableList(s);
        cacheTtlHours = c.getInt("cacheTtlHours", CAT, 24, 1, 24 * 365,
                "Re-fetch name-based skins older than this (hours) when the player joins. URL skins never expire.");
        missTtlMinutes = c.getInt("missTtlMinutes", CAT, 60, 1, 60 * 24 * 30,
                "When no source has a skin for a name, wait this long (minutes) before asking again.");
        httpTimeoutMs = c.getInt("httpTimeoutMs", CAT, 5000, 500, 60000,
                "Connect and read timeout per HTTP request (ms).");
        mineskinApiKey = c.getString("mineskinApiKey", CAT, "",
                "MineSkin API key (https://account.mineskin.org). Needed for URL skins and for re-hosting Ely.by skins.");
        useUsernameByDefault = c.getBoolean("useUsernameByDefault", CAT, true,
                "Players without a /skin set get the skin of the account with their username.");
        setCooldownSeconds = c.getInt("setCooldownSeconds", CAT, 30, 0, 86400,
                "Cooldown between /skin set uses per player (seconds). Ops with pelt.skin.other bypass it.");
        allowUrlSkins = c.getBoolean("allowUrlSkins", CAT, true,
                "Allow /skin set <http(s) url> (uploaded through MineSkin).");
        rehostNonMojangSkins = c.getBoolean("rehostNonMojangSkins", CAT, true,
                "Re-upload skins not hosted on textures.minecraft.net (e.g. Ely.by-only skins) through MineSkin so "
                        + "vanilla clients show them. Only used when mineskinApiKey is set.");
        debug = c.getBoolean("debug", CAT, false, "Verbose logging.");
    }

    public static PeltConfig load(File file) {
        Configuration c = new Configuration(file);
        c.load();
        PeltConfig cfg = new PeltConfig(c);
        if (c.hasChanged()) c.save();
        return cfg;
    }

    public List<String> describe() {
        return Arrays.asList(
                "sources=" + sources,
                "cacheTtlHours=" + cacheTtlHours,
                "mineskin=" + (mineskinApiKey.isEmpty() ? "off" : "on"),
                "useUsernameByDefault=" + useUsernameByDefault);
    }
}
