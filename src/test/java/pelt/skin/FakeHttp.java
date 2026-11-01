package pelt.skin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Scripted HttpFetcher: exact-URL routes, records every request. */
public final class FakeHttp implements HttpFetcher {

    public final List<String> requests = new ArrayList<>();
    public final List<String> bodies = new ArrayList<>();
    public final List<Map<String, String>> headers = new ArrayList<>();
    private final Map<String, List<Object>> routes = new HashMap<>();

    /** Queue a response (HttpResponse) or a failure (IOException) for "GET url" / "POST url". */
    public FakeHttp on(String methodAndUrl, Object... responses) {
        List<Object> list = routes.computeIfAbsent(methodAndUrl, k -> new ArrayList<>());
        for (Object r : responses) list.add(r);
        return this;
    }

    public static HttpResponse r(int code, String body) {
        return new HttpResponse(code, body);
    }

    @Override
    public HttpResponse get(String url, Map<String, String> h) throws IOException {
        return answer("GET " + url, null, h);
    }

    @Override
    public HttpResponse postJson(String url, String jsonBody, Map<String, String> h) throws IOException {
        return answer("POST " + url, jsonBody, h);
    }

    private HttpResponse answer(String key, String body, Map<String, String> h) throws IOException {
        requests.add(key);
        bodies.add(body);
        headers.add(h);
        List<Object> list = routes.get(key);
        if (list == null || list.isEmpty()) throw new AssertionError("unexpected request: " + key);
        Object o = list.size() > 1 ? list.remove(0) : list.get(0);
        if (o instanceof IOException) throw (IOException) o;
        return (HttpResponse) o;
    }
}
