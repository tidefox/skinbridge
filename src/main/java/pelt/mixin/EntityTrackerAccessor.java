package pelt.mixin;

import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.entity.EntityTracker;
import net.minecraft.util.IntHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Needed to re-send a player's spawn packet to the players already tracking them. */
@Mixin(EntityTracker.class)
public interface EntityTrackerAccessor {

    @Accessor("trackedEntityHashTable")
    IntHashMap<EntityTrackerEntry> pelt$getTrackedEntities();
}
