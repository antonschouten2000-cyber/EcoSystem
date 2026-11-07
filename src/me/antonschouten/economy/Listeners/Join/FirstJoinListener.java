package me.antonschouten.economy.Listeners.Join;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Data.ConfigData;
import me.antonschouten.economy.Manager.EconomyManager;
import me.antonschouten.economy.Transaction.TransactionReason;

public final class FirstJoinListener implements Listener {

    @EventHandler
    public void onFirstJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPlayedBefore()) {
            return;
        }

        ConfigData config = ConfigData.getInstance();
        if (!config.getData().getBoolean("Settings.Join.Enabled")) {
            return;
        }

        int amount = Math.max(0, config.getData().getInt("Settings.Join.moneyOnFirstJoin"));
        if (amount <= 0) {
            return;
        }

        EconomyManager.addBal(player, amount, TransactionReason.FIRST_JOIN_REWARD, null, "first-join");
        player.sendMessage(Main.prefix + "§a$" + amount + " §fhas been added to your account for joining for the first time.");
    }
}
