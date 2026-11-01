package pelt.skin;

import java.util.Collections;
import java.util.List;

/** Outcome of resolving an input: a skin (with the source that produced it) or "none found". */
public final class SkinResult {

    public final SkinData skin;
    public final String source;
    /** Per-source error messages, for logs and /skin feedback. */
    public final List<String> errors;

    SkinResult(SkinData skin, String source, List<String> errors) {
        this.skin = skin;
        this.source = source;
        this.errors = Collections.unmodifiableList(errors);
    }

    public boolean found() {
        return skin != null;
    }

    /** True when nothing was found AND at least one source failed (so the miss may be transient). */
    public boolean failed() {
        return skin == null && !errors.isEmpty();
    }
}
