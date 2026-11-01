package pelt.skin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class UrlConnectionFetcher implements HttpFetcher {

    private static final int MAX_BODY = 1 << 20;

    private final int timeoutMs;
    private final String userAgent;

    public UrlConnectionFetcher(int timeoutMs, String userAgent) {
        this.timeoutMs = timeoutMs;
        this.userAgent = userAgent;
    }

    @Override
    public HttpResponse get(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection c = open(url, "GET", headers);
        return read(c);
    }

    @Override
    public HttpResponse postJson(String url, String jsonBody, Map<String, String> headers) throws IOException {
        HttpURLConnection c = open(url, "POST", headers);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream os = c.getOutputStream()) {
            os.write(bytes);
        }
        return read(c);
    }

    private HttpURLConnection open(String url, String method, Map<String, String> headers) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setInstanceFollowRedirects(true);
        c.setUseCaches(false);
        c.setRequestProperty("User-Agent", userAgent);
        c.setRequestProperty("Accept", "application/json");
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                c.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        return c;
    }

    private static HttpResponse read(HttpURLConnection c) throws IOException {
        try {
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String body = "";
            if (in != null) {
                try (InputStream is = in) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) != -1) {
                        if (out.size() + n > MAX_BODY) throw new IOException("Response too large from " + c.getURL());
                        out.write(buf, 0, n);
                    }
                    body = new String(out.toByteArray(), StandardCharsets.UTF_8);
                }
            }
            return new HttpResponse(code, body);
        } finally {
            c.disconnect();
        }
    }
}
