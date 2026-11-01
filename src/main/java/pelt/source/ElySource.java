package pelt.source;

import java.io.IOException;
import java.util.Collections;

import pelt.skin.HttpFetcher;
import pelt.skin.HttpResponse;
import pelt.skin.SkinData;
import pelt.skin.SkinSource;

/**
 * Ely.by skin system. With proxy=true Ely falls back to the Mojang skin for names
 * that have no Ely skin. Signatures are Ely's own (not Mojang's); the vanilla
 * client does not require a valid signature for other players' skins.
 */
public final class ElySource implements SkinSource {

    static final String URL = "https://skinsystem.ely.by/textures/signed/";

    private final HttpFetcher http;

    public ElySource(HttpFetcher http) {
        this.http = http;
    }

    @Override
    public String id() {
        return "ely";
    }

    @Override
    public SkinData fetch(String name) throws IOException {
        HttpResponse r = http.get(URL + name + "?proxy=true", Collections.<String, String>emptyMap());
        if (r.code == 204 || r.code == 404) return null;
        if (!r.isOk()) throw new IOException("ely: HTTP " + r.code);
        if (r.body.trim().isEmpty()) return null;
        return Json.texturesFromProfile(Json.object(r.body, "ely"), "ely");
    }
}
