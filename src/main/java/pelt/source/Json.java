package pelt.source;

import java.io.IOException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import pelt.skin.SkinData;

final class Json {

    private Json() {}

    static JsonObject object(String body, String what) throws IOException {
        try {
            JsonElement e = new JsonParser().parse(body);
            if (!e.isJsonObject()) throw new IOException(what + ": expected a JSON object");
            return e.getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException(what + ": malformed JSON", e);
        }
    }

    static String string(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    static JsonObject child(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    /** Extracts the "textures" entry from a Yggdrasil-style {"properties":[{name,value,signature}]} profile. */
    static SkinData texturesFromProfile(JsonObject profile, String what) throws IOException {
        JsonElement props = profile.get("properties");
        if (props == null || !props.isJsonArray()) throw new IOException(what + ": no properties in profile");
        JsonArray arr = props.getAsJsonArray();
        for (JsonElement p : arr) {
            if (!p.isJsonObject()) continue;
            JsonObject po = p.getAsJsonObject();
            if ("textures".equals(string(po, "name"))) {
                String value = string(po, "value");
                if (value == null || value.isEmpty()) throw new IOException(what + ": empty textures value");
                return new SkinData(value, string(po, "signature"));
            }
        }
        return null;
    }
}
