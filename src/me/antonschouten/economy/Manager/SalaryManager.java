package me.antonschouten.economy.Manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Transaction.TransactionReason;
import me.antonschouten.economy.Data.ConfigData;

public final class SalaryManager {

    private static final ConfigData DATA = ConfigData.getInstance();
    private static final long TICKS_PER_HOUR = 72_000L;

    private static BukkitTask salaryTask;

    private SalaryManager() {
    }

    public static void GetSalaryInfo(Player player) {
        if (player == null) {
            return;
        }
        player.sendMessage(Main.prefix + "The current salary is: §a$" + getSalaryAmount() + "§f.");
        player.sendMessage(Main.prefix + "You get your salary every §a" + getPayHours() + " §fhour(s).");
    }

    public static int getSalaryAmount() {
        return Math.max(0, DATA.getData().getInt("Settings.Salary.Amount", 50));
    }

    /** Compatibility method retaining the old public API name. */
    public static int GetPayHours() {
        return getPayHours();
    }

    public static int getPayHours() {
        return Math.max(1, DATA.getData().getInt("Settings.Salary.PayPerEveryHours", 1));
    }

    public static void PaySalary() {
        stop();

        Main plugin = Main.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }
        if (!DATA.getData().getBoolean("Settings.Salary.Enabled", true)) {
            return;
        }

        long period = safePeriodTicks(getPayHours());
        salaryTask = Bukkit.getScheduler().runTaskTimer(plugin, SalaryManager::payOnlinePlayers, period, period);
    }

    public static void restart() {
        PaySalary();
    }

    public static void stop() {
        if (salaryTask != null) {
            salaryTask.cancel();
            salaryTask = null;
        }
    }

    private static void payOnlinePlayers() {
        int amount = getSalaryAmount();
        if (amount <= 0) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                EconomyManager.addBal(player, amount, TransactionReason.SALARY, null, "scheduled-salary");
                player.sendMessage(Main.prefix + "You got your salary! (§a$" + amount + "§f)");
            } catch (ArithmeticException exception) {
                if (Main.getInstance() != null) {
                    Main.getInstance().getLogger().warning(
                            "Could not pay salary to " + player.getName() + ": balance would overflow."
                    );
                }
            }
        }
    }

    private static long safePeriodTicks(int hours) {
        try {
            return Math.multiplyExact(TICKS_PER_HOUR, (long) Math.max(1, hours));
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }
}
