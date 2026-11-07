package me.antonschouten.economy.Listeners.ATM.Cheque;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.antonschouten.economy.Main;

final class ChequeSecurity {

    private static final String DISPLAY_NAME = "§bCheque";

    private ChequeSecurity() {
    }

    static ItemStack create(int amount) {
        ItemStack cheque = new ItemStack(Material.PAPER);
        ItemMeta meta = cheque.getItemMeta();
        if (meta == null) {
            return cheque;
        }

        meta.setDisplayName(DISPLAY_NAME);
        meta.setLore(List.of(
                "§f► §3Worth: §a$" + amount,
                "§8Right-click to deposit this cheque."
        ));
        meta.getPersistentDataContainer().set(valueKey(), PersistentDataType.INTEGER, amount);
        cheque.setItemMeta(meta);
        return cheque;
    }

    static Integer readValue(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || !DISPLAY_NAME.equals(meta.getDisplayName())) {
            return null;
        }

        Integer stored = meta.getPersistentDataContainer().get(valueKey(), PersistentDataType.INTEGER);
        if (stored != null && stored > 0) {
            return stored;
        }

        // Backwards compatibility for cheques created by older plugin versions.
        if (meta.hasLore() && meta.getLore() != null) {
            for (String line : meta.getLore()) {
                String plain = ChatColor.stripColor(line);
                if (plain == null) {
                    continue;
                }
                int index = plain.toLowerCase().indexOf("worth:");
                if (index < 0) {
                    continue;
                }
                String number = plain.substring(index + 6).replace("$", "").trim();
                try {
                    int parsed = Integer.parseInt(number);
                    return parsed > 0 ? parsed : null;
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static NamespacedKey valueKey() {
        return new NamespacedKey(Main.getInstance(), "cheque_value");
    }
}
