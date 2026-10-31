package pelt.core;

import java.util.Map;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Coremod entry point. Its only job is to hand our mixin config to FermiumBooter
 * as an EARLY mixin (vanilla targets). FermiumBooter (SortingIndex 990) adds all
 * early configs to Mixin in its own injectData, which FML calls after every
 * coremod plugin has been constructed and after lower-index plugins' injectData.
 *
 * We try in the constructor (the documented place) and, if FermiumBooter's jar was
 * not on the classpath yet, again in injectData (we sort before FermiumBooter).
 * Presence is checked by resource lookup so a missing FermiumBooter never causes a
 * sticky NoClassDefFoundError.
 */
@IFMLLoadingPlugin.Name("Pelt")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(500)
@IFMLLoadingPlugin.TransformerExclusions({"pelt.core."})
public class PeltLoadingPlugin implements IFMLLoadingPlugin {

    public static final String MIXIN_CONFIG = "mixins.pelt.json";
    private static final Logger LOGGER = LogManager.getLogger("pelt");

    private static boolean enqueued = false;

    public PeltLoadingPlugin() {
        tryEnqueue(false);
    }

    @Override
    public void injectData(Map<String, Object> data) {
        tryEnqueue(true);
    }

    private static void tryEnqueue(boolean lastChance) {
        if (enqueued) return;
        if (!FermiumCompat.isPresent()) {
            if (lastChance) {
                LOGGER.error("FermiumBooter was not found. Pelt's mixins will NOT be applied and skins will not be "
                        + "injected. Install FermiumBooter (>= 1.4.1) in mods/.");
            }
            return;
        }
        FermiumCompat.enqueueEarly(MIXIN_CONFIG);
        enqueued = true;
        LOGGER.info("Enqueued {} with FermiumBooter for early mixin application", MIXIN_CONFIG);
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
