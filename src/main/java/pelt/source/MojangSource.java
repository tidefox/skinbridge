package pelt.source;

import java.io.IOException;
import java.util.Collections;

import com.google.gson.JsonObject;
import pelt.skin.HttpFetcher;
import pelt.skin.HttpResponse;
import pelt.skin.SkinData;
import pelt.skin.SkinSource;

/** Premium Minecraft account skins: name -> uuid -> signed textures. */
public final class MojangSource implements SkinSource {

    static final String PROFILE_URL = "https://api.mojang.com/users/profiles/minecraft/";
    static final String SESSION_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";

    private final HttpFetcher http;

    public MojangSource(HttpFetcher http) {
        this.http = http;
    }

    @Override
    public String id() {
        return "mojang";
    }

    @Override
    public SkinData fetch(String name) throws IOException {
        HttpResponse r = http.get(PROFILE_URL + name, Collections.<String, String>emptyMap());
        if (r.code == 204 || r.code == 404) return null;
        if (!r.isOk()) throw new IOException("mojang: profile lookup HTTP " + r.code);
        String uuid = Json.string(Json.object(r.body, "mojang"), "id");
        if (uuid == null || !uuid.matches("[0-9a-fA-F]{32}")) throw new IOException("mojang: bad uuid in reply");

        HttpResponse s = http.get(SESSION_URL + uuid + "?unsigned=false", Collections.<String, String>emptyMap());
        if (s.code == 204 || s.code == 404) return null;
        if (!s.isOk()) throw new IOException("mojang: session profile HTTP " + s.code);
        return Json.texturesFromProfile(Json.object(s.body, "mojang"), "mojang");
    }
}
