package pelt.source;

import static org.junit.jupiter.api.Assertions.*;
import static pelt.skin.FakeHttp.r;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import pelt.skin.FakeHttp;
import pelt.skin.SkinData;

class MineSkinSourceTest {

    static final String GEN = "POST " + MineSkinSource.BASE + "/v2/generate";

    static String skin(String value, String sig) {
        return "{\"success\":true,\"skin\":{\"uuid\":\"u\",\"texture\":{\"data\":{\"value\":\"" + value
                + "\",\"signature\":\"" + sig + "\"},\"url\":{\"skin\":\"http://textures.minecraft.net/texture/z\"}}}}";
    }

    @Test
    void syncGenerate() throws IOException {
        FakeHttp http = new FakeHttp().on(GEN, r(200, skin("VAL", "SIG")));
        SkinData d = new MineSkinSource(http, "key123", 3, ms -> {}).generateFromUrl("https://i.imgur.com/a.png", true);
        assertEquals("VAL", d.value);
        assertEquals("SIG", d.signature);
        assertEquals("Bearer key123", http.headers.get(0).get("Authorization"));
        assertTrue(http.bodies.get(0).contains("\"variant\":\"slim\""));
        assertTrue(http.bodies.get(0).contains("\"url\":\"https://i.imgur.com/a.png\""));
    }

    @Test
    void queuedJobIsPolled() throws IOException {
        FakeHttp http = new FakeHttp()
                .on(GEN, r(202, "{\"success\":true,\"job\":{\"id\":\"J1\",\"status\":\"waiting\"}}"))
                .on("GET " + MineSkinSource.BASE + "/v2/queue/J1",
                        r(200, "{\"job\":{\"id\":\"J1\",\"status\":\"processing\"}}"),
                        r(200, "{\"job\":{\"id\":\"J1\",\"status\":\"completed\"}," + skin("V2", "S2").substring(1)));
        SkinData d = new MineSkinSource(http, "k", 5, ms -> {}).generateFromUrl("https://x.example/s.png", false);
        assertEquals("V2", d.value);
        assertEquals(3, http.requests.size());
    }

    @Test
    void failedJobAndTimeout() {
        FakeHttp failed = new FakeHttp()
                .on(GEN, r(202, "{\"job\":{\"id\":\"J\",\"status\":\"waiting\"}}"))
                .on("GET " + MineSkinSource.BASE + "/v2/queue/J", r(200, "{\"job\":{\"id\":\"J\",\"status\":\"failed\"}}"));
        assertThrows(IOException.class, () -> new MineSkinSource(failed, "k", 5, ms -> {}).generateFromUrl("https://x.example/a.png", false));

        FakeHttp slow = new FakeHttp()
                .on(GEN, r(202, "{\"job\":{\"id\":\"J\",\"status\":\"waiting\"}}"))
                .on("GET " + MineSkinSource.BASE + "/v2/queue/J", r(200, "{\"job\":{\"id\":\"J\",\"status\":\"processing\"}}"));
        assertThrows(IOException.class, () -> new MineSkinSource(slow, "k", 2, ms -> {}).generateFromUrl("https://x.example/a.png", false));
    }

    @Test
    void errorsAndMissingKey() {
        FakeHttp bad = new FakeHttp().on(GEN, r(400, "{\"success\":false,\"errors\":[{\"code\":\"invalid_image\",\"message\":\"bad image\"}]}"));
        IOException e = assertThrows(IOException.class, () -> new MineSkinSource(bad, "k").generateFromUrl("https://x.example/a.png", false));
        assertTrue(e.getMessage().contains("bad image"));

        FakeHttp limited = new FakeHttp().on(GEN, r(429, "{}"));
        assertThrows(IOException.class, () -> new MineSkinSource(limited, "k").generateFromUrl("https://x.example/a.png", false));

        FakeHttp none = new FakeHttp();
        MineSkinSource noKey = new MineSkinSource(none, "  ");
        assertFalse(noKey.isConfigured());
        assertThrows(IOException.class, () -> noKey.generateFromUrl("https://x.example/a.png", false));
        assertTrue(none.requests.isEmpty());
    }
}
