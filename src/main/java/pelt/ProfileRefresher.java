package pelt;

import java.util.ArrayList;
import java.util.List;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketDestroyEntities;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.ForgeEventFactory;
import pelt.mixin.EntityTrackerAccessor;
import pelt.mixinsupport.MixinState;
import pelt.skin.SkinData;

/** Applies a texture to a live player and makes other clients re-render them. Server thread only. */
public final class ProfileRefresher {

    private ProfileRefresher() {}

    public static void setTextures(GameProfile profile, SkinData data) {
        profile.getProperties().removeAll("textures");
        if (data != null) {
            profile.getProperties().put("textures", data.signature == null
                    ? new Property("textures", data.value)
                    : new Property("textures", data.value, data.signature));
        }
    }

    /**
     * Re-sends the tab-list entry to everyone, then despawns/respawns the player for
     * everyone tracking them so their client creates a new entity with the new skin.
     * The player's OWN client caches its skin and only updates after a relog.
     */
    public static void refresh(MinecraftServer server, EntityPlayerMP target) {
        server.getPlayerList().sendPacketToAllPlayers(
                new SPacketPlayerListItem(SPacketPlayerListItem.Action.REMOVE_PLAYER, target));
        server.getPlayerList().sendPacketToAllPlayers(
                new SPacketPlayerListItem(SPacketPlayerListItem.Action.ADD_PLAYER, target));

        if (!MixinState.isApplied("EntityTrackerAccessor")) {
            Pelt.LOGGER.warn("EntityTracker accessor mixin not applied; others will see {}'s new skin after "
                    + "they leave view range or relog", target.getName());
            return;
        }
        EntityTrackerEntry entry = ((EntityTrackerAccessor) target.getServerWorld().getEntityTracker())
                .pelt$getTrackedEntities().lookup(target.getEntityId());
        if (entry == null) return;
        List<EntityPlayerMP> viewers = new ArrayList<>(entry.trackingPlayers);
        for (EntityPlayerMP viewer : viewers) {
            // Same as removeTrackedPlayerSymmetric, except the destroy packet is sent now:
            // removeEntity() would queue it for the viewer's next tick, i.e. AFTER the respawn below.
            entry.trackingPlayers.remove(viewer);
            target.removeTrackingPlayer(viewer);
            ForgeEventFactory.onStopEntityTracking(target, viewer);
            viewer.connection.sendPacket(new SPacketDestroyEntities(target.getEntityId()));
            entry.updatePlayerEntity(viewer);
        }
    }
}
