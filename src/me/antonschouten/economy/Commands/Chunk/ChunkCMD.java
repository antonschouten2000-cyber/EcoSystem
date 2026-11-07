package me.antonschouten.economy.Commands.Chunk;

import java.util.List;

import org.bukkit.Chunk;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.ChunkClaim;
import me.antonschouten.economy.Manager.ChunkManager;
import me.antonschouten.economy.Utils.CommandUtils;
import me.antonschouten.economy.Utils.Perms;

public final class ChunkCMD implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("info", "claim", "unclaim", "trust", "untrust");

    @SuppressWarnings("deprecation")
	@Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (!CommandUtils.requirePermission(player, Perms.Chunk)) {
            return true;
        }

        Chunk chunk = player.getLocation().getChunk();
        if (args.length == 0) {
            ChunkManager.chunkHelpMessage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info" -> {
                if (args.length != 1) {
                    player.sendMessage(Main.prefix + "Usage: §a/chunk info§f.");
                } else {
                    ChunkManager.sendChunkInfo(player, chunk);
                }
            }
            case "claim" -> {
                if (args.length != 1) {
                    player.sendMessage(Main.prefix + "Usage: §a/chunk claim§f.");
                } else {
                    ChunkManager.claimChunk(player, chunk);
                }
            }
            case "unclaim" -> handleUnclaim(player, chunk, args);
            case "trust" -> handleTrust(player, chunk, args, true);
            case "untrust" -> handleTrust(player, chunk, args, false);
            default -> ChunkManager.chunkHelpMessage(player);
        }

        return true;
    }

    private void handleUnclaim(Player player, Chunk chunk, String[] args) {
        if (args.length != 1) {
            player.sendMessage(Main.prefix + "Usage: §a/chunk unclaim§f.");
            return;
        }

        if (ChunkManager.unclaimChunk(player, chunk)) {
            player.sendMessage(Main.prefix + "Successfully unclaimed this chunk.");
        } else {
            player.sendMessage(Main.prefix + "You do not own this chunk.");
        }
    }

    private void handleTrust(Player player, Chunk chunk, String[] args, boolean trust) {
        if (args.length != 2) {
            player.sendMessage(Main.prefix + "Usage: §a/chunk " + (trust ? "trust" : "untrust") + " <player>§f.");
            return;
        }

        ChunkClaim claim = ChunkManager.getClaim(chunk);
        if (claim == null) {
            player.sendMessage(Main.prefix + "This chunk isn't claimed.");
            return;
        }
        if (!claim.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(Main.prefix + "You do not own this chunk.");
            return;
        }

        OfflinePlayer target = CommandUtils.findKnownPlayer(args[1]);
        if (target == null) {
            player.sendMessage(Main.prefix + "Player §a" + args[1] + " §fwas not found.");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Main.prefix + "You are already the owner of this chunk.");
            return;
        }

        if (trust) {
            if (claim.isMember(target.getUniqueId())) {
                player.sendMessage(Main.prefix + "§a" + CommandUtils.playerName(target) + " §fis already trusted in this chunk.");
                return;
            }
            claim.addMember(target.getUniqueId());
            ChunkManager.saveClaims();
            player.sendMessage(Main.prefix + "Added §a" + CommandUtils.playerName(target) + " §fas a chunk member.");
            CommandUtils.notifyIfOnline(target, Main.prefix + "§a" + player.getName() + " §ftrusted you in a chunk.");
        } else {
            if (!claim.isMember(target.getUniqueId())) {
                player.sendMessage(Main.prefix + "§a" + CommandUtils.playerName(target) + " §fis not trusted in this chunk.");
                return;
            }
            claim.removeMember(target.getUniqueId());
            ChunkManager.saveClaims();
            player.sendMessage(Main.prefix + "Removed §a" + CommandUtils.playerName(target) + " §fas a chunk member.");
            CommandUtils.notifyIfOnline(target, Main.prefix + "§a" + player.getName() + " §funtrusted you in a chunk.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return CommandUtils.complete(args[0], SUBCOMMANDS);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust"))) {
            return CommandUtils.complete(args[1], CommandUtils.onlinePlayerNames(sender instanceof Player p ? p : null));
        }
        return List.of();
    }
}
