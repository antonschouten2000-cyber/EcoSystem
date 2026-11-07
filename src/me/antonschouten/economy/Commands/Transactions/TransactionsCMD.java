package me.antonschouten.economy.Commands.Transactions;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Transaction.TransactionManager;
import me.antonschouten.economy.Transaction.TransactionParty;
import me.antonschouten.economy.Transaction.TransactionRecord;
import me.antonschouten.economy.Utils.CommandUtils;
import me.antonschouten.economy.Utils.Perms;

public final class TransactionsCMD implements TabExecutor {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd-MM HH:mm")
            .withZone(ZoneId.systemDefault());

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(Perms.TRANSACTIONS)) {
            sender.sendMessage(Main.perms);
            return true;
        }

        if (!TransactionManager.isEnabled()) {
            sender.sendMessage(Main.prefix + "Transaction logging is disabled in Config.yml.");
            return true;
        }

        Target target = resolveTarget(sender, args);
        if (target == null) {
            return true;
        }

        if (!target.uuid().equals(sender instanceof Player player ? player.getUniqueId() : null)
                && !sender.hasPermission(Perms.TRANSACTIONS_OTHERS)) {
            sender.sendMessage(Main.perms);
            return true;
        }

        List<TransactionRecord> records = TransactionManager.getForPlayer(target.uuid());
        if (records.isEmpty()) {
            sender.sendMessage(Main.prefix + "No recent transactions were found for §a" + target.name() + "§f.");
            return true;
        }

        int pageSize = TransactionManager.getPageSize();
        int totalPages = Math.max(1, (records.size() + pageSize - 1) / pageSize);
        int page = Math.min(target.page(), totalPages);
        int start = (page - 1) * pageSize;
        int end = Math.min(records.size(), start + pageSize);

        sender.sendMessage("§8----------- §6Economy++ Transactions §8-----------");
        sender.sendMessage("§fPlayer: §a" + target.name() + " §8| §fPage: §a" + page + "§f/§a" + totalPages);

        for (int i = start; i < end; i++) {
            sender.sendMessage(format(records.get(i), target.uuid(), sender));
        }

        sender.sendMessage("§8Use §a/" + label + " " + target.name() + " <page> §8to browse history.");
        return true;
    }

    private static Target resolveTarget(CommandSender sender, String[] args) {
        if (sender instanceof Player player) {
            if (args.length == 0) {
                return new Target(player.getUniqueId(), player.getName(), 1);
            }
            if (args.length == 1 && isPositiveInteger(args[0])) {
                return new Target(player.getUniqueId(), player.getName(), parsePage(args[0]));
            }
            if (args.length > 2) {
                sender.sendMessage(Main.prefix + "Usage: §a/transactions [player] [page]§f.");
                return null;
            }
            OfflinePlayer target = CommandUtils.findKnownPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(Main.prefix + "Player §a" + args[0] + " §fwas not found.");
                return null;
            }
            int page = args.length == 2 ? parsePageOrError(sender, args[1]) : 1;
            if (page < 1) {
                return null;
            }
            return new Target(target.getUniqueId(), CommandUtils.playerName(target), page);
        }

        if (args.length < 1 || args.length > 2) {
            sender.sendMessage(Main.prefix + "Console usage: §a/transactions <player> [page]§f.");
            return null;
        }
        OfflinePlayer target = CommandUtils.findKnownPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Main.prefix + "Player §a" + args[0] + " §fwas not found.");
            return null;
        }
        int page = args.length == 2 ? parsePageOrError(sender, args[1]) : 1;
        if (page < 1) {
            return null;
        }
        return new Target(target.getUniqueId(), CommandUtils.playerName(target), page);
    }

    private static String format(TransactionRecord record, UUID playerUuid, CommandSender viewer) {
        Integer delta = record.deltaFor(playerUuid);
        TransactionParty party = record.partyFor(playerUuid);
        TransactionParty other = record.counterpartyFor(playerUuid);

        String amount;
        if (delta == null) {
            amount = "§7$" + record.amount();
        } else if (delta > 0) {
            amount = "§a+$" + delta;
        } else if (delta < 0) {
            amount = "§c-$" + Math.abs(delta);
        } else {
            amount = "§7$0";
        }

        StringBuilder line = new StringBuilder();
        line.append("§8#")
                .append(record.id().toString(), 0, 8)
                .append(" §7")
                .append(TIME_FORMAT.format(Instant.ofEpochMilli(record.timestamp())))
                .append(" §f")
                .append(record.reason().displayName())
                .append(" §8- ")
                .append(amount);

        if (other != null && other.name() != null && !other.name().isBlank()) {
            line.append(" §8[").append(prettyType(other)).append("]");
        }

        if (party != null && party.balanceBefore() != null && party.balanceAfter() != null) {
            line.append(" §8(")
                    .append(party.balanceBefore())
                    .append("→")
                    .append(party.balanceAfter())
                    .append(")");
        }

        if (viewer.hasPermission(Perms.TRANSACTIONS_OTHERS)
                && record.actorName() != null
                && !record.actorName().isBlank()
                && !"System".equalsIgnoreCase(record.actorName())) {
            line.append(" §8by ").append(record.actorName());
        }
        return line.toString();
    }

    private static String prettyType(TransactionParty party) {
        return switch (party.type()) {
            case PLAYER -> party.name();
            case GUILD -> "Guild: " + party.name();
            case SERVER_BANK -> "Server Bank";
            case CHEQUE -> "Cheque";
            case SYSTEM -> party.name().isBlank() ? "System" : party.name();
        };
    }

    private static int parsePageOrError(CommandSender sender, String raw) {
        if (!isPositiveInteger(raw)) {
            sender.sendMessage(Main.prefix + "Page must be a positive whole number.");
            return -1;
        }
        return parsePage(raw);
    }

    private static int parsePage(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static boolean isPositiveInteger(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            return Integer.parseInt(raw) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission(Perms.TRANSACTIONS_OTHERS)) {
            return CommandUtils.complete(args[0], CommandUtils.onlinePlayerNames(null));
        }
        return List.of();
    }

    private record Target(UUID uuid, String name, int page) {
    }
}
