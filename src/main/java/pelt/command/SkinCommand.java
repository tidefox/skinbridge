package pelt.command;

import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.server.permission.PermissionAPI;
import pelt.Pelt;
import pelt.PeltConfig;
import pelt.SkinManager;
import pelt.skin.SkinResolver;

/**
 * /skin set <name|url>            - yourself              (pelt.skin.set)
 * /skin set <player> <name|url>   - someone else          (pelt.skin.other)
 * /skin clear [player]            - back to default skin  (pelt.skin.set / pelt.skin.other)
 * /skin reload [player]           - re-read config, re-fetch online players (pelt.skin.reload)
 */
public class SkinCommand extends CommandBase {

    private static final String USAGE = "/skin set <name|url> | /skin set <player> <name|url> | /skin clear [player] | /skin reload [player]";

    @Override
    public String getName() {
        return "skin";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return USAGE;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    /** Real checks are per sub-command below. */
    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        SkinManager m = SkinManager.get();
        if (m == null) throw new CommandException("Pelt is not running.");
        if (args.length == 0) throw new WrongUsageException(USAGE);

        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "set":
                set(server, sender, m, args);
                return;
            case "clear":
                clear(server, sender, m, args);
                return;
            case "reload":
                require(sender, Pelt.PERM_RELOAD);
                List<EntityPlayerMP> targets = args.length >= 2
                        ? Collections.singletonList(getPlayer(server, sender, args[1]))
                        : server.getPlayerList().getPlayers();
                m.reload(new java.util.ArrayList<>(targets), sender);
                return;
            default:
                throw new WrongUsageException(USAGE);
        }
    }

    private void set(MinecraftServer server, ICommandSender sender, SkinManager m, String[] args) throws CommandException {
        EntityPlayerMP target;
        String input;
        if (args.length == 2) {
            require(sender, Pelt.PERM_SET);
            target = getCommandSenderAsPlayer(sender);
            input = args[1];
        } else if (args.length == 3) {
            require(sender, Pelt.PERM_OTHER);
            target = getPlayer(server, sender, args[1]);
            input = args[2];
        } else {
            throw new WrongUsageException("/skin set <name|url>  or  /skin set <player> <name|url>");
        }

        PeltConfig cfg = m.config();
        if (SkinResolver.isUrl(input)) {
            if (!cfg.allowUrlSkins) throw new CommandException("URL skins are disabled on this server.");
            if (cfg.mineskinApiKey.isEmpty()) throw new CommandException("URL skins need a MineSkin API key in config/pelt.cfg.");
        } else if (!SkinResolver.isValidName(input)) {
            throw new CommandException("'" + input + "' is not a valid player name (A-Z, 0-9, _; max 16) or http(s) URL.");
        }

        if (sender instanceof EntityPlayerMP && !has(sender, Pelt.PERM_OTHER)) {
            EntityPlayerMP self = (EntityPlayerMP) sender;
            long left = m.cooldownLeft(self.getUniqueID());
            if (left > 0) throw new CommandException("Please wait " + left + "s before changing your skin again.");
            m.markCooldown(self.getUniqueID());
        }
        m.set(target.getUniqueID(), target.getName(), input, sender);
    }

    private void clear(MinecraftServer server, ICommandSender sender, SkinManager m, String[] args) throws CommandException {
        EntityPlayerMP target;
        if (args.length == 1) {
            require(sender, Pelt.PERM_SET);
            target = getCommandSenderAsPlayer(sender);
        } else if (args.length == 2) {
            require(sender, Pelt.PERM_OTHER);
            target = getPlayer(server, sender, args[1]);
        } else {
            throw new WrongUsageException("/skin clear [player]");
        }
        m.clear(target, sender);
    }

    /** Players: Forge PermissionAPI (works with permission mods). Console/RCON: always. Others: op level 2. */
    private static boolean has(ICommandSender sender, String node) {
        if (sender instanceof EntityPlayerMP) return PermissionAPI.hasPermission((EntityPlayerMP) sender, node);
        if (sender instanceof MinecraftServer) return true;
        return sender.canUseCommand(2, "skin");
    }

    private static void require(ICommandSender sender, String node) throws CommandException {
        if (!has(sender, node)) throw new CommandException("You don't have permission (" + node + ").");
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "set", "clear", "reload");
        if (args.length == 2 && ("clear".equalsIgnoreCase(args[0]) || "reload".equalsIgnoreCase(args[0])
                || "set".equalsIgnoreCase(args[0]))) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return Collections.emptyList();
    }
}
