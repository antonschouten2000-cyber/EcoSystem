package me.antonschouten.economy.Commands.Pay;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.EconomyManager;
import me.antonschouten.economy.Utils.CommandUtils;

public final class PayCMD implements TabExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(Main.prefix + "Usage: §a/" + label + " <player> <amount>§f.");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(Main.prefix + "Player §a" + args[0] + " §fis not online.");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Main.prefix + "You can't pay yourself.");
            return true;
        }

        Integer amount = CommandUtils.parsePositiveInt(player, args[1], "Amount");
        if (amount == null) {
            return true;
        }
        if (EconomyManager.getBal(player) < amount) {
            player.sendMessage(Main.prefix + "You don't have enough money.");
            return true;
        }
        if ((long) EconomyManager.getBal(target) + amount > Integer.MAX_VALUE) {
            player.sendMessage(Main.prefix + "That player's balance is too high to receive this amount.");
            return true;
        }

        if (!EconomyManager.payBal(player, target, amount)) {
            player.sendMessage(Main.prefix + "The payment could not be completed.");
            return true;
        }

        player.sendMessage(Main.prefix + "You paid §a$" + amount + " §fto §a" + target.getName() + "§f.");
        target.sendMessage(Main.prefix + "§a" + player.getName() + " §fpaid you §a$" + amount + "§f.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return CommandUtils.complete(args[0], CommandUtils.onlinePlayerNames(sender instanceof Player p ? p : null));
        }
        return List.of();
    }
}
