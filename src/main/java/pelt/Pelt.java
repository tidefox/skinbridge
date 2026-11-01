package pelt;

import java.io.File;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.PermissionAPI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pelt.command.SkinCommand;
import pelt.mixinsupport.MixinState;

@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION,
        acceptableRemoteVersions = "*", acceptedMinecraftVersions = "[1.12.2]")
public class Pelt {

    public static final Logger LOGGER = LogManager.getLogger("pelt");

    public static final String PERM_SET = "pelt.skin.set";
    public static final String PERM_OTHER = "pelt.skin.other";
    public static final String PERM_RELOAD = "pelt.skin.reload";

    private File configFile;
    private File storeFile;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        configFile = event.getSuggestedConfigurationFile(); // config/pelt.cfg
        storeFile = new File(new File(event.getModConfigurationDirectory(), "pelt"), "skins.json");
        PeltConfig.load(configFile); // write defaults on first start
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        PermissionAPI.registerNode(PERM_SET, DefaultPermissionLevel.ALL, "Use /skin set and /skin clear on yourself");
        PermissionAPI.registerNode(PERM_OTHER, DefaultPermissionLevel.OP, "Use /skin set|clear on other players; bypass cooldown");
        PermissionAPI.registerNode(PERM_RELOAD, DefaultPermissionLevel.OP, "Use /skin reload");
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        if (!MixinState.isApplied("MixinPlayerList")) {
            LOGGER.error("Pelt's PlayerList mixin is NOT applied (is FermiumBooter installed?). Skins will only "
                    + "appear after a refresh, not on join.");
        }
        SkinManager.start(event.getServer(), configFile, storeFile);
        event.registerServerCommand(new SkinCommand());
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        SkinManager.stop();
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        SkinManager m = SkinManager.get();
        if (m == null || !(event.player instanceof EntityPlayerMP)) return;
        try {
            m.onLoggedIn((EntityPlayerMP) event.player);
        } catch (Throwable t) {
            LOGGER.warn("Skin login handling failed for {}", event.player.getName(), t);
        }
    }
}
