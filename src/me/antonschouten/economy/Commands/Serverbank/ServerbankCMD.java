package me.antonschouten.economy.Commands.Serverbank;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.EconomyManager;

public final class ServerbankCMD implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 0) {
            sender.sendMessage(Main.prefix + "Usage: §a/" + label + "§f.");
            return true;
        }

        sender.sendMessage(Main.prefix + "Current server bank balance: §a$" + EconomyManager.getServerbankBalance() + "§f.");
        return true;
    }
}
