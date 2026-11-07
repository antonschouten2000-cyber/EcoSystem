package me.antonschouten.economy.Commands.Jobs;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.EconomyManager;
import me.antonschouten.economy.Transaction.TransactionReason;
import me.antonschouten.economy.Manager.JobManager;
import me.antonschouten.economy.Manager.JobProgressionManager;
import me.antonschouten.economy.Utils.CommandUtils;
import me.antonschouten.economy.Utils.Perms;

public final class JobCMD implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("help", "change", "booster", "progress", "level", "stats", "info", "delete");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            JobManager.HelpJobMessage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "change" -> handleChange(player, args);
            case "booster" -> handleBooster(player, args);
            case "progress", "level" -> handleProgress(player, args);
            case "stats" -> handleStats(player, args);
            case "info" -> handleInfo(player, args);
            case "delete" -> handleDelete(player, args);
            default -> {
                player.sendMessage(Main.prefix + "Unknown subcommand. Use §a/job help §ffor help.");
                return true;
            }
        }

        return true;
    }

    private void handleChange(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(Main.prefix + "Usage: §a/job change§f.");
            return;
        }

        String currentJob = JobManager.getPlayerJob(player);
        if (currentJob == null || currentJob.isBlank() || currentJob.equalsIgnoreCase("Unemployed")) {
            player.sendMessage(Main.prefix + "You don't currently have a job. Use §a/jobs §fto choose one.");
            return;
        }

        int changePrice = JobManager.getChangeJobPrice();
        if (changePrice > 0 && !EconomyManager.tryWithdraw(player, changePrice, TransactionReason.JOB_CHANGE, player, "job=" + currentJob)) {
            player.sendMessage(Main.prefix + "You need §a$" + changePrice + " §fto change your job.");
            return;
        }

        JobManager.leaveCurrentJob(player);
        player.sendMessage(Main.prefix + "You left your current job. Your stats and level were preserved.");
        player.sendMessage(Main.prefix + "Use §a/jobs §fto choose your next job.");
    }

    private void handleBooster(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(Main.prefix + "Usage: §a/job booster§f.");
            return;
        }

        String currentJob = JobManager.getPlayerJob(player);
        if (currentJob == null || currentJob.isBlank() || currentJob.equalsIgnoreCase("Unemployed")) {
            player.sendMessage(Main.prefix + "You can't buy a booster without a job.");
            player.sendMessage(Main.prefix + "Choose a job first with §a/jobs§f.");
            return;
        }

        JobManager.OpenJobBooster(player);
    }


    private void handleProgress(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(Main.prefix + "Usage: §a/job progress§f.");
            return;
        }
        JobProgressionManager.sendProgress(player);
    }

    private void handleStats(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(Main.prefix + "Usage: §a/job stats§f.");
            return;
        }

        String currentJob = JobManager.getPlayerJob(player);
        if (currentJob == null || currentJob.isBlank() || currentJob.equalsIgnoreCase("Unemployed")) {
            player.sendMessage(Main.prefix + "You need to choose a job first. Use §a/jobs§f.");
            return;
        }

        JobManager.GetJobStats(player);
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(Main.prefix + "Usage: §a/job info§f.");
            return;
        }

        String currentJob = JobManager.getPlayerJob(player);
        if (currentJob == null || currentJob.isBlank() || currentJob.equalsIgnoreCase("Unemployed")) {
            player.sendMessage(Main.prefix + "You need to choose a job first. Use §a/jobs§f.");
            return;
        }

        JobManager.OpenJobInfoScreen(player);
    }

    @SuppressWarnings("deprecation")
	private void handleDelete(Player player, String[] args) {
        if (!CommandUtils.requirePermission(player, Perms.deleteJob)) {
            return;
        }
        if (args.length != 2) {
            player.sendMessage(Main.prefix + "Usage: §a/job delete <player>§f.");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(Main.prefix + "Player §a" + args[1] + " §fis not online.");
            return;
        }

        String currentJob = JobManager.getPlayerJob(target);
        if (currentJob == null || currentJob.isBlank() || currentJob.equalsIgnoreCase("Unemployed")) {
            player.sendMessage(Main.prefix + "§a" + target.getName() + " §fdoesn't have a job.");
            return;
        }

        JobManager.DeletePlayerJob(target);
        player.sendMessage(Main.prefix + "§a" + target.getName() + "'s §fjob has been deleted.");
        target.sendMessage(Main.prefix + "Your job has been removed by an administrator.");
    }

    @SuppressWarnings("deprecation")
	@Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return CommandUtils.complete(args[0], SUBCOMMANDS);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("delete") && sender.hasPermission(Perms.deleteJob)) {
            return CommandUtils.complete(args[1], CommandUtils.onlinePlayerNames(null));
        }
        return List.of();
    }
}
