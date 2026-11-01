package pelt.skin;

import java.io.IOException;
import java.util.Map;

/** Minimal HTTP abstraction so skin sources can be unit-tested without network. */
public interface HttpFetcher {

    HttpResponse get(String url, Map<String, String> headers) throws IOException;

    HttpResponse postJson(String url, String jsonBody, Map<String, String> headers) throws IOException;
}
