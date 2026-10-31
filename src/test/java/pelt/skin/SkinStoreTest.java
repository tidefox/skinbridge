package pelt.skin;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkinStoreTest {

    @TempDir
    Path dir;

    @Test
    void roundTrip() throws IOException {
        Path f = dir.resolve("pelt/skins.json");
        SkinStore s = new SkinStore(f);
        s.load(); // missing file is fine
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        s.put(a, new SkinRecord("Notch", "mojang", new SkinData("VAL", "SIG"), 1000L, true));
        s.put(b, new SkinRecord("ghost", null, null, 2000L, false));
        s.save();
        assertFalse(Files.exists(dir.resolve("pelt/skins.json.tmp")));

        SkinStore t = new SkinStore(f);
        t.load();
        assertEquals(2, t.size());
        SkinRecord ra = t.get(a);
        assertEquals("Notch", ra.input);
        assertEquals("VAL", ra.data().value);
        assertEquals("SIG", ra.data().signature);
        assertTrue(ra.explicit);
        assertFalse(t.get(b).hasSkin());
        assertNull(t.get(b).data());
    }

    @Test
    void corruptFileIsMovedAside() throws IOException {
        Path f = dir.resolve("skins.json");
        Files.write(f, "{not json".getBytes(StandardCharsets.UTF_8));
        SkinStore s = new SkinStore(f);
        assertThrows(IOException.class, s::load);
        assertEquals(0, s.size());
        assertFalse(Files.exists(f));
        try (java.util.stream.Stream<Path> files = Files.list(dir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("skins.json.corrupt-")));
        }
    }

    @Test
    void staleness() {
        long h = 3600_000L;
        SkinRecord hit = new SkinRecord("Notch", "mojang", new SkinData("V", null), 0, false);
        assertFalse(hit.isStale(h - 1, h, 60_000));
        assertTrue(hit.isStale(h, h, 60_000));

        SkinRecord miss = new SkinRecord("ghost", null, null, 0, false);
        assertTrue(miss.isStale(60_000, h, 60_000));
        assertFalse(miss.isStale(59_999, h, 60_000));

        SkinRecord url = new SkinRecord("https://example.com/s.png", "mineskin", new SkinData("V", "S"), 0, true);
        assertFalse(url.isStale(Long.MAX_VALUE / 2, h, 60_000));
    }
}
