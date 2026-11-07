package me.antonschouten.economy.Commands.Balance;

import java.util.List;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.EconomyManager;
import me.antonschouten.economy.Transaction.TransactionReason;
import me.antonschouten.economy.Utils.CommandUtils;
import me.antonschouten.economy.Utils.Perms;

public final class RemoveBalanceCMD implements TabExecutor {

    @SuppressWarnings("deprecation")
	@Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!CommandUtils.requirePermission(sender, Perms.removeBal)) {
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(Main.prefix + "Usage: §a/" + label + " <player> <amount>§f.");
            return true;
        }

        OfflinePlayer target = CommandUtils.findKnownPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Main.prefix + "Player §a" + args[0] + " §fwas not found.");
            return true;
        }

        Integer amount = CommandUtils.parsePositiveInt(sender, args[1], "Amount");
        if (amount == null) {
            return true;
        }

        int currentBalance = EconomyManager.getBal(target);
        if (currentBalance < amount) {
            sender.sendMessage(Main.prefix + "§a" + CommandUtils.playerName(target) + " §fdoesn't have enough money.");
            return true;
        }

        EconomyManager.tryWithdraw(target, amount, TransactionReason.ADMIN_REMOVE, sender, "command=/removebal");
        String targetName = CommandUtils.playerName(target);
        sender.sendMessage(Main.prefix + "Removed §a$" + amount + " §ffrom §a" + targetName + "§f.");
        CommandUtils.notifyIfOnline(target, Main.prefix + "§a$" + amount + " §fhas been removed from your account.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return CommandUtils.complete(args[0], CommandUtils.onlinePlayerNames(null));
        }
        return List.of();
    }
}
