package pelt.skin;

import java.io.IOException;

public interface SkinSource {

    /** Short id used in config and in /skin feedback, e.g. "mojang". */
    String id();

    /**
     * @return the skin for the given input, or null if this source has no skin for it.
     * @throws IOException on network errors, rate limits or malformed replies (caller tries next source).
     */
    SkinData fetch(String input) throws IOException;
}
