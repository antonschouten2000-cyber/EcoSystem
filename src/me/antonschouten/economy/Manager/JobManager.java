package me.antonschouten.economy.Manager;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.antonschouten.economy.Data.ConfigData;
import me.antonschouten.economy.Data.PlayerData;

public final class JobManager {

    private static final PlayerData DATA = PlayerData.getInstance();
    private static final ConfigData CONFIG = ConfigData.getInstance();

    /** Thread-safe because reset confirmation currently arrives from async chat. */
    public static final Set<UUID> resetJobs = ConcurrentHashMap.newKeySet();

    private JobManager() {
    }

    public static void OpenJobBooster(Player player) {
        if (player == null) {
            return;
        }

        Inventory booster = Bukkit.createInventory(null, 45, "§a► §fJob booster §a◄");
        booster.setItem(10, item(Material.EXPERIENCE_BOTTLE, "§fx2 coin booster", List.of(
                "§8- §aBuy a x2 coin booster",
                "§8- §aThe booster works for §f1 §ahour.",
                "§8- §aPrice: §f$200")));
        booster.setItem(13, item(Material.EXPERIENCE_BOTTLE, "§fx3 coin booster", List.of(
                "§8- §aBuy a x3 coin booster",
                "§8- §aThe booster works for §f1 §ahour.",
                "§8- §aPrice: §f$600")));
        booster.setItem(16, item(Material.EXPERIENCE_BOTTLE, "§fx4 coin booster", List.of(
                "§8- §aBuy a x4 coin booster",
                "§8- §aThe booster works for §f1 §ahour.",
                "§8- §aPrice: §f$1000")));
        booster.setItem(39, item(Material.EMERALD, "§fYour current booster", List.of(
                "§8- §aClick to see your current booster")));
        booster.setItem(41, item(Material.BARRIER, "§fClose Menu", List.of(
                "§8- §aClick to close the menu.")));
        player.openInventory(booster);
    }

