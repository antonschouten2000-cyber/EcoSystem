package me.antonschouten.economy.Manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Data.GuildData;

public final class GuildManager {

    private static final GuildData DATA = GuildData.getInstance();
    private static final long INVITE_LIFETIME_MILLIS = 2L * 60L * 1000L;
    private static final Map<String, GuildInvite> INVITES = new HashMap<>();

    private GuildManager() {
    }

    public static void guildHelpMessage(Player player) {
        player.sendMessage("");
        player.sendMessage("§8-------------- §6[Economy++ Guilds] §8--------------");
        player.sendMessage("§a/guild help §8- §fShow this help message.");
        player.sendMessage("§a/guild create <name> §8- §fCreate a guild.");
        player.sendMessage("§a/guild delete §8- §fDelete a guild.");
        player.sendMessage("§a/guild invite <player> §8- §fInvite a player to your guild.");
        player.sendMessage("§a/guild accept §8- §fAccept a pending guild invite.");
        player.sendMessage("§a/guild kick <player> §8- §fKick a player from your guild.");
        player.sendMessage("§a/guild info <name> §8- §fShow guild info.");
        player.sendMessage("§a/g <message> §8- §fTalk in guild chat.");
        player.sendMessage("§a/guild balance §8- §fShow your guild balance.");
        player.sendMessage("§a/guild deposit <amount> §8- §fDeposit money into your guild.");
        player.sendMessage("§a/guild withdraw <amount> §8- §fWithdraw money from your guild.");
        player.sendMessage("§8-------------- §6[Economy++ Guilds] §8--------------");
    }

    public static void createGuild(String guild, String leaderName) {
        if (!isSafeGuildName(guild) || !valid(leaderName) || guildExists(guild) || playerHasGuild(leaderName)) {
            return;
        }

        DATA.getData().set("Guild." + guild + ".Leader", leaderName);
        DATA.getData().set("Guild." + guild + ".Members", new ArrayList<String>());
        DATA.getData().set("Guild." + guild + ".Balance", 0);
        DataSaveManager.markGuildDataDirty();
    }

    public static boolean deleteGuild(String playerName) {
        String guildName = getGuilNamedByLeader(playerName);
        if (guildName == null) {
            return false;
        }

        DATA.getData().set("Guild." + guildName, null);
        INVITES.entrySet().removeIf(entry -> entry.getValue().guildName().equalsIgnoreCase(guildName));
        DataSaveManager.markGuildDataDirty();
        return true;
    }

    public static boolean invitePlayer(String guildName, String targetPlayerName) {
        String actualGuild = findGuildName(guildName);
        if (actualGuild == null || !valid(targetPlayerName) || playerHasGuild(targetPlayerName)) {
            return false;
        }

        purgeExpiredInvites();
        String targetKey = normalize(targetPlayerName);
        if (INVITES.containsKey(targetKey)) {
            return false;
        }

        INVITES.put(targetKey, new GuildInvite(actualGuild, System.currentTimeMillis() + INVITE_LIFETIME_MILLIS));
        return true;
    }

    public static boolean acceptGuildInvite(String playerName) {
        if (!valid(playerName) || playerHasGuild(playerName)) {
            return false;
        }

        purgeExpiredInvites();
        GuildInvite invite = INVITES.remove(normalize(playerName));
        if (invite == null) {
            return false;
        }

        String guild = findGuildName(invite.guildName());
        if (guild == null) {
            return false;
        }

        List<String> members = new ArrayList<>(getGuildMembers(guild));
        if (containsIgnoreCase(members, playerName)) {
            return false;
        }

        members.add(playerName);
        DATA.getData().set("Guild." + guild + ".Members", members);
        DataSaveManager.markGuildDataDirty();
        return true;
    }

    public static boolean kickMember(String leaderName, String targetName) {
        String guild = getGuilNamedByLeader(leaderName);
        if (guild == null || !valid(targetName) || targetName.equalsIgnoreCase(leaderName)) {
            return false;
        }

        List<String> members = new ArrayList<>(getGuildMembers(guild));
        boolean removed = members.removeIf(member -> member.equalsIgnoreCase(targetName));
        if (!removed) {
            return false;
        }

        DATA.getData().set("Guild." + guild + ".Members", members);
        DataSaveManager.markGuildDataDirty();
        return true;
    }

