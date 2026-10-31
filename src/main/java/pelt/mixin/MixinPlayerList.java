package pelt.mixin;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.management.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pelt.PeltHooks;

/**
 * Puts the cached "textures" property on the joining player's GameProfile before
 * PlayerList sends the tab-list (SPacketPlayerListItem) and spawn packets, so
 * everyone (including the joining player) receives the skin in the first packets.
 * Cache lookup only - no I/O happens here.
 */
@Mixin(PlayerList.class)
public abstract class MixinPlayerList {

    @Inject(method = "initializeConnectionToPlayer", at = @At("HEAD"))
    private void pelt$applyCachedSkin(NetworkManager netManager, EntityPlayerMP player,
                                      NetHandlerPlayServer handler, CallbackInfo ci) {
        PeltHooks.beforePlayerJoin(player);
    }
}
