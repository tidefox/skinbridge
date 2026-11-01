package pelt.skin;

public final class HttpResponse {

    public final int code;
    public final String body;

    public HttpResponse(int code, String body) {
        this.code = code;
        this.body = body == null ? "" : body;
    }

    public boolean isOk() {
        return code >= 200 && code < 300 && code != 204;
    }
}
