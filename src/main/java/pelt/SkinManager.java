package pelt;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import pelt.skin.HttpFetcher;
import pelt.skin.SkinRecord;
import pelt.skin.SkinResolver;
import pelt.skin.SkinResult;
import pelt.skin.SkinSource;
import pelt.skin.SkinStore;
import pelt.skin.UrlConnectionFetcher;
import pelt.source.ElySource;
import pelt.source.MineSkinSource;
import pelt.source.MojangSource;

/**
 * Runtime glue. All network I/O runs on a background worker; every change to
 * players happens on the server thread via addScheduledTask.
 */
public final class SkinManager {

    private static volatile SkinManager instance;

    private final MinecraftServer server;
    private final File configFile;
    private final SkinStore store;
    private final ExecutorService worker;
    private final Set<UUID> inFlight = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, Long> lastSet = new ConcurrentHashMap<>();

    private volatile PeltConfig config;
    private volatile SkinResolver resolver;

    private SkinManager(MinecraftServer server, File configFile, File storeFile) {
        this.server = server;
        this.configFile = configFile;
        this.store = new SkinStore(storeFile.toPath());
        AtomicInteger n = new AtomicInteger();
        this.worker = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "pelt-fetch-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        reloadConfig();
        try {
            store.load();
            Pelt.LOGGER.info("Loaded {} cached skin(s) from {}", store.size(), storeFile);
        } catch (IOException e) {
            Pelt.LOGGER.error("Could not load {}: {}", storeFile, e.getMessage());
        }
    }

    public static SkinManager get() {
        return instance;
    }

    static void start(MinecraftServer server, File configFile, File storeFile) {
        instance = new SkinManager(server, configFile, storeFile);
    }

    static void stop() {
        SkinManager m = instance;
        instance = null;
        if (m == null) return;
        m.worker.shutdownNow();
        try {
            m.store.save();
        } catch (IOException e) {
            Pelt.LOGGER.error("Could not save skins on shutdown", e);
        }
    }

    public PeltConfig config() {
        return config;
    }

    void reloadConfig() {
        PeltConfig c = PeltConfig.load(configFile);
        HttpFetcher http = new UrlConnectionFetcher(c.httpTimeoutMs, "pelt/" + Tags.VERSION + " (Minecraft 1.12.2 server mod)");
        List<SkinSource> sources = new ArrayList<>();
        for (String id : c.sources) {
            if ("mojang".equals(id)) sources.add(new MojangSource(http));
            else if ("ely".equals(id)) sources.add(new ElySource(http));
            else Pelt.LOGGER.warn("Unknown skin source '{}' in config (known: mojang, ely)", id);
        }
        MineSkinSource mineSkin = new MineSkinSource(http, c.mineskinApiKey);
        this.config = c;
        this.resolver = new SkinResolver(sources, mineSkin, c.rehostNonMojangSkins);
    }

    // ---- join flow -------------------------------------------------------

    /** Server thread, before the join packets are sent. Cache only. */
    void applyCached(EntityPlayerMP player) {
        SkinRecord rec = store.get(player.getUniqueID());
        if (rec != null && rec.hasSkin()) {
            ProfileRefresher.setTextures(player.getGameProfile(), rec.data());
            debug("Applied cached {} skin to {}", rec.source, player.getName());
        }
    }

    /** Server thread, after the player joined. May start a background fetch. */
    void onLoggedIn(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        SkinRecord rec = store.get(id);
        PeltConfig c = config;
        long now = System.currentTimeMillis();
        if (rec == null) {
            if (!c.useUsernameByDefault) return;
            // online-mode servers (or other mods) may already have provided a skin
            if (player.getGameProfile().getProperties().containsKey("textures")) return;
            fetch(id, player.getName(), false, null, false);
        } else if (rec.isStale(now, c.cacheTtlHours * 3600_000L, c.missTtlMinutes * 60_000L)) {
            if (!rec.explicit && !c.useUsernameByDefault) return;
            fetch(id, rec.explicit ? rec.input : player.getName(), rec.explicit, null, false);
        }
    }

    // ---- commands --------------------------------------------------------

    /** @return seconds left on the caller's cooldown, or 0 if they may run /skin set now. */
    public long cooldownLeft(UUID caller) {
        Long last = lastSet.get(caller);
        if (last == null) return 0;
        long left = last + config.setCooldownSeconds * 1000L - System.currentTimeMillis();
        return left > 0 ? (left + 999) / 1000 : 0;
    }

    public void markCooldown(UUID caller) {
        lastSet.put(caller, System.currentTimeMillis());
    }

    public void set(UUID target, String targetName, String input, ICommandSender feedback) {
        msg(feedback, TextFormatting.GRAY, "Looking up skin '" + input + "' for " + targetName + "...");
        fetch(target, input, true, feedback, true);
    }

