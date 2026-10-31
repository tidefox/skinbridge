package pelt.source;

import static org.junit.jupiter.api.Assertions.*;
import static pelt.skin.FakeHttp.r;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import pelt.skin.FakeHttp;
import pelt.skin.SkinData;
import pelt.skin.Textures;

class ElySourceTest {

    @Test
    void parsesSignedProfile() throws IOException {
        String v = Textures.value("http://ely.by/storage/skins/x.png", true);
        FakeHttp http = new FakeHttp().on("GET " + ElySource.URL + "erickskrauch?proxy=true",
                r(200, Textures.profile("ffc8fdc95824509e8a57c99b940fb996", "erickskrauch", v, "ELYSIG")));
        SkinData d = new ElySource(http).fetch("erickskrauch");
        assertEquals(v, d.value);
        assertEquals("ELYSIG", d.signature);
        assertTrue(d.isSlim());
        assertFalse(SkinData.isMojangHosted(d.skinUrl()));
    }

    @Test
    void noContentIsMiss() throws IOException {
        FakeHttp http = new FakeHttp().on("GET " + ElySource.URL + "ghost?proxy=true", r(204, ""));
        assertNull(new ElySource(http).fetch("ghost"));
    }

    @Test
    void serverErrorIsError() {
        FakeHttp http = new FakeHttp().on("GET " + ElySource.URL + "x?proxy=true", r(503, "down"));
        assertThrows(IOException.class, () -> new ElySource(http).fetch("x"));
    }
}
