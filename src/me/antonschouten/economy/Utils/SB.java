package me.antonschouten.economy.Utils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Data.ConfigData;
import me.antonschouten.economy.Manager.EconomyManager;
import me.antonschouten.economy.Manager.GuildManager;
import me.antonschouten.economy.Manager.JobManager;

public final class SB {

    private static final String OBJECTIVE_NAME = "economypp";
    private static final String TITLE = "§8[§6Economy++§8]";

    private static final long UPDATE_INTERVAL_TICKS = 40L;

    private static final int LINE_COUNT = 12;

    private static final String[] ENTRIES = {
            ChatColor.BLACK.toString(),
            ChatColor.DARK_BLUE.toString(),
            ChatColor.DARK_GREEN.toString(),
            ChatColor.DARK_AQUA.toString(),
            ChatColor.DARK_RED.toString(),
            ChatColor.DARK_PURPLE.toString(),
            ChatColor.GOLD.toString(),
            ChatColor.GRAY.toString(),
            ChatColor.DARK_GRAY.toString(),
            ChatColor.BLUE.toString(),
            ChatColor.GREEN.toString(),
            ChatColor.AQUA.toString()
    };

    private static final Map<UUID, BoardState> BOARDS = new HashMap<>();

    private static BukkitTask updateTask;

    private SB() {
        // Utility class.
    }

    public static void SendScoreBoard(Player player) {
        updateScoreboard(player);
    }

    public static void updateScoreboard(Player player) {
        if (player == null) {
            return;
        }

        if (!Bukkit.isPrimaryThread()) {
            Main plugin = Main.getInstance();
            if (plugin != null && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> updateScoreboard(player));
            }
            return;
        }

        if (!player.isOnline()) {
            BOARDS.remove(player.getUniqueId());
            return;
        }

        ensureUpdaterRunning();

        if (!isEnabled()) {
            removeScoreboard(player);
            return;
        }

        BoardState state = BOARDS.computeIfAbsent(player.getUniqueId(), ignored -> createBoard());
        if (state == null) {
            BOARDS.remove(player.getUniqueId());
            return;
        }

        if (player.getScoreboard() != state.scoreboard()) {
            player.setScoreboard(state.scoreboard());
        }

        updateDynamicLines(player, state);
    }

    public static void refreshAll() {
        if (!Bukkit.isPrimaryThread()) {
            Main plugin = Main.getInstance();
            if (plugin != null && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, SB::refreshAll);
            }
            return;
        }

        ensureUpdaterRunning();
        Bukkit.getOnlinePlayers().forEach(SB::updateScoreboard);
    }

    public static void removeScoreboard(Player player) {
        if (player == null) {
            return;
        }

        if (!Bukkit.isPrimaryThread()) {
            Main plugin = Main.getInstance();
            if (plugin != null && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> removeScoreboard(player));
            }
            return;
        }

        BoardState state = BOARDS.remove(player.getUniqueId());
        if (state == null || player.getScoreboard() != state.scoreboard()) {
            return;
        }

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }

    public static void shutdown() {
        if (!Bukkit.isPrimaryThread()) {
            Main plugin = Main.getInstance();
            if (plugin != null && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, SB::shutdown);
            }
            return;
        }

        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                BoardState state = BOARDS.get(player.getUniqueId());
                if (state != null && player.getScoreboard() == state.scoreboard()) {
                    player.setScoreboard(manager.getMainScoreboard());
                }
            }
        }

        BOARDS.clear();
    }

    private static void ensureUpdaterRunning() {
        if (updateTask != null && !updateTask.isCancelled()) {
            return;
        }

        Main plugin = Main.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }

        updateTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                SB::tick,
                UPDATE_INTERVAL_TICKS,
                UPDATE_INTERVAL_TICKS
        );
    }

    private static void tick() {
        cleanupOfflinePlayers();

        if (!isEnabled()) {
            removeAllEconomyScoreboards();
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            BoardState state = BOARDS.get(player.getUniqueId());

            if (state == null) {
                state = createBoard();
                if (state == null) {
                    continue;
                }

                BOARDS.put(player.getUniqueId(), state);
                player.setScoreboard(state.scoreboard());
            } else if (player.getScoreboard() != state.scoreboard()) {
                continue;
            }

            updateDynamicLines(player, state);
        }
    }

    private static BoardState createBoard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            Main plugin = Main.getInstance();
            if (plugin != null) {
                plugin.getLogger().warning("Could not create Economy++ scoreboard: ScoreboardManager is unavailable.");
            }
            return null;
        }

        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective(
                OBJECTIVE_NAME,
                Criteria.DUMMY,
                TITLE
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        Team[] teams = new Team[LINE_COUNT];

        for (int index = 0; index < LINE_COUNT; index++) {
            Team team = board.registerNewTeam(String.format("eco_line_%02d", index));
            String entry = ENTRIES[index];

            team.addEntry(entry);
            objective.getScore(entry).setScore(LINE_COUNT - 1 - index);
            teams[index] = team;
        }

        BoardState state = new BoardState(board, teams);
        setStaticLines(state);
        return state;
    }

    private static void setStaticLines(BoardState state) {
        setLine(state, 0, "§f");
        setLine(state, 1, "§f💰 Balance:");
        setLine(state, 3, "§f ");
        setLine(state, 4, "§f🔨 Job:");
        setLine(state, 6, "§f  ");
        setLine(state, 7, "§f💰 Serverbank Balance:");
        setLine(state, 9, "§f   ");
        setLine(state, 10, "§f⛊ Guild:");
    }

    private static void updateDynamicLines(Player player, BoardState state) {
        int balance = EconomyManager.getBal(player);
        int serverBankBalance = EconomyManager.getServerbankBalance();

        String job = JobManager.getPlayerJob(player);
        if (job == null || job.isBlank()) {
            job = "Unemployed";
        }

        String guild = GuildManager.getPlayerGuild(player.getName());
        if (guild == null || guild.isBlank()) {
            guild = "§c❌";
        }

        setLine(state, 2, " §a$" + balance);
        setLine(state, 5, " §a" + job);
        setLine(state, 8, " §a$" + serverBankBalance);
        setLine(state, 11, " §a" + guild);
    }

    private static void setLine(BoardState state, int index, String text) {
        if (index < 0 || index >= state.teams().length) {
            return;
        }

        String safeText = text == null ? "" : text;
        Team team = state.teams()[index];
        
        if (!team.getPrefix().equals(safeText)) {
            team.setPrefix(safeText);
        }
    }

    private static void cleanupOfflinePlayers() {
        Iterator<Map.Entry<UUID, BoardState>> iterator = BOARDS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, BoardState> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                iterator.remove();
            }
        }
    }

    private static void removeAllEconomyScoreboards() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();

        if (manager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                BoardState state = BOARDS.get(player.getUniqueId());
                if (state != null && player.getScoreboard() == state.scoreboard()) {
                    player.setScoreboard(manager.getMainScoreboard());
                }
            }
        }

        BOARDS.clear();
    }

    private static boolean isEnabled() {
        return ConfigData.getInstance().getData().getBoolean("Settings.ScoreboardEnabled");
    }

    private record BoardState(Scoreboard scoreboard, Team[] teams) {
    }
}
