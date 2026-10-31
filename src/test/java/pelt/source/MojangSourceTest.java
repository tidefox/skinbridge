package pelt.source;

import static org.junit.jupiter.api.Assertions.*;
import static pelt.skin.FakeHttp.r;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import pelt.skin.FakeHttp;
import pelt.skin.SkinData;
import pelt.skin.Textures;

class MojangSourceTest {

    static final String UUID = "069a79f444e94726a5befca90e38aaf5";

    @Test
    void resolvesSignedTextures() throws IOException {
        String v = Textures.value("http://textures.minecraft.net/texture/abc", false);
        FakeHttp http = new FakeHttp()
                .on("GET " + MojangSource.PROFILE_URL + "Notch", r(200, "{\"id\":\"" + UUID + "\",\"name\":\"Notch\"}"))
                .on("GET " + MojangSource.SESSION_URL + UUID + "?unsigned=false", r(200, Textures.profile(UUID, "Notch", v, "SIG")));
        SkinData d = new MojangSource(http).fetch("Notch");
        assertEquals(v, d.value);
        assertEquals("SIG", d.signature);
        assertEquals("http://textures.minecraft.net/texture/abc", d.skinUrl());
    }

    @Test
    void unknownNameIsMiss() throws IOException {
        FakeHttp http = new FakeHttp().on("GET " + MojangSource.PROFILE_URL + "nobody", r(204, ""));
        assertNull(new MojangSource(http).fetch("nobody"));
        FakeHttp http404 = new FakeHttp().on("GET " + MojangSource.PROFILE_URL + "nobody", r(404, "{}"));
        assertNull(new MojangSource(http404).fetch("nobody"));
    }

    @Test
    void rateLimitIsError() {
        FakeHttp http = new FakeHttp().on("GET " + MojangSource.PROFILE_URL + "Notch", r(429, ""));
        assertThrows(IOException.class, () -> new MojangSource(http).fetch("Notch"));
    }

    @Test
    void malformedJsonIsError() {
        FakeHttp http = new FakeHttp().on("GET " + MojangSource.PROFILE_URL + "Notch", r(200, "<html>"));
        assertThrows(IOException.class, () -> new MojangSource(http).fetch("Notch"));
    }
}
