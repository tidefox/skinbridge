package pelt.mixinsupport;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Records which of our mixins were applied, so runtime code can degrade gracefully. */
public final class MixinState {

    private static final Set<String> APPLIED = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private MixinState() {}

    public static void markApplied(String mixinClassName) {
        APPLIED.add(mixinClassName);
    }

    public static boolean isApplied(String simpleName) {
        return APPLIED.contains("pelt.mixin." + simpleName);
    }
}
