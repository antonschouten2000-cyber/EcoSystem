package me.antonschouten.economy.Commands.Guild;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.GuildManager;
import me.antonschouten.economy.Utils.CommandUtils;

public final class GuildChatCMD implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) {
            return true;
        }

        String guild = GuildManager.getPlayerGuild(player.getName());
        if (guild == null) {
            player.sendMessage(Main.prefix + "You're not in a guild.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Main.prefix + "Usage: §a/" + label + " <message>§f.");
            return true;
        }

        String message = String.join(" ", args).trim();
        if (message.isEmpty()) {
            player.sendMessage(Main.prefix + "Your guild message cannot be empty.");
            return true;
        }

        String formatted = "§2[Guild] §a" + player.getName() + "§f: " + message;
        GuildManager.sendGuildMessage(guild, formatted);
        return true;
    }
}
