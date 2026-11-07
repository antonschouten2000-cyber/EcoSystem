package me.antonschouten.economy.Commands.TradeNPC;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.TradeManager;
import me.antonschouten.economy.Utils.CommandUtils;
import me.antonschouten.economy.Utils.Perms;

public final class TradeNPCCMD implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("spawn", "remove", "addtrade", "list");

    @SuppressWarnings("deprecation")
	@Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (!CommandUtils.requirePermission(player, Perms.TradeNPC)) {
            return true;
        }

        if (args.length == 0) {
            TradeManager.TradeHelpMessage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn" -> handleSpawn(player, args);
            case "remove" -> handleRemove(player, args);
            case "addtrade" -> handleAddTrade(player, args);
            case "list" -> handleList(player, args);
            default -> TradeManager.TradeHelpMessage(player);
        }
        return true;
    }

    private void handleSpawn(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(Main.prefix + "Usage: §a/tradenpc spawn <id>§f.");
            return;
        }

        String id = args[1];
        if (!CommandUtils.isSafeIdentifier(id, 32)) {
            player.sendMessage(Main.prefix + "Trader IDs may only contain letters, numbers, §a_ §for §a-§f (max 32 characters).");
            return;
        }
        if (findTrader(id) != null) {
            player.sendMessage(Main.prefix + "A trader with this ID already exists.");
            return;
        }

        TradeManager.spawnTrader(player.getLocation(), id);
        TradeManager.saveTraderToConfig(id, player.getLocation());
        player.sendMessage(Main.prefix + "Trader §a'" + id + "' §fhas been created.");
    }

    private void handleRemove(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(Main.prefix + "Usage: §a/tradenpc remove <id>§f.");
            return;
        }

        String id = findTrader(args[1]);
        if (id == null) {
            player.sendMessage(Main.prefix + "Trader §a'" + args[1] + "' §fdoesn't exist.");
            return;
        }

        TradeManager.removeTrader(id);
        player.sendMessage(Main.prefix + "Trader §a" + id + " §fhas been removed.");
    }

    private void handleAddTrade(Player player, String[] args) {
        if (args.length != 6) {
            player.sendMessage(Main.prefix
                    + "Usage: §a/tradenpc addtrade <id> <result> <resultAmount> <ingredient> <ingredientAmount>§f.");
            return;
        }

        String id = findTrader(args[1]);
        if (id == null) {
            player.sendMessage(Main.prefix + "Trader §a'" + args[1] + "' §fdoesn't exist.");
            return;
        }

        Material result = Material.matchMaterial(args[2]);
        Material ingredient = Material.matchMaterial(args[4]);
        if (result == null || ingredient == null || !result.isItem() || !ingredient.isItem()) {
            player.sendMessage(Main.prefix + "One or both material names are invalid.");
            return;
        }

        Integer resultAmount = CommandUtils.parsePositiveInt(player, args[3], "Result amount");
        Integer ingredientAmount = CommandUtils.parsePositiveInt(player, args[5], "Ingredient amount");
        if (resultAmount == null || ingredientAmount == null) {
            return;
        }
        if (resultAmount > result.getMaxStackSize()) {
            player.sendMessage(Main.prefix + "The result amount may not exceed §a" + result.getMaxStackSize() + "§f for this item.");
            return;
        }
        if (ingredientAmount > ingredient.getMaxStackSize()) {
            player.sendMessage(Main.prefix + "The ingredient amount may not exceed §a" + ingredient.getMaxStackSize() + "§f for this item.");
            return;
        }

        TradeManager.addTradeToConfig(id, result, resultAmount, ingredient, ingredientAmount);
        player.sendMessage(Main.prefix + "Added the trade to §a'" + id + "'§f.");
    }

    private void handleList(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(Main.prefix + "Usage: §a/tradenpc list§f.");
            return;
        }
        if (TradeManager.tradersList.isEmpty()) {
            player.sendMessage(Main.prefix + "There are no registered trader NPCs.");
            return;
        }

        player.sendMessage(Main.prefix + "Registered trader NPCs:");
        TradeManager.tradersList.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(id -> player.sendMessage("§8- §a" + id));
    }

    private String findTrader(String input) {
        return TradeManager.tradersList.stream()
                .filter(id -> id.equalsIgnoreCase(input))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return CommandUtils.complete(args[0], SUBCOMMANDS);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("addtrade"))) {
            return CommandUtils.complete(args[1], TradeManager.tradersList);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("addtrade")) {
            return CommandUtils.complete(args[2], CommandUtils.materialNames());
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("addtrade")) {
            return CommandUtils.complete(args[4], CommandUtils.materialNames());
        }
        return List.of();
    }
}
