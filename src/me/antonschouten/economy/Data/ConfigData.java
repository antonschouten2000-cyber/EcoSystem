package me.antonschouten.economy.Data;

import org.bukkit.configuration.file.YamlConfiguration;

public final class ConfigData extends AbstractYamlData {

    private static final ConfigData INSTANCE = new ConfigData();

    private ConfigData() {
        super("Config.yml");
    }

    public static ConfigData getInstance() {
        return INSTANCE;
    }

    @Override
    protected boolean applyDefaults(YamlConfiguration config) {
        boolean changed = false;

        // Set only after DataMigrationManager has safely migrated older files.
        if (!config.isInt(DataSchema.VERSION_KEY)
                || config.getInt(DataSchema.VERSION_KEY) < DataSchema.CURRENT_VERSION) {
            config.set(DataSchema.VERSION_KEY, DataSchema.CURRENT_VERSION);
            changed = true;
        }

        changed |= ensureInt(config, "Price-Per-Chunk-Claim", 1000, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Max-Claims-Per-Player", 3, 0, Integer.MAX_VALUE);
        changed |= ensureString(config, "AdminPanelPassword", "adminPanel.yourserver", false);

        changed |= ensureBoolean(config, "Settings.Join.Enabled", true);
        changed |= ensureInt(config, "Settings.Join.moneyOnFirstJoin", 250, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Settings.Join.moneyOnJoin", 50, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Settings.Join.CooldownHours", 24 ,0, Integer.MAX_VALUE);

        changed |= ensureBoolean(config, "Settings.Salary.Enabled", true);
        changed |= ensureInt(config, "Settings.Salary.Amount", 50, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Settings.Salary.PayPerEveryHours", 1, 1, Integer.MAX_VALUE);

        changed |= ensureInt(config, "Settings.Signs.CostRepairSign", 500, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Settings.Lootcrate", 100, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Settings.AmountOfHomes", 5, 0, Integer.MAX_VALUE);
        changed |= ensureBoolean(config, "Settings.HomesEnabled", true);
        changed |= ensureBoolean(config, "Settings.ScoreboardEnabled", true);
        changed |= ensureInt(config, "ServerbankBalance", 0, 0, Integer.MAX_VALUE);

        changed |= ensureBoolean(config, "Transactions.Enabled", true);
        changed |= ensureInt(config, "Transactions.RetentionDays", 30, 1, 3650);
        changed |= ensureBoolean(config, "Transactions.LogJobRewards", true);
        changed |= ensureInt(config, "Transactions.Cache.Global", 5000, 100, 100000);
        changed |= ensureInt(config, "Transactions.Cache.PerPlayer", 250, 25, 10000);
        changed |= ensureInt(config, "Transactions.PageSize", 8, 1, 20);

        changed |= ensureBoolean(config, "Settings.Teleports.Enabled", true);
        changed |= ensureInt(config, "Settings.Teleports.DelaySeconds", 3, 0, 300);
        changed |= ensureInt(config, "Settings.Teleports.CooldownSeconds", 5, 0, 3600);
        changed |= ensureBoolean(config, "Settings.Teleports.CancelOnMove", true);
        changed |= ensureBoolean(config, "Settings.Teleports.CancelOnDamage", true);
        changed |= ensureBoolean(config, "Settings.Teleports.Features.Spawn", true);
        changed |= ensureBoolean(config, "Settings.Teleports.Features.Warps", true);
        changed |= ensureBoolean(config, "Settings.Teleports.Features.TPA", true);
        changed |= ensureBoolean(config, "Settings.Teleports.Features.Back", true);
        changed |= ensureInt(config, "Settings.Teleports.TPA.RequestTimeoutSeconds", 60, 5, 600);
        changed |= ensureInt(config, "Settings.Teleports.TPA.RequestCooldownSeconds", 10, 0, 300);
        changed |= ensureBoolean(config, "Settings.Teleports.Back.OnDeath", true);
        changed |= ensureBoolean(config, "Settings.Teleports.Back.PersistAcrossRestarts", false);
        changed |= ensureBoolean(config, "Settings.Teleports.Back.TrackExternalTeleports", true);
        changed |= ensureBoolean(config, "Settings.Teleports.SafeLocation.Enabled", true);
        changed |= ensureInt(config, "Settings.Teleports.SafeLocation.HorizontalRadius", 2, 0, 8);
        changed |= ensureInt(config, "Settings.Teleports.SafeLocation.VerticalRadius", 6, 0, 32);

        changed |= ensureInt(config, "Jobs.ChangePrice", 1000, 0, Integer.MAX_VALUE);
        changed |= ensureBoolean(config, "Jobs.Progression.Enabled", true);
        changed |= ensureInt(config, "Jobs.Progression.MaxLevel", 50, 1, 1000);
        changed |= ensureInt(config, "Jobs.Progression.BaseXP", 100, 1, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Progression.XPIncreasePerLevel", 50, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Progression.RewardBonusPercentPerLevel", 2, 0, 100);
        changed |= ensureInt(config, "Jobs.Progression.XPPerRewardPoint", 1, 0, 1000);
        changed |= ensureInt(config, "Jobs.Progression.MinimumXPPerAction", 1, 0, 1000);
        changed |= ensureBoolean(config, "Jobs.Progression.NotifyLevelUp", true);

        changed |= ensureInt(config, "Jobs.Rewards.Hunter.KilledAnimals", 2, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Fisherman.CatchedFishes", 2, 0, Integer.MAX_VALUE);

        changed |= ensureInt(config, "Jobs.Rewards.Smith.CraftWoodTools", 1, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Smith.CraftStoneTools", 2, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Smith.CraftIronTools", 3, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Smith.CraftCopperTools", 3, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Smith.CraftGoldTools", 4, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Smith.CraftDiamondTools", 5, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Smith.CraftNetheriteTools", 6, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Smith.CraftLeatherArmor", 2, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Smith.CraftChainmailArmor", 4, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Smith.CraftIronArmor", 6, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Smith.CraftCopperArmor", 6, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Smith.CraftGoldArmor", 8, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Smith.CraftDiamondArmor", 10, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Smith.CraftNetheriteArmor", 15, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Smith.CookedItem", 1, 0, Integer.MAX_VALUE);
        

        changed |= ensureInt(config, "Jobs.Rewards.Woodcutter.Cuttedlog", 1, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Woodcutter.Shearsleaves", 1, 0, Integer.MAX_VALUE);

        changed |= ensureInt(config, "Jobs.Rewards.Farmer.Wheat", 1, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Farmer.Carrots", 1, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Farmer.Potato", 1, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Farmer.Beetroot", 1, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Farmer.Pumpkin", 1, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Farmer.Melon", 1, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Farmer.Torchflower", 1, 0, Integer.MAX_VALUE);

        changed |= ensureInt(config, "Jobs.Rewards.Miner.CoalOre", 1, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Miner.IronOre", 1, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Miner.LapisOre", 1, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Miner.RedstoneOre", 1, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Miner.GoldOre", 1, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Miner.DiamondOre", 2, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Miner.EmeraldOre", 3, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Miner.QuartzOre", 1, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Miner.CopperOre", 1, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Miner.DeepSlateCoalOre", 1, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Miner.DeepSlateCopperOre", 1, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Miner.DeepSlateDiamondOre", 1, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Miner.DeepSlateGoldOre", 1, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Miner.DeepSlateEmeraldOre", 1, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Miner.DeepSlateIronOre", 1, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Miner.DeepSlateRedstoneOre", 1, 0, Integer.MAX_VALUE);
        changed |= ensureInt(config, "Jobs.Rewards.Miner.DeepSlateLapisOre", 1, 0, Integer.MAX_VALUE);
        // Historic key retained for backwards compatibility with existing servers.
        changed |= ensureInt(config, "Jobs.Rewards.Miner.DeepSlateNetherGoldOre", 1, 0, Integer.MAX_VALUE);

        return changed;
    }
}
