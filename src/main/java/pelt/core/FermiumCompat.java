package pelt.core;

import fermiumbooter.FermiumRegistryAPI;

/** Isolates the FermiumBooter reference so this class is only linked once we know it exists. */
final class FermiumCompat {

    private FermiumCompat() {}

    static boolean isPresent() {
        ClassLoader cl = PeltLoadingPlugin.class.getClassLoader();
        return cl != null && cl.getResource("fermiumbooter/FermiumRegistryAPI.class") != null;
    }

    static void enqueueEarly(String config) {
        // API present since FermiumBooter 1.x: enqueueMixin(boolean late, String configuration)
        FermiumRegistryAPI.enqueueMixin(false, config);
    }
}
