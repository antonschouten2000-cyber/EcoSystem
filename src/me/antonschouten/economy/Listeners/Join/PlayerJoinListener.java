
package me.antonschouten.economy.Listeners.Join;

import java.util.concurrent.TimeUnit;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Data.ConfigData;
import me.antonschouten.economy.Data.PlayerData;
import me.antonschouten.economy.Manager.DataSaveManager;
import me.antonschouten.economy.Manager.EconomyManager;
import me.antonschouten.economy.Transaction.TransactionReason;
import me.antonschouten.economy.Updater.Updater;
import me.antonschouten.economy.Utils.Perms;
import me.antonschouten.economy.Utils.SB;

public final class PlayerJoinListener implements Listener {

    private static final String JOIN_REWARD_PATH = "JoinReward.LastClaim";

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ConfigData config = ConfigData.getInstance();

        // Existing donation messages.
        player.sendMessage(Main.prefix
                + "If you like our plugin and want to support us, donate:");
        player.sendMessage(Main.prefix
                + "PayPal: §ahttps://www.paypal.me/economyplusplus");

        // Join reward with a persistent per-player cooldown.
        if (config.getData().getBoolean("Settings.Join.Enabled")) {
            int amount = Math.max(
                    0,
                    config.getData().getInt("Settings.Join.moneyOnJoin")
            );

            if (amount > 0) {
                giveJoinRewardIfAvailable(player, amount, config.getData());
            }
        }

        // Existing scoreboard update.
        SB.updateScoreboard(player);

        // Existing update checker.
        if (player.hasPermission(Perms.updateMessage)) {
            Updater.updateCheckerPlayer(player);
        }
    }

    private void giveJoinRewardIfAvailable(
            Player player,
            int amount,
            FileConfiguration config
    ) {
        int cooldownHours = Math.max(
                1,
                config.getInt("Settings.Join.CooldownHours", 24)
        );

        long cooldownMillis = TimeUnit.HOURS.toMillis(cooldownHours);
        long now = System.currentTimeMillis();

        FileConfiguration playerData = PlayerData.getInstance().getData();

        String path = "Players."
                + player.getUniqueId()
                + "."
                + JOIN_REWARD_PATH;

        long lastClaim = playerData.getLong(path, 0L);

        // Prevent another reward until the cooldown has expired.
        if (lastClaim > 0L && now - lastClaim < cooldownMillis) {
            return;
        }

        // Preserve Economy++'s normal transaction handling.
        EconomyManager.addBal(
                player,
                amount,
                TransactionReason.JOIN_REWARD,
                null,
                "join-reward"
        );

        // Persist the claim timestamp in PlayerData.yml.
        playerData.set(path, now);
        DataSaveManager.markPlayerDataDirty();

        player.sendMessage(
                Main.prefix
                + "§a$" + amount
                + " §fhas been added to your account."
        );
    }
}
