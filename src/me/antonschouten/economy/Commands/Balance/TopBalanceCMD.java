package me.antonschouten.economy.Commands.Balance;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.EconomyManager;
import me.antonschouten.economy.Utils.CommandUtils;

public final class TopBalanceCMD implements CommandExecutor {

    private static final int TOP_LIMIT = 10;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 0) {
            sender.sendMessage(Main.prefix + "Usage: §a/" + label + "§f.");
            return true;
        }

        List<UUID> topBalances = EconomyManager.getTopBalances(TOP_LIMIT);
        if (topBalances.isEmpty()) {
            sender.sendMessage(Main.prefix + "There are no economy accounts to display yet.");
            return true;
        }

        sender.sendMessage(Main.prefix + "Top " + Math.min(TOP_LIMIT, topBalances.size()) + " richest players:");
        for (int i = 0; i < topBalances.size(); i++) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(topBalances.get(i));
            sender.sendMessage("§8#" + (i + 1) + " §a" + CommandUtils.playerName(player) + "§f: $"
                    + EconomyManager.getBal(player));
        }
        return true;
    }
}
