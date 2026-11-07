package me.antonschouten.economy.Commands.Salary;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.SalaryManager;
import me.antonschouten.economy.Utils.CommandUtils;

public final class SalaryHelpCMD implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length != 0) {
            player.sendMessage(Main.prefix + "Usage: §a/" + label + "§f.");
            return true;
        }

        SalaryManager.GetSalaryInfo(player);
        return true;
    }
}
