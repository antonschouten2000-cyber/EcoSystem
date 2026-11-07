package me.antonschouten.economy.Commands;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Data.DataFiles;
import me.antonschouten.economy.Manager.BoosterManager;
import me.antonschouten.economy.Manager.DataSaveManager;
import me.antonschouten.economy.Manager.SalaryManager;
import me.antonschouten.economy.Manager.TradeManager;
import me.antonschouten.economy.EcoSystem.EcoSystem;
import me.antonschouten.economy.Updater.Updater;
import me.antonschouten.economy.Transaction.TransactionManager;
import me.antonschouten.economy.Utils.CommandUtils;
import me.antonschouten.economy.Utils.Perms;

public final class HelpCMD implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of(
            "help", "info", "version", "donate", "reload", "update", "debug", "adminpanel");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            Player player = CommandUtils.requirePlayer(sender);
            if (player != null) {
                EcoSystem.SendHelpMessage(player);
            }
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(Main.prefix + "Usage: §a/" + label + " <subcommand>§f.");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info" -> sendInfo(sender);
            case "version" -> sender.sendMessage(Main.prefix + "Current plugin version: §a" + Main.instance.getDescription().getVersion() + "§f.");
            case "donate" -> sendDonate(sender);
            case "reload" -> handleReload(sender);
            case "update" -> handleUpdate(sender);
            case "debug" -> handleDebug(sender);
            case "adminpanel" -> handleAdminPanel(sender);
            default -> sender.sendMessage(Main.prefix + "Unknown subcommand. Use §a/" + label + " help §ffor help.");
        }
        return true;
    }

    private void sendInfo(CommandSender sender) {
        sender.sendMessage(Main.prefix + "Developers: §aantonschouten, basschouten, janschouten§f.");
        sender.sendMessage(Main.prefix + "Plugin version: §a" + Main.instance.getDescription().getVersion() + "§f.");
        sender.sendMessage(Main.prefix + "First release: §a27 March 2018§f.");
    }

    private void sendDonate(CommandSender sender) {
        sender.sendMessage(Main.prefix + "If you like our plugin and want to support us:");
        sender.sendMessage(Main.prefix + "PayPal: §bhttps://www.paypal.me/economyplusplus");
    }

    @SuppressWarnings("deprecation")
	private void handleReload(CommandSender sender) {
        if (!CommandUtils.requirePermission(sender, Perms.reload)) {
            return;
        }

        try {
            if (!DataSaveManager.flushAllAndReport()) {
                sender.sendMessage(Main.prefix + "§cReload cancelled because pending data could not be saved. Check the console.");
                return;
            }

            if (!DataFiles.reloadAll(Main.instance)) {
                sender.sendMessage(Main.prefix + "§cReload cancelled because one or more YAML files are invalid. Check the console.");
                return;
            }

            if (Main.getChunkClaimManager() != null) {
                Main.getChunkClaimManager().loadClaims();
            }
            BoosterManager.restoreActiveBoosters();
            SalaryManager.restart();
            TradeManager.loadTradersFromConfig();
            TransactionManager.reloadSettings();

            sender.sendMessage(Main.prefix + "All Economy++ data files were reloaded successfully.");
        } catch (RuntimeException exception) {
            Main.instance.getLogger().severe("Could not reload Economy++ data files: " + exception.getMessage());
            sender.sendMessage(Main.prefix + "§cReload failed. Check the server console for details.");
        }
    }

    @SuppressWarnings("deprecation")
	private void handleUpdate(CommandSender sender) {
        if (!CommandUtils.requirePermission(sender, Perms.updateMessage)) {
            return;
        }
        Player player = CommandUtils.requirePlayer(sender);
        if (player != null) {
            Updater.updateCheckerPlayer(player);
        }
    }

    @SuppressWarnings("deprecation")
	private void handleDebug(CommandSender sender) {
        if (!CommandUtils.requirePermission(sender, Perms.debugPlugin)) {
            return;
        }
        Player player = CommandUtils.requirePlayer(sender);
        if (player != null) {
            EcoSystem.DebugPlugin(player);
        }
    }

    @SuppressWarnings("deprecation")
	private void handleAdminPanel(CommandSender sender) {
        if (!CommandUtils.requirePermission(sender, Perms.adminPanel)) {
            return;
        }
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) {
            return;
        }

        if (!EcoSystem.adminpanelPassword.contains(player.getUniqueId())) {
            EcoSystem.adminpanelPassword.add(player.getUniqueId());
        }
        player.sendMessage(Main.prefix + "Enter the admin panel password in chat.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return CommandUtils.complete(args[0], SUBCOMMANDS);
        }
        return List.of();
    }
}