    @SuppressWarnings("deprecation")
    public static void resetAllJobs() {
        if (!Bukkit.isPrimaryThread()) {
            if (me.antonschouten.economy.Main.getInstance() != null
                    && me.antonschouten.economy.Main.getInstance().isEnabled()) {
                Bukkit.getScheduler().runTask(me.antonschouten.economy.Main.getInstance(), JobManager::resetAllJobs);
            }
            return;
        }

        ConfigurationSection players = DATA.getData().getConfigurationSection("Players");
        if (players != null) {
            for (String uuid : players.getKeys(false)) {
                DATA.getData().set("Players." + uuid + ".Job", null);
                DATA.getData().set("Players." + uuid + ".JobProgress", null);
                DATA.getData().set("Players." + uuid + ".Prefix", null);
            }
        }
        DataSaveManager.markPlayerDataDirty();

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendTitle("§c>> §fEcoSystem §c<<", "§aAll jobs have been reset by an admin.");
        }
    }

    public static void HelpJobMessage(Player player) {
        if (player == null) {
            return;
        }
        player.sendMessage("");
        player.sendMessage("§8-------------- §6[Economy++ Jobs] §8--------------");
        player.sendMessage("§a/job help §8- §fPrints this help message.");
        player.sendMessage("§a/job change §8- §fChange your job for §b$" + getChangeJobPrice() + "§f.");
        player.sendMessage("§a/job booster §8- §fOpen the job coin booster menu.");
        player.sendMessage("§a/job progress §8- §fShow your level, XP and earnings.");
        player.sendMessage("§a/job stats §8- §fShow your detailed job statistics.");
        player.sendMessage("§a/job info §8- §fGet information about jobs.");
        player.sendMessage("§a/job delete <player> §8- §fAdmin: reset a player's current job.");
        player.sendMessage("§a/jobs §8- §fOpen the jobs GUI.");
        player.sendMessage("§8-------------- §6[Economy++ Jobs] §8--------------");
    }

    public static void OpenJobsScreen(Player player) {
        if (player == null) {
            return;
        }

        Inventory jobs = Bukkit.createInventory(null, 36, "§fJobs §aChoose a job!");
        jobs.setItem(10, jobSelectionItem(player, Material.WOODEN_HOE, "Farmer",
                "Earn money by harvesting crops."));
        jobs.setItem(13, jobSelectionItem(player, Material.DIAMOND, "Miner",
                "Earn money by mining ores."));
        jobs.setItem(16, jobSelectionItem(player, Material.OAK_LOG, "Woodcutter",
                "Earn money by cutting logs and shearing leaves."));
        jobs.setItem(20, jobSelectionItem(player, Material.ANVIL, "Smith",
                "Earn money by crafting, smithing and smelting."));
        jobs.setItem(22, jobSelectionItem(player, Material.BOW, "Hunter",
                "Earn money by hunting animals."));
        jobs.setItem(24, jobSelectionItem(player, Material.FISHING_ROD, "Fisherman",
                "Earn money by catching fish."));
        player.openInventory(jobs);
    }

    /**
     * Admin reset for the player's currently selected job. Other job histories stay intact.
     */
    public static void DeletePlayerJob(Player target) {
        if (target == null) {
            return;
        }

        String currentJob = JobProgressionManager.canonicalJob(getPlayerJob(target));
        String base = playerBase(target);
        if (currentJob != null) {
            DATA.getData().set(base + ".Job." + currentJob, null);
            DATA.getData().set(base + ".JobProgress." + currentJob, null);
        }
        DATA.getData().set(base + ".Prefix", "Unemployed");
        DataSaveManager.markPlayerDataDirty();
    }

    /**
     * Leaves the active job without deleting its statistics or progression.
     */
    public static void leaveCurrentJob(Player player) {
        if (player == null) {
            return;
        }
        DATA.getData().set(playerBase(player) + ".Prefix", "Unemployed");
        DataSaveManager.markPlayerDataDirty();
    }

    public static int getChangeJobPrice() {
        return Math.max(0, CONFIG.getData().getInt("Jobs.ChangePrice", 1000));
    }

    public static void SetPlayerPrefix(Player player, String job) {
        if (player == null || job == null || job.isBlank()) {
            return;
        }
        DATA.getData().set(playerBase(player) + ".Prefix", job);
        DataSaveManager.markPlayerDataDirty();
    }

    public static void SetJobSmith(Player player) {
        if (player == null) return;
        initializeCounter(player, "Smith.CraftedItems", 0);
        initializeCounter(player, "Smith.CraftedTools", 0);
        initializeCounter(player, "Smith.CraftedArmor", 0);
        initializeCounter(player, "Smith.CookedItems", 0);
        setPrefixInMemory(player, "Smith");
        JobProgressionManager.ensureProgress(player, "Smith");
        DataSaveManager.markPlayerDataDirty();
    }

    public static void SetJobHunter(Player player) {
        if (player == null) return;
        initializeCounter(player, "Hunter.KilledAnimals.Sword", 0);
        initializeCounter(player, "Hunter.KilledAnimals.Bow", 0);
        setPrefixInMemory(player, "Hunter");
        JobProgressionManager.ensureProgress(player, "Hunter");
        DataSaveManager.markPlayerDataDirty();
    }

    public static void SetJobFisherman(Player player) {
        if (player == null) return;
        initializeCounter(player, "Fisherman.CatchedFishes", 0);
        setPrefixInMemory(player, "Fisherman");
        JobProgressionManager.ensureProgress(player, "Fisherman");
        DataSaveManager.markPlayerDataDirty();
    }

    public static void SetJobWoodcutter(Player player) {
        if (player == null) return;
        initializeCounter(player, "Woodcutter.CuttedLogs", 0);
        initializeCounter(player, "Woodcutter.ShearsLeaves", 0);
        setPrefixInMemory(player, "Woodcutter");
        JobProgressionManager.ensureProgress(player, "Woodcutter");
        DataSaveManager.markPlayerDataDirty();
    }

    public static void SetJobMiner(Player player) {
        if (player == null) return;
        String[] counters = {
                "Miner.MinedOres.Coal", "Miner.MinedOres.DeepSlateCoalOre",
                "Miner.MinedOres.Iron", "Miner.MinedOres.DeepSlateIronOre",
                "Miner.MinedOres.Lapis", "Miner.MinedOres.DeepSlateLapisOre",
                "Miner.MinedOres.Redstone", "Miner.MinedOres.DeepSlateRedstoneOre",
                "Miner.MinedOres.Gold", "Miner.MinedOres.DeepSlateGoldOre",
                "Miner.MinedOres.Diamond", "Miner.MinedOres.DeepSlateDiamondOre",
                "Miner.MinedOres.Emerald", "Miner.MinedOres.DeepSlateEmeraldOre",
                "Miner.MinedOres.Quartz", "Miner.MinedOres.DeepSlateNetherGoldOre",
                "Miner.MinedOres.Copper", "Miner.MinedOres.DeepSlateCopperOre"
        };
        for (String counter : counters) {
            initializeCounter(player, counter, 0);
        }
        setPrefixInMemory(player, "Miner");
        JobProgressionManager.ensureProgress(player, "Miner");
        DataSaveManager.markPlayerDataDirty();
    }

    public static void SetJobFarmer(Player player) {
        if (player == null) return;
        String[] counters = {
                "Farmer.Harvest.Wheat", "Farmer.Harvest.Carrots", "Farmer.Harvest.Potatos",
                "Farmer.Harvest.Beetroot", "Farmer.Harvest.Pumpkin", "Farmer.Harvest.Melon",
                "Farmer.Harvest.Torchflower"
        };
        for (String counter : counters) {
            initializeCounter(player, counter, 0);
        }
        setPrefixInMemory(player, "Farmer");
        JobProgressionManager.ensureProgress(player, "Farmer");
        DataSaveManager.markPlayerDataDirty();
    }

    public static void GetJobStats(Player player) {
        if (player == null) {
            return;
        }

        String job = getPlayerJob(player);
        if (job == null || job.isBlank() || job.equalsIgnoreCase("Unemployed")) {
            player.sendMessage("§fYou currently don't have a job.");
            return;
        }

        player.sendMessage("§fJob statistics from §a" + player.getName());
        player.sendMessage("§fJob: §a" + job);
        int level = JobProgressionManager.getLevel(player, job);
        int maxLevel = JobProgressionManager.getMaxLevel();
        int currentXp = JobProgressionManager.getCurrentXp(player, job);
        int nextXp = JobProgressionManager.getXpRequiredForNextLevel(level);
        player.sendMessage("§fLevel: §a" + level + "§7/§a" + maxLevel);
        player.sendMessage(level >= maxLevel
                ? "§fXP: §6MAX LEVEL"
                : "§fXP: §a" + currentXp + "§7/§a" + nextXp);
        player.sendMessage("§fReward bonus: §a+" + JobProgressionManager.getRewardBonusPercent(player, job) + "%");
        player.sendMessage("§fTotal job earnings: §a$" + JobProgressionManager.getTotalEarned(player, job));

        switch (job.toLowerCase()) {
            case "woodcutter" -> {
                player.sendMessage("§fCut logs: §a" + getCuttedLogs(player));
                player.sendMessage("§fLeaves sheared: §a" + getShearsLeaves(player));
            }
            case "miner" -> {
                player.sendMessage("§fMined coal ores: §a" + getMinedCoalOres(player));
                player.sendMessage("§fMined deepslate coal ores: §a" + getMinedDeepSlateCoalOres(player));
                player.sendMessage("§fMined iron ores: §a" + getMinedIronOres(player));
                player.sendMessage("§fMined deepslate iron ores: §a" + getMinedDeepSlateIronOres(player));
                player.sendMessage("§fMined lapis ores: §a" + getMinedLapisOres(player));
                player.sendMessage("§fMined deepslate lapis ores: §a" + getMinedDeepSlateLapisOres(player));
                player.sendMessage("§fMined redstone ores: §a" + getMinedRedstoneOres(player));
                player.sendMessage("§fMined deepslate redstone ores: §a" + getMinedDeepSlateRedstoneOres(player));
                player.sendMessage("§fMined gold ores: §a" + getMinedGoldOres(player));
                player.sendMessage("§fMined deepslate gold ores: §a" + getMinedDeepSlateGoldOres(player));
                player.sendMessage("§fMined diamond ores: §a" + getMinedDiamondOres(player));
                player.sendMessage("§fMined deepslate diamond ores: §a" + getMinedDeepSlateDiamondOres(player));
                player.sendMessage("§fMined emerald ores: §a" + getMinedEmeraldOres(player));
                player.sendMessage("§fMined deepslate emerald ores: §a" + getMinedDeepSlateEmeraldOres(player));
                player.sendMessage("§fMined quartz ores: §a" + getMinedQuartzOres(player));
                player.sendMessage("§fMined nether gold ores: §a" + getMinedDeepSlateNetherGoldOres(player));
                player.sendMessage("§fMined copper ores: §a" + getMinedCopperOres(player));
                player.sendMessage("§fMined deepslate copper ores: §a" + getMinedDeepSlateCopperOres(player));
            }
            case "farmer" -> {
                player.sendMessage("§fHarvested wheat: §a" + getHarvestWheat(player));
                player.sendMessage("§fHarvested carrots: §a" + getHarvestCarrots(player));
                player.sendMessage("§fHarvested potatoes: §a" + getHarvestPotatos(player));
                player.sendMessage("§fHarvested beetroot: §a" + getHarvestBeetroot(player));
                player.sendMessage("§fHarvested pumpkins: §a" + getHarvestPumpkin(player));
                player.sendMessage("§fHarvested melons: §a" + getHarvestMelon(player));
                player.sendMessage("§fHarvested torchflowers: §a" + getHarvestTorchflower(player));
            }
            case "smith" -> {
                player.sendMessage("§fCrafted tools: §a" + getCraftedTools(player));
                player.sendMessage("§fCrafted armor: §a" + getCraftedArmor(player));
                player.sendMessage("§fCooked items: §a" + getCookedItems(player));
            }
            case "fisherman" -> player.sendMessage("§fCaught fish: §a" + getCatchedFishes(player));
            case "hunter" -> {
                player.sendMessage("§fKilled animals by sword: §a" + getKilledAnimalsBySword(player));
                player.sendMessage("§fKilled animals by bow: §a" + getKilledAnimalsByBow(player));
            }
            default -> player.sendMessage("§fNo statistics are available for this job.");
        }
    }

    public static void OpenJobInfoScreen(Player player) {
        if (player == null) {
            return;
        }

        String currentJob = getPlayerJob(player);
        if (currentJob == null || currentJob.isBlank()) {
            currentJob = "Unemployed";
        }

        Inventory inventory = Bukkit.createInventory(null, 45, "§fJobs §aInformation");
        int currentLevel = JobProgressionManager.getLevel(player, currentJob);
        inventory.setItem(19, item(Material.PAPER, "§aYour Job:", List.of(
                "§8- §fYour job is §a" + currentJob,
                "§8- §fCurrent level: §a" + currentLevel,
                "§8- §fClick to view your progression.")));
        inventory.setItem(21, infoItem(Material.WHEAT_SEEDS, "Farmer"));
        inventory.setItem(23, infoItem(Material.DIAMOND, "Miner"));
        inventory.setItem(25, infoItem(Material.OAK_LOG, "Woodcutter"));
        inventory.setItem(15, infoItem(Material.ANVIL, "Smith"));
        inventory.setItem(13, infoItem(Material.FISHING_ROD, "Fisherman"));
        inventory.setItem(11, infoItem(Material.BOW, "Hunter"));
        inventory.setItem(40, item(Material.BARRIER, "§aClose Menu", List.of("§8- §fClick to close the menu.")));
        player.openInventory(inventory);
    }

    public static void getJobHunterInformation(Player p) {
        sendJobInfo(p, "Hunter", "Kill animals with a sword or axe", "Kill animals with a bow");
    }

    public static void getJobFishermanInformation(Player p) {
        sendJobInfo(p, "Fisherman", "Catch fish in oceans and rivers");
    }

    public static void getJobSmithInformation(Player p) {
        sendJobInfo(p, "Smith", "Cook items in furnaces", "Craft armor", "Craft tools");
    }

    public static void getJobFarmerInformation(Player p) {
        sendJobInfo(p, "Farmer", "Harvest wheat", "Harvest carrots", "Harvest potatoes", "Harvest beetroot",
                "Harvest pumpkins", "Harvest melons", "Harvest torchflowers");
    }

    public static void getJobMinerInformation(Player p) {
        sendJobInfo(p, "Miner", "Mine coal ores / deepslate", "Mine iron ores / deepslate",
                "Mine lapis ores / deepslate", "Mine redstone ores / deepslate", "Mine gold ores / deepslate",
                "Mine diamond ores / deepslate", "Mine emerald ores / deepslate", "Mine quartz / nether gold",
                "Mine copper ores / deepslate");
    }

    public static void getJobWoodcutterInformation(Player p) {
        sendJobInfo(p, "Woodcutter", "Cut trees (logs)", "Shear leaves");
    }

    public static String getPlayerJob(Player player) {
        if (player == null) {
            return null;
        }
        return DATA.getData().getString(playerBase(player) + ".Prefix");
    }

    public static void UpdateKilledAnimalsByBow(Player p) { increment(p, "Hunter.KilledAnimals.Bow", 1); }
    public static int getKilledAnimalsByBow(Player p) { return getCounter(p, "Hunter.KilledAnimals.Bow"); }
    public static void UpdateKilledAnimalsBySword(Player p) { increment(p, "Hunter.KilledAnimals.Sword", 1); }
    public static int getKilledAnimalsBySword(Player p) { return getCounter(p, "Hunter.KilledAnimals.Sword"); }
    public static void UpdateCatchedFishes(Player p) { increment(p, "Fisherman.CatchedFishes", 1); }
    public static int getCatchedFishes(Player p) { return getCounter(p, "Fisherman.CatchedFishes"); }
    public static void UpdateCookedItems(Player p, int amount) { increment(p, "Smith.CookedItems", amount); }
    public static int getCookedItems(Player p) { return getCounter(p, "Smith.CookedItems"); }
    public static void UpdateCraftedArmor(Player p) { UpdateCraftedArmor(p, 1); }
    public static void UpdateCraftedArmor(Player p, int amount) { increment(p, "Smith.CraftedArmor", amount); }
    public static int getCraftedArmor(Player p) { return getCounter(p, "Smith.CraftedArmor"); }
    public static void UpdateCraftedTools(Player p) { UpdateCraftedTools(p, 1); }
    public static void UpdateCraftedTools(Player p, int amount) { increment(p, "Smith.CraftedTools", amount); }
    public static int getCraftedTools(Player p) { return getCounter(p, "Smith.CraftedTools"); }
    public static void UpdateCuttedLogs(Player p) { increment(p, "Woodcutter.CuttedLogs", 1); }
    public static int getCuttedLogs(Player p) { return getCounter(p, "Woodcutter.CuttedLogs"); }
    public static void UpdateShearsLeaves(Player p) { increment(p, "Woodcutter.ShearsLeaves", 1); }
    public static int getShearsLeaves(Player p) { return getCounter(p, "Woodcutter.ShearsLeaves"); }

    public static void UpdateMinedCoalOres(Player p) { increment(p, "Miner.MinedOres.Coal", 1); }
    public static int getMinedCoalOres(Player p) { return getCounter(p, "Miner.MinedOres.Coal"); }
    public static void UpdateMinedIronOres(Player p) { increment(p, "Miner.MinedOres.Iron", 1); }
    public static int getMinedIronOres(Player p) { return getCounter(p, "Miner.MinedOres.Iron"); }
    public static void UpdateMinedLapisOres(Player p) { increment(p, "Miner.MinedOres.Lapis", 1); }
    public static int getMinedLapisOres(Player p) { return getCounter(p, "Miner.MinedOres.Lapis"); }
    public static void UpdateMinedRedstoneOres(Player p) { increment(p, "Miner.MinedOres.Redstone", 1); }
    public static int getMinedRedstoneOres(Player p) { return getCounter(p, "Miner.MinedOres.Redstone"); }
    public static void UpdateMinedGoldOres(Player p) { increment(p, "Miner.MinedOres.Gold", 1); }
    public static int getMinedGoldOres(Player p) { return getCounter(p, "Miner.MinedOres.Gold"); }
    public static void UpdateMinedDiamondOres(Player p) { increment(p, "Miner.MinedOres.Diamond", 1); }
    public static int getMinedDiamondOres(Player p) { return getCounter(p, "Miner.MinedOres.Diamond"); }
    public static void UpdateMinedEmeraldOres(Player p) { increment(p, "Miner.MinedOres.Emerald", 1); }
    public static int getMinedEmeraldOres(Player p) { return getCounter(p, "Miner.MinedOres.Emerald"); }
    public static void UpdateMinedQuartzOres(Player p) { increment(p, "Miner.MinedOres.Quartz", 1); }
    public static int getMinedQuartzOres(Player p) { return getCounter(p, "Miner.MinedOres.Quartz"); }
    public static void UpdateMinedCopperOres(Player p) { increment(p, "Miner.MinedOres.Copper", 1); }
    public static int getMinedCopperOres(Player p) { return getCounter(p, "Miner.MinedOres.Copper"); }

    public static void UpdateMinedDeepSlateCoalOres(Player p) { increment(p, "Miner.MinedOres.DeepSlateCoalOre", 1); }
    public static int getMinedDeepSlateCoalOres(Player p) { return getCounter(p, "Miner.MinedOres.DeepSlateCoalOre"); }
    public static void UpdateMinedDeepSlateCopperOres(Player p) { increment(p, "Miner.MinedOres.DeepSlateCopperOre", 1); }
    public static int getMinedDeepSlateCopperOres(Player p) { return getCounter(p, "Miner.MinedOres.DeepSlateCopperOre"); }
    public static void UpdateMinedDeepSlateDiamondOres(Player p) { increment(p, "Miner.MinedOres.DeepSlateDiamondOre", 1); }
    public static int getMinedDeepSlateDiamondOres(Player p) { return getCounter(p, "Miner.MinedOres.DeepSlateDiamondOre"); }
    public static void UpdateMinedDeepSlateGoldOres(Player p) { increment(p, "Miner.MinedOres.DeepSlateGoldOre", 1); }
    public static int getMinedDeepSlateGoldOres(Player p) { return getCounter(p, "Miner.MinedOres.DeepSlateGoldOre"); }
    public static void UpdateMinedDeepSlateEmeraldOres(Player p) { increment(p, "Miner.MinedOres.DeepSlateEmeraldOre", 1); }
    public static int getMinedDeepSlateEmeraldOres(Player p) { return getCounter(p, "Miner.MinedOres.DeepSlateEmeraldOre"); }
    public static void UpdateMinedDeepSlateIronOres(Player p) { increment(p, "Miner.MinedOres.DeepSlateIronOre", 1); }
    public static int getMinedDeepSlateIronOres(Player p) { return getCounter(p, "Miner.MinedOres.DeepSlateIronOre"); }
    public static void UpdateMinedDeepSlateRedstoneOres(Player p) { increment(p, "Miner.MinedOres.DeepSlateRedstoneOre", 1); }
    public static int getMinedDeepSlateRedstoneOres(Player p) { return getCounter(p, "Miner.MinedOres.DeepSlateRedstoneOre"); }
    public static void UpdateMinedDeepSlateLapisOres(Player p) { increment(p, "Miner.MinedOres.DeepSlateLapisOre", 1); }
    public static int getMinedDeepSlateLapisOres(Player p) { return getCounter(p, "Miner.MinedOres.DeepSlateLapisOre"); }
    public static void UpdateMinedDeepSlateNetherGoldOres(Player p) { increment(p, "Miner.MinedOres.DeepSlateNetherGoldOre", 1); }
    public static int getMinedDeepSlateNetherGoldOres(Player p) { return getCounter(p, "Miner.MinedOres.DeepSlateNetherGoldOre"); }

    public static void UpdateHarvestWheat(Player p) { increment(p, "Farmer.Harvest.Wheat", 1); }
    public static int getHarvestWheat(Player p) { return getCounter(p, "Farmer.Harvest.Wheat"); }
    public static void UpdateHarvestCarrots(Player p) { increment(p, "Farmer.Harvest.Carrots", 1); }
    public static int getHarvestCarrots(Player p) { return getCounter(p, "Farmer.Harvest.Carrots"); }
    public static void UpdateHarvestPotatos(Player p) { increment(p, "Farmer.Harvest.Potatos", 1); }
    public static int getHarvestPotatos(Player p) { return getCounter(p, "Farmer.Harvest.Potatos"); }
    public static void UpdateHarvestBeetroot(Player p) { increment(p, "Farmer.Harvest.Beetroot", 1); }
    public static int getHarvestBeetroot(Player p) { return getCounter(p, "Farmer.Harvest.Beetroot"); }
    public static void UpdateHarvestPumpkin(Player p) { increment(p, "Farmer.Harvest.Pumpkin", 1); }
    public static int getHarvestPumpkin(Player p) { return getCounter(p, "Farmer.Harvest.Pumpkin"); }
    public static void UpdateHarvestMelon(Player p) { increment(p, "Farmer.Harvest.Melon", 1); }
    public static int getHarvestMelon(Player p) { return getCounter(p, "Farmer.Harvest.Melon"); }
    public static void UpdateHarvestTorchflower(Player p) { increment(p, "Farmer.Harvest.Torchflower", 1); }
    public static int getHarvestTorchflower(Player p) { return getCounter(p, "Farmer.Harvest.Torchflower"); }

    public static void flush() {
        DataSaveManager.flushAll();
    }

    private static void increment(Player player, String counter, int amount) {
        if (player == null || amount <= 0) {
            return;
        }

        int current = getCounter(player, counter);
        long updated = (long) current + amount;
        setCounter(player, counter, updated > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) updated);
        DataSaveManager.markPlayerDataDirty();
    }

    private static int getCounter(Player player, String counter) {
        if (player == null) {
            return 0;
        }
        return Math.max(0, DATA.getData().getInt(jobPath(player, counter)));
    }

    private static void initializeCounter(Player player, String counter, int defaultValue) {
        String path = jobPath(player, counter);
        if (!DATA.getData().contains(path)) {
            DATA.getData().set(path, Math.max(0, defaultValue));
        }
    }

    private static void setCounter(Player player, String counter, int value) {
        DATA.getData().set(jobPath(player, counter), Math.max(0, value));
    }

    private static String jobPath(Player player, String counter) {
        return playerBase(player) + ".Job." + counter;
    }

    private static String playerBase(Player player) {
        return "Players." + player.getUniqueId();
    }

    private static void setPrefixInMemory(Player player, String prefix) {
        DATA.getData().set(playerBase(player) + ".Prefix", prefix);
    }

    private static ItemStack infoItem(Material material, String job) {
        return item(material, "§a" + job, List.of("§8- §fClick to get information about this job."));
    }

    private static ItemStack jobSelectionItem(Player player, Material material, String job, String description) {
        int level = JobProgressionManager.getLevel(player, job);
        int bonus = JobProgressionManager.getRewardBonusPercent(player, job);
        return item(material, "§a" + job, List.of(
                "§8- §fClick to choose the " + job.toLowerCase() + " job.",
                "§8- §f" + description,
                "§8- §fSaved level: §a" + level,
                "§8- §fReward bonus: §a+" + bonus + "%",
                "§8- §7Your progress is preserved when changing jobs."));
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static void sendJobInfo(Player player, String job, String... activities) {
        if (player == null) {
            return;
        }
        player.sendMessage("§fInformation from the job: §a" + job);
        player.sendMessage("§fSaved level: §a" + JobProgressionManager.getLevel(player, job));
        player.sendMessage("§fReward bonus: §a+" + JobProgressionManager.getRewardBonusPercent(player, job) + "%");
        player.sendMessage("§fYou can earn money and XP with the following activities:");
        for (String activity : activities) {
            player.sendMessage("§8- §a" + activity);
        }
    }
}