    public void clear(EntityPlayerMP target, ICommandSender feedback) {
        store.remove(target.getUniqueID());
        saveAsync();
        ProfileRefresher.setTextures(target.getGameProfile(), null);
        ProfileRefresher.refresh(server, target);
        msg(feedback, TextFormatting.GREEN, "Cleared custom skin for " + target.getName() + ".");
        if (config.useUsernameByDefault) fetch(target.getUniqueID(), target.getName(), false, null, true);
    }

    /** Re-reads the config and re-fetches skins for the given online players (ignoring cache age). */
    public void reload(List<EntityPlayerMP> targets, ICommandSender feedback) {
        reloadConfig();
        for (EntityPlayerMP p : targets) {
            SkinRecord rec = store.get(p.getUniqueID());
            if (rec != null && rec.explicit) fetch(p.getUniqueID(), rec.input, true, null, true);
            else if (config.useUsernameByDefault) fetch(p.getUniqueID(), p.getName(), false, null, true);
        }
        Pelt.LOGGER.info("Reload complete: config re-read ({}), refreshing {} player(s)",
                String.join(", ", config.describe()), targets.size());
        msg(feedback, TextFormatting.GREEN, "Pelt config reloaded; refreshing " + targets.size() + " player(s).");
    }

    // ---- fetching --------------------------------------------------------

    private void fetch(UUID target, String input, boolean explicit, ICommandSender feedback, boolean force) {
        if (!inFlight.add(target)) {
            if (!force) return;
            msg(feedback, TextFormatting.YELLOW, "A skin lookup for that player is already running, try again shortly.");
            return;
        }
        final SkinResolver r = resolver;
        try {
            worker.execute(() -> {
                SkinResult result;
                try {
                    result = r.resolve(input, false);
                } catch (Throwable t) {
                    Pelt.LOGGER.warn("Skin lookup for '{}' crashed", input, t);
                    inFlight.remove(target);
                    return;
                }
                server.addScheduledTask(() -> {
                    inFlight.remove(target);
                    complete(target, input, explicit, feedback, result);
                });
            });
        } catch (RuntimeException e) {
            inFlight.remove(target);
            Pelt.LOGGER.warn("Could not schedule skin lookup: {}", e.toString());
        }
    }

    /** Server thread. */
    private void complete(UUID target, String input, boolean explicit, ICommandSender feedback, SkinResult result) {
        if (!result.errors.isEmpty()) {
            Pelt.LOGGER.warn("Skin lookup for '{}': {}", input, String.join("; ", result.errors));
        }
        long now = System.currentTimeMillis();
        if (!result.found()) {
            if (explicit) {
                // keep whatever they had; just report
                msg(feedback, TextFormatting.RED, "No skin found for '" + input + "'"
                        + (result.errors.isEmpty() ? "." : ": " + String.join("; ", result.errors)));
                return;
            }
            if (!result.failed()) {
                // a genuine miss: remember it so we don't ask on every join
                SkinRecord old = store.get(target);
                if (old == null || !old.explicit) {
                    store.put(target, new SkinRecord(input, null, null, now, false));
                    saveAsync();
                }
            }
            return;
        }

        SkinRecord old = store.get(target);
        if (!explicit && old != null && old.explicit) return; // a /skin set raced a default fetch
        store.put(target, new SkinRecord(input, result.source, result.skin, now, explicit));
        saveAsync();

        EntityPlayerMP online = server.getPlayerList().getPlayerByUUID(target);
        if (online != null) {
            boolean changed = old == null || !result.skin.value.equals(old.value);
            ProfileRefresher.setTextures(online.getGameProfile(), result.skin);
            if (changed) ProfileRefresher.refresh(server, online);
        }
        debug("Resolved '{}' via {}", input, result.source);
        msg(feedback, TextFormatting.GREEN, "Skin set from " + result.source + " ('" + input + "'). "
                + "Others see it now; you will see it yourself after you rejoin.");
    }

    private void saveAsync() {
        try {
            worker.execute(() -> {
                try {
                    store.save();
                } catch (IOException e) {
                    Pelt.LOGGER.error("Could not save {}: {}", store.file(), e.getMessage());
                }
            });
        } catch (RuntimeException ignored) {
            // shutting down; stop() saves synchronously
        }
    }

    private static void msg(ICommandSender to, TextFormatting color, String text) {
        if (to == null) return;
        TextComponentString c = new TextComponentString(text);
        c.getStyle().setColor(color);
        to.sendMessage(c);
    }

    private void debug(String fmt, Object... args) {
        if (config.debug) Pelt.LOGGER.info(fmt, args);
    }
}
