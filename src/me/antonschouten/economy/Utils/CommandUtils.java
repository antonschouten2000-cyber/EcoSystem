package me.antonschouten.economy.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.antonschouten.economy.Main;

public final class CommandUtils {

    private static final List<String> MATERIAL_NAMES = Arrays.stream(Material.values())
            .filter(Material::isItem)
            .map(material -> material.name().toLowerCase(Locale.ROOT))
            .sorted()
            .toList();

    private CommandUtils() {
    }

    public static Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }

        sender.sendMessage(Main.prefix + "This command can only be used by a player.");
        return null;
    }

    public static boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }

        sender.sendMessage(Main.perms);
        return false;
    }

    public static Integer parsePositiveInt(CommandSender sender, String value, String description) {
        Integer parsed = parseInt(sender, value, description);
        if (parsed == null) {
            return null;
        }

        if (parsed <= 0) {
            sender.sendMessage(Main.prefix + description + " must be greater than 0.");
            return null;
        }

        return parsed;
    }

    public static Integer parseNonNegativeInt(CommandSender sender, String value, String description) {
        Integer parsed = parseInt(sender, value, description);
        if (parsed == null) {
            return null;
        }

        if (parsed < 0) {
            sender.sendMessage(Main.prefix + description + " cannot be negative.");
            return null;
        }

        return parsed;
    }

    private static Integer parseInt(CommandSender sender, String value, String description) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            sender.sendMessage(Main.prefix + "§a" + value + " §fis not a valid " + description.toLowerCase(Locale.ROOT) + ".");
            return null;
        }
    }

    public static OfflinePlayer findKnownPlayer(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        Player exactOnline = Bukkit.getPlayerExact(input);
        if (exactOnline != null) {
            return exactOnline;
        }

        try {
            UUID uuid = UUID.fromString(input);
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            if (player.hasPlayedBefore() || player.isOnline()) {
                return player;
            }
        } catch (IllegalArgumentException ignored) {
            // Input is a player name, not a UUID.
        }

        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            String name = offlinePlayer.getName();
            if (name != null && name.equalsIgnoreCase(input)) {
                return offlinePlayer;
            }
        }

        return null;
    }

    public static String playerName(OfflinePlayer player) {
        if (player == null) {
            return "unknown";
        }

        return player.getName() != null ? player.getName() : player.getUniqueId().toString();
    }

    public static void notifyIfOnline(OfflinePlayer player, String message) {
        if (player != null && player.isOnline() && player.getPlayer() != null) {
            player.getPlayer().sendMessage(message);
        }
    }

    public static boolean isSafeIdentifier(String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            return false;
        }

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-') {
                return false;
            }
        }

        return true;
    }

    public static List<String> complete(String input, Collection<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(candidate -> candidate != null && candidate.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    public static List<String> onlinePlayerNames(Player exclude) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (exclude == null || !player.getUniqueId().equals(exclude.getUniqueId())) {
                names.add(player.getName());
            }
        }
        return names;
    }

    public static List<String> materialNames() {
        return MATERIAL_NAMES;
    }

    public static boolean canFit(Player target, ItemStack source, int amount) {
        if (target == null || source == null || source.getType().isAir() || amount <= 0) {
            return false;
        }

        int remaining = amount;
        int maxStackSize = source.getMaxStackSize();

        for (ItemStack slot : target.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                remaining -= maxStackSize;
            } else if (slot.isSimilar(source)) {
                remaining -= Math.max(0, maxStackSize - slot.getAmount());
            }

            if (remaining <= 0) {
                return true;
            }
        }

        return false;
    }
}
