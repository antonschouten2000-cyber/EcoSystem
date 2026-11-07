package me.antonschouten.economy.Listeners.ATM.Cheque;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.EcoSystem.EcoSystem;
import me.antonschouten.economy.Listeners.ListenerUtils;
import me.antonschouten.economy.Manager.EconomyManager;
import me.antonschouten.economy.Transaction.TransactionReason;

public final class CreateChequeListener implements Listener {

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player source = event.getPlayer();
        boolean awaiting;
        synchronized (EcoSystem.createCheque) {
            awaiting = EcoSystem.createCheque.contains(source.getUniqueId());
        }
        if (!awaiting) {
            return;
        }

        event.setCancelled(true);
        String input = event.getMessage().trim();

        Bukkit.getScheduler().runTask(Main.getInstance(), () -> handleInput(source, input));
    }

    private static void handleInput(Player player, String input) {
        if (!player.isOnline()) {
            clear(player);
            return;
        }

        if (input.equalsIgnoreCase("cancel")) {
            clear(player);
            player.sendMessage(Main.prefix + "Cheque creation cancelled.");
            return;
        }

        final int amount;
        try {
            amount = Integer.parseInt(input);
        } catch (NumberFormatException exception) {
            player.sendMessage(Main.prefix + "§a" + input + " §fis not a valid positive amount. Type a number or §acancel§f.");
            return;
        }

        if (amount <= 0) {
            player.sendMessage(Main.prefix + "The cheque amount must be greater than 0.");
            return;
        }
        if (!EconomyManager.tryWithdraw(player, amount, TransactionReason.CHEQUE_CREATE, player, "cheque=$" + amount)) {
            clear(player);
            player.sendMessage(Main.prefix + "You don't have enough money to create this cheque.");
            return;
        }

        ItemStack cheque = ChequeSecurity.create(amount);
        ListenerUtils.addOrDrop(player, cheque);
        clear(player);
        player.sendMessage(Main.prefix + "The cheque has been created with a value of §a$" + amount + "§f.");
    }

    private static void clear(Player player) {
        synchronized (EcoSystem.createCheque) {
            EcoSystem.createCheque.remove(player.getUniqueId());
        }
    }
}
