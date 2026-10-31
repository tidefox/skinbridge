package pelt.source;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import pelt.skin.HttpFetcher;
import pelt.skin.HttpResponse;
import pelt.skin.SkinData;

/**
 * MineSkin v2: uploads an image URL to a Mojang account and returns Mojang-signed
 * textures hosted on textures.minecraft.net. Requires an API key.
 */
public final class MineSkinSource {

    static final String BASE = "https://api.mineskin.org";

    public interface Sleeper {
        void sleep(long ms) throws InterruptedException;
    }

    private final HttpFetcher http;
    private final String apiKey;
    private final int maxPolls;
    private final Sleeper sleeper;

    public MineSkinSource(HttpFetcher http, String apiKey) {
        this(http, apiKey, 15, Thread::sleep);
    }

    public MineSkinSource(HttpFetcher http, String apiKey, int maxPolls, Sleeper sleeper) {
        this.http = http;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.maxPolls = maxPolls;
        this.sleeper = sleeper;
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    public SkinData generateFromUrl(String imageUrl, boolean slim) throws IOException {
        if (!isConfigured()) throw new IOException("mineskin: no API key configured (mineskinApiKey)");
        JsonObject body = new JsonObject();
        body.addProperty("url", imageUrl);
        body.addProperty("variant", slim ? "slim" : "classic");
        body.addProperty("visibility", "unlisted");

        HttpResponse r = http.postJson(BASE + "/v2/generate", body.toString(), headers());
        JsonObject reply = parseReply(r);
        SkinData skin = skinFrom(reply);
        if (skin != null) return skin;

        // Request was queued instead of answered synchronously: poll the job.
        String jobId = Json.string(Json.child(reply, "job"), "id");
        if (jobId == null) throw new IOException("mineskin: reply had neither skin nor job");
        for (int i = 0; i < maxPolls; i++) {
            try {
                sleeper.sleep(2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("mineskin: interrupted");
            }
            JsonObject job = parseReply(http.get(BASE + "/v2/queue/" + jobId, headers()));
            skin = skinFrom(job);
            if (skin != null) return skin;
            String status = Json.string(Json.child(job, "job"), "status");
            if ("failed".equalsIgnoreCase(status)) throw new IOException("mineskin: job failed");
        }
        throw new IOException("mineskin: job " + jobId + " did not finish in time");
    }

    private Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        h.put("Authorization", "Bearer " + apiKey);
        return h;
    }

    private static JsonObject parseReply(HttpResponse r) throws IOException {
        if (r.code == 429) throw new IOException("mineskin: rate limited");
        if (r.code == 401 || r.code == 403) throw new IOException("mineskin: API key rejected (HTTP " + r.code + ")");
        JsonObject o = Json.object(r.body, "mineskin");
        if (!r.isOk() && r.code != 202) {
            throw new IOException("mineskin: HTTP " + r.code + " " + firstError(o));
        }
        return o;
    }

    private static String firstError(JsonObject o) {
        try {
            return o.getAsJsonArray("errors").get(0).getAsJsonObject().get("message").getAsString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static SkinData skinFrom(JsonObject reply) throws IOException {
        JsonObject data = Json.child(Json.child(Json.child(reply, "skin"), "texture"), "data");
        if (data == null) return null;
        String value = Json.string(data, "value");
        if (value == null || value.isEmpty()) throw new IOException("mineskin: empty texture value");
        return new SkinData(value, Json.string(data, "signature"));
    }
}