    public static void kickPlayerFromGuild(String guild, Player target) {
        if (target == null) {
            return;
        }
        String actualGuild = findGuildName(guild);
        if (actualGuild == null) {
            return;
        }

        List<String> members = new ArrayList<>(getGuildMembers(actualGuild));
        if (members.removeIf(member -> member.equalsIgnoreCase(target.getName()))) {
            DATA.getData().set("Guild." + actualGuild + ".Members", members);
            DataSaveManager.markGuildDataDirty();
        }
    }

    public static void guildInformation(Player player, String guild) {
        String actualGuild = findGuildName(guild);
        if (player == null || actualGuild == null) {
            return;
        }

        List<String> members = getGuildMembers(actualGuild);
        player.sendMessage(Main.prefix + "Guild Information:");
        player.sendMessage("§fGuild Name: §a" + actualGuild);
        player.sendMessage("§fLeader: §a" + getGuildLeader(actualGuild));
        player.sendMessage("§fMembers: §a" + (members.isEmpty() ? "None" : String.join(", ", members)));
    }

    public static String getGuildLeader(String guild) {
        String actualGuild = findGuildName(guild);
        return actualGuild == null ? null : DATA.getData().getString("Guild." + actualGuild + ".Leader");
    }

    public static List<String> getGuildMembers(String guildName) {
        String actualGuild = findGuildName(guildName);
        if (actualGuild == null) {
            return List.of();
        }
        return List.copyOf(DATA.getData().getStringList("Guild." + actualGuild + ".Members"));
    }

    public static boolean guildExists(String guild) {
        return findGuildName(guild) != null;
    }

    public static boolean isGuildLeader(Player player) {
        return player != null && getGuilNamedByLeader(player.getName()) != null;
    }

    /** Compatibility method retaining the original misspelled public API. */
    public static String getGuilNamedByLeader(String playerName) {
        if (!valid(playerName)) {
            return null;
        }

        ConfigurationSection guilds = guildSection();
        if (guilds == null) {
            return null;
        }

        for (String guildName : guilds.getKeys(false)) {
            String leader = DATA.getData().getString("Guild." + guildName + ".Leader");
            if (playerName.equalsIgnoreCase(leader)) {
                return guildName;
            }
        }
        return null;
    }

    public static String getGuildNamedByLeader(String playerName) {
        return getGuilNamedByLeader(playerName);
    }

    public static boolean playerHasGuild(String playerName) {
        return getPlayerGuild(playerName) != null;
    }

    public static String getPlayerGuild(String playerName) {
        if (!valid(playerName)) {
            return null;
        }

        ConfigurationSection guilds = guildSection();
        if (guilds == null) {
            return null;
        }

        for (String guildName : guilds.getKeys(false)) {
            String leader = DATA.getData().getString("Guild." + guildName + ".Leader");
            if (playerName.equalsIgnoreCase(leader)) {
                return guildName;
            }

            if (containsIgnoreCase(DATA.getData().getStringList("Guild." + guildName + ".Members"), playerName)) {
                return guildName;
            }
        }
        return null;
    }

    public static void sendGuildMessage(String guildName, String message) {
        String actualGuild = findGuildName(guildName);
        if (actualGuild == null || message == null || message.isBlank()) {
            return;
        }

        Set<String> recipients = new LinkedHashSet<>();
        String leader = getGuildLeader(actualGuild);
        if (leader != null) {
            recipients.add(leader);
        }
        recipients.addAll(getGuildMembers(actualGuild));

        for (String name : recipients) {
            Player player = Bukkit.getPlayerExact(name);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    public static List<String> getGuildNames() {
        ConfigurationSection guilds = guildSection();
        if (guilds == null) {
            return List.of();
        }
        return List.copyOf(guilds.getKeys(false));
    }

    private static String findGuildName(String input) {
        if (!valid(input)) {
            return null;
        }

        ConfigurationSection guilds = guildSection();
        if (guilds == null) {
            return null;
        }

        for (String guildName : guilds.getKeys(false)) {
            if (guildName.equalsIgnoreCase(input)) {
                return guildName;
            }
        }
        return null;
    }

    private static ConfigurationSection guildSection() {
        return DATA.getData().getConfigurationSection("Guild");
    }

    private static void purgeExpiredInvites() {
        long now = System.currentTimeMillis();
        INVITES.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    private static boolean containsIgnoreCase(List<String> values, String needle) {
        for (String value : values) {
            if (value.equalsIgnoreCase(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean valid(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isSafeGuildName(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,24}");
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private record GuildInvite(String guildName, long expiresAt) {
    }
}
