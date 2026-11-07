package me.antonschouten.economy.Commands.Pay;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.EconomyManager;
import me.antonschouten.economy.Utils.CommandUtils;

public final class PayAllCMD implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Main.prefix + "Usage: §a/" + label + " <amount>§f.");
            return true;
        }

        Integer amount = CommandUtils.parsePositiveInt(player, args[0], "Amount");
        if (amount == null) {
            return true;
        }

        List<Player> recipients = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(player.getUniqueId())) {
                recipients.add(online);
            }
        }

        if (recipients.isEmpty()) {
            player.sendMessage(Main.prefix + "There are no other online players to pay.");
            return true;
        }

        long total = (long) amount * recipients.size();
        if (total > Integer.MAX_VALUE || EconomyManager.getBal(player) < total) {
            player.sendMessage(Main.prefix + "You need §a$" + total + " §fto pay §a" + recipients.size() + " §fplayer(s).");
            return true;
        }

        for (Player recipient : recipients) {
            if ((long) EconomyManager.getBal(recipient) + amount > Integer.MAX_VALUE) {
                player.sendMessage(Main.prefix + "Payment cancelled because §a" + recipient.getName()
                        + "§f cannot receive that amount without overflowing their balance.");
                return true;
            }
        }

        if (!EconomyManager.payAll(player, recipients, amount)) {
            player.sendMessage(Main.prefix + "The payment could not be completed.");
            return true;
        }

        for (Player recipient : recipients) {
            recipient.sendMessage(Main.prefix + "You received §a$" + amount + " §ffrom §a" + player.getName() + "§f.");
        }
        player.sendMessage(Main.prefix + "You paid §a$" + total + " §ftotal to §a" + recipients.size() + " §fplayer(s).");
        return true;
    }
}
