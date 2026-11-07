package me.antonschouten.economy.Commands.Jobs;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.JobManager;
import me.antonschouten.economy.Manager.JobProgressionManager;
import me.antonschouten.economy.Utils.CommandUtils;

public final class JobsMainCMD implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length != 0) {
            player.sendMessage(Main.prefix + "Usage: §a/" + label + "§f.");
            return true;
        }

        String currentJob = JobManager.getPlayerJob(player);
        if (currentJob == null || currentJob.isBlank() || currentJob.equalsIgnoreCase("Unemployed")) {
            JobManager.OpenJobsScreen(player);
            return true;
        }

        int level = JobProgressionManager.getLevel(player, currentJob);
        player.sendMessage(Main.prefix + "Your current job is §a" + currentJob + " §f(level §a" + level + "§f).");
        player.sendMessage(Main.prefix + "Use §a/job progress §fto view your XP and job earnings.");
        player.sendMessage(Main.prefix + "You can change your job for §a$" + JobManager.getChangeJobPrice()
                + "§f with §a/job change§f.");
        return true;
    }
}
