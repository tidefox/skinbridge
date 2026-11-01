package pelt.skin;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** A "textures" profile property: base64 value plus optional signature. */
public final class SkinData {

    public final String value;
    public final String signature;

    public SkinData(String value, String signature) {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("empty texture value");
        this.value = value;
        this.signature = (signature == null || signature.isEmpty()) ? null : signature;
    }

    /** @return the SKIN texture URL inside the value, or null if none / unparsable. */
    public String skinUrl() {
        JsonObject skin = skinObject();
        return skin != null && skin.has("url") ? skin.get("url").getAsString() : null;
    }

    /** @return true if the SKIN uses the slim (Alex) model. */
    public boolean isSlim() {
        JsonObject skin = skinObject();
        if (skin == null || !skin.has("metadata")) return false;
        JsonObject meta = skin.getAsJsonObject("metadata");
        return meta.has("model") && "slim".equalsIgnoreCase(meta.get("model").getAsString());
    }

    private JsonObject skinObject() {
        try {
            String json = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            if (!root.has("textures")) return null;
            JsonObject textures = root.getAsJsonObject("textures");
            return textures.has("SKIN") ? textures.getAsJsonObject("SKIN") : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Vanilla 1.12 clients only reliably load textures hosted by Mojang. */
    public static boolean isMojangHosted(String url) {
        if (url == null) return false;
        String u = url.toLowerCase(java.util.Locale.ROOT);
        int i = u.indexOf("://");
        if (i < 0) return false;
        String host = u.substring(i + 3);
        int slash = host.indexOf('/');
        if (slash >= 0) host = host.substring(0, slash);
        int colon = host.indexOf(':');
        if (colon >= 0) host = host.substring(0, colon);
        return host.equals("textures.minecraft.net") || host.endsWith(".minecraft.net") || host.endsWith(".mojang.com");
    }
}
