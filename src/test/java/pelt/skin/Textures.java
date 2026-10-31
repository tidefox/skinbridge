package pelt.skin;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class Textures {

    private Textures() {}

    public static String value(String skinUrl, boolean slim) {
        String meta = slim ? ",\"metadata\":{\"model\":\"slim\"}" : "";
        String json = "{\"timestamp\":1,\"profileName\":\"x\",\"textures\":{\"SKIN\":{\"url\":\"" + skinUrl + "\"" + meta + "}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    public static String profile(String id, String name, String value, String sig) {
        String s = sig == null ? "" : ",\"signature\":\"" + sig + "\"";
        return "{\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"properties\":[{\"name\":\"textures\",\"value\":\""
                + value + "\"" + s + "}]}";
    }
}
