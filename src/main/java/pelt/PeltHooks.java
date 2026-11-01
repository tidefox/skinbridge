package pelt;

import net.minecraft.entity.player.EntityPlayerMP;

/** Static entry points called from mixins. Must never throw into vanilla code. */
public final class PeltHooks {

    private PeltHooks() {}

    public static void beforePlayerJoin(EntityPlayerMP player) {
        SkinManager m = SkinManager.get();
        if (m == null) return;
        try {
            m.applyCached(player);
        } catch (Throwable t) {
            Pelt.LOGGER.warn("Failed to apply cached skin for {}", player.getName(), t);
        }
    }
}
