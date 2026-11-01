package pelt.skin;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

/** uuid -> SkinRecord, persisted as JSON. Thread-safe; save() writes atomically. */
public final class SkinStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type TYPE = new TypeToken<HashMap<String, SkinRecord>>() {}.getType();

    private final Path file;
    private final Map<UUID, SkinRecord> records = new ConcurrentHashMap<>();
    private final Object ioLock = new Object();

    public SkinStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** Loads from disk. A corrupt file is moved aside (not deleted) and an empty store is used. */
    public void load() throws IOException {
        synchronized (ioLock) {
            records.clear();
            if (!Files.exists(file)) return;
            Map<String, SkinRecord> raw;
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                raw = GSON.fromJson(r, TYPE);
            } catch (JsonParseException e) {
                Path bad = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
                Files.move(file, bad, StandardCopyOption.REPLACE_EXISTING);
                throw new IOException("skins.json was corrupt, moved to " + bad.getFileName(), e);
            }
            if (raw == null) return;
            for (Map.Entry<String, SkinRecord> e : raw.entrySet()) {
                try {
                    if (e.getValue() != null) records.put(UUID.fromString(e.getKey()), e.getValue());
                } catch (IllegalArgumentException ignored) {
                    // skip bad keys
                }
            }
        }
    }

    public void save() throws IOException {
        synchronized (ioLock) {
            Map<String, SkinRecord> out = new TreeMap<>();
            for (Map.Entry<UUID, SkinRecord> e : records.entrySet()) out.put(e.getKey().toString(), e.getValue());
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(out, TYPE, w);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public SkinRecord get(UUID id) {
        return records.get(id);
    }

    public void put(UUID id, SkinRecord record) {
        records.put(id, record);
    }

    public SkinRecord remove(UUID id) {
        return records.remove(id);
    }

    public int size() {
        return records.size();
    }
}
