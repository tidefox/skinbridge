package pelt.skin;

import static org.junit.jupiter.api.Assertions.*;
import static pelt.skin.FakeHttp.r;

import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import pelt.source.ElySource;
import pelt.source.MineSkinSource;
import pelt.source.MojangSource;

class SkinResolverTest {

    static final String MOJ = "https://api.mojang.com/users/profiles/minecraft/";
    static final String ELY = "https://skinsystem.ely.by/textures/signed/";
    static final String GEN = "POST https://api.mineskin.org/v2/generate";

    static SkinResolver resolver(FakeHttp http, String key, boolean rehost) {
        return new SkinResolver(Arrays.asList(new MojangSource(http), new ElySource(http)),
                new MineSkinSource(http, key, 2, ms -> {}), rehost);
    }

    @Test
    void fallsBackToElyWhenMojangErrors() {
        String v = Textures.value("http://textures.minecraft.net/texture/e", false);
        FakeHttp http = new FakeHttp()
                .on("GET " + MOJ + "Steve", new IOException("timeout"))
                .on("GET " + ELY + "Steve?proxy=true", r(200, Textures.profile("a", "Steve", v, "S")));
        SkinResult res = resolver(http, "", true).resolve("Steve", false);
        assertTrue(res.found());
        assertEquals("ely", res.source);
        assertEquals(1, res.errors.size());
        assertTrue(res.errors.get(0).startsWith("mojang:"));
    }

    @Test
    void fallsBackToElyWhenMojangMisses() {
        String v = Textures.value("http://textures.minecraft.net/texture/e", false);
        FakeHttp http = new FakeHttp()
                .on("GET " + MOJ + "ElyOnly", r(204, ""))
                .on("GET " + ELY + "ElyOnly?proxy=true", r(200, Textures.profile("a", "ElyOnly", v, "S")));
        SkinResult res = resolver(http, "", true).resolve("ElyOnly", false);
        assertEquals("ely", res.source);
        assertTrue(res.errors.isEmpty());
    }

    @Test
    void nothingFoundIsMissNotFailure() {
        FakeHttp http = new FakeHttp().on("GET " + MOJ + "ghost", r(204, "")).on("GET " + ELY + "ghost?proxy=true", r(204, ""));
        SkinResult res = resolver(http, "", true).resolve("ghost", false);
        assertFalse(res.found());
        assertFalse(res.failed());
    }

    @Test
    void allSourcesDownIsFailure() {
        FakeHttp http = new FakeHttp().on("GET " + MOJ + "x", new IOException("dns")).on("GET " + ELY + "x?proxy=true", r(500, ""));
        SkinResult res = resolver(http, "", true).resolve("x", false);
        assertTrue(res.failed());
        assertEquals(2, res.errors.size());
    }

    @Test
    void nonMojangElySkinIsRehostedThroughMineSkin() {
        String v = Textures.value("http://ely.by/storage/skins/q.png", true);
        FakeHttp http = new FakeHttp()
                .on("GET " + MOJ + "q", r(204, ""))
                .on("GET " + ELY + "q?proxy=true", r(200, Textures.profile("a", "q", v, "ELY")))
                .on(GEN, r(200, "{\"skin\":{\"texture\":{\"data\":{\"value\":\"MS\",\"signature\":\"MSIG\"}}}}"));
        SkinResult res = resolver(http, "key", true).resolve("q", false);
        assertEquals("ely+mineskin", res.source);
        assertEquals("MS", res.skin.value);
        String body = http.bodies.get(2);
        assertTrue(body.contains("http://ely.by/storage/skins/q.png"));
        assertTrue(body.contains("\"variant\":\"slim\""));
    }

    @Test
    void rehostFailureKeepsElySkin() {
        String v = Textures.value("http://ely.by/storage/skins/q.png", false);
        FakeHttp http = new FakeHttp()
                .on("GET " + MOJ + "q", r(204, ""))
                .on("GET " + ELY + "q?proxy=true", r(200, Textures.profile("a", "q", v, "ELY")))
                .on(GEN, r(429, ""));
        SkinResult res = resolver(http, "key", true).resolve("q", false);
        assertEquals("ely", res.source);
        assertEquals(v, res.skin.value);
    }

    @Test
    void urlGoesToMineSkinOnly() {
        FakeHttp http = new FakeHttp().on(GEN, r(200, "{\"skin\":{\"texture\":{\"data\":{\"value\":\"U\",\"signature\":\"US\"}}}}"));
        SkinResult res = resolver(http, "key", true).resolve("https://example.com/skin.png", false);
        assertEquals("mineskin", res.source);
        assertEquals(1, http.requests.size());

        SkinResult noKey = resolver(new FakeHttp(), "", true).resolve("https://example.com/skin.png", false);
        assertFalse(noKey.found());
        assertTrue(noKey.failed());
    }

    @Test
    void rejectsBadInputWithoutNetwork() {
        FakeHttp http = new FakeHttp();
        for (String bad : new String[] {"", "a b", "../etc", "name?x=1", "waytoolongname_12345", "ftp://x/y.png"}) {
            assertFalse(resolver(http, "key", true).resolve(bad, false).found(), bad);
        }
        assertTrue(http.requests.isEmpty());
        assertTrue(SkinResolver.isValidName("Some_Name123"));
    }

    @Test
    void mojangHostDetection() {
        assertTrue(SkinData.isMojangHosted("http://textures.minecraft.net/texture/abc"));
        assertTrue(SkinData.isMojangHosted("https://TEXTURES.minecraft.net:443/texture/abc"));
        assertFalse(SkinData.isMojangHosted("http://textures.minecraft.net.evil.com/x"));
        assertFalse(SkinData.isMojangHosted("http://ely.by/storage/skins/x.png"));
        assertFalse(SkinData.isMojangHosted(null));
    }
}
