package pelt.skin;

/** One persisted entry in skins.json. Fields are public for Gson. */
public final class SkinRecord {

    /** What the skin was resolved from: a player name or a URL. */
    public String input;
    /** Source id that produced the texture ("mojang", "ely", "mineskin", ...), null for a cached miss. */
    public String source;
    public String value;
    public String signature;
    public long fetchedAt;
    /** True if set with /skin set (kept on refresh); false for the automatic username default. */
    public boolean explicit;

    public SkinRecord() {}

    public SkinRecord(String input, String source, SkinData data, long fetchedAt, boolean explicit) {
        this.input = input;
        this.source = source;
        this.value = data == null ? null : data.value;
        this.signature = data == null ? null : data.signature;
        this.fetchedAt = fetchedAt;
        this.explicit = explicit;
    }

    public boolean hasSkin() {
        return value != null && !value.isEmpty();
    }

    public SkinData data() {
        return hasSkin() ? new SkinData(value, signature) : null;
    }

    /**
     * URL skins (MineSkin uploads) never change, so they never go stale.
     * Misses are retried after {@code missTtlMs}, hits after {@code ttlMs}.
     */
    public boolean isStale(long now, long ttlMs, long missTtlMs) {
        if (hasSkin() && SkinResolver.isUrl(input)) return false;
        long ttl = hasSkin() ? ttlMs : missTtlMs;
        return now - fetchedAt >= ttl;
    }
}
