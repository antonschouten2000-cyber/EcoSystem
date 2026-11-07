package me.antonschouten.economy.EcoSystem;

import java.util.ArrayList;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Data.ConfigData;
import me.antonschouten.economy.Data.PlayerData;

public final class EcoSystem {

    /** Backwards-compatible singleton reference for older Economy++ code. */
    public static final PlayerData pData = PlayerData.getInstance();

    public static final ArrayList<UUID> adminpanelPassword = new ArrayList<>();
    public static final ArrayList<UUID> createCheque = new ArrayList<>();

    /** Kept for backwards compatibility with older code that may reference it. */
    public static Plugin plugin;

    private EcoSystem() {
    }

    public static String getAdminPanelPassword() {
        return ConfigData.getInstance().getData().getString("AdminPanelPassword", "");
    }

    public static void SendHelpMessage(Player player) {
        if (player == null) {
            return;
        }

        player.sendMessage("");
        player.sendMessage("§8-------------- §6[Economy++ Help] §8--------------");
        player.sendMessage("§a§lEconomy++ §8- §fEconomy, jobs and server core features.");
        player.sendMessage("§a/eco help §8- §fShow this help message.");
        player.sendMessage("§a/bal [player] §8- §fView a balance.");
        player.sendMessage("§a/pay <player> <amount> §8- §fPay another player.");
        player.sendMessage("§a/transactions [player] [page] §8- §fView transaction history.");
        player.sendMessage("§a/jobs §8- §fOpen the Jobs menu.");
        player.sendMessage("§a/job progress §8- §fView Job level and XP.");
        player.sendMessage("§a/home <home> §8- §fTeleport to one of your homes.");
        player.sendMessage("§a/spawn §8- §fTeleport to the server spawn.");
        player.sendMessage("§a/warp <name> §8- §fTeleport to a warp.");
        player.sendMessage("§a/warps §8- §fList all warps.");
        player.sendMessage("§a/tpa <player> §8- §fRequest to teleport to a player.");
        player.sendMessage("§a/tpahere <player> §8- §fAsk a player to teleport to you.");
        player.sendMessage("§a/tpaccept [player] §8- §fAccept a teleport request.");
        player.sendMessage("§a/tpdeny [player] §8- §fDeny a teleport request.");
        player.sendMessage("§a/back §8- §fReturn to your previous location.");
        player.sendMessage("§a/guild help §8- §fView Guild commands.");
        player.sendMessage("§a/chunk info §8- §fView Chunk Claim information.");
        player.sendMessage("§a/shop §8- §fOpen the server shop.");
        player.sendMessage("§a/tradenpc §8- §fTrade NPC administration.");
        player.sendMessage("§a/eco info §8- §fView plugin information.");
        player.sendMessage("§a/eco update §8- §fCheck for a new Economy++ release.");
        player.sendMessage("§8-------------- §6[Economy++ Help] §8--------------");
    }

    public static void DebugPlugin(Player player) {
        if (player == null) {
            return;
        }

        Main pluginInstance = Main.getInstance();
        player.sendMessage("§4Try to get plugin details:");
        player.sendMessage("§fCurrently installed version: §a" + getVersion());
        player.sendMessage("§fPlugin ID: §a" + getPluginID());
        player.sendMessage("§fWebsite: §a" + getWebsite());
        if (pluginInstance != null) {
            player.sendMessage("§fPlugin name: §a" + pluginInstance.getName());
        }
    }

    public static String getWebsite() {
        return "https://economyplusplus.jimdosite.com/";
    }

    public static int getPluginID() {
        return 54958;
    }

    public static String getVersion() {
        Main pluginInstance = Main.getInstance();
        return pluginInstance != null
                ? pluginInstance.getDescription().getVersion()
                : "unknown";
    }
}
