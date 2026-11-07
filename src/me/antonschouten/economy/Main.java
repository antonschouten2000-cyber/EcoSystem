package me.antonschouten.economy;

import java.util.Objects;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandExecutor;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import me.antonschouten.economy.Commands.HelpCMD;
import me.antonschouten.economy.Commands.Accounts.ResetAllAccountsCMD;
import me.antonschouten.economy.Commands.Accounts.ResetAllJobsCMD;
import me.antonschouten.economy.Commands.Auction.AuctionCMD;
import me.antonschouten.economy.Commands.Balance.AddBalanceCMD;
import me.antonschouten.economy.Commands.Balance.GetBalanceCMD;
import me.antonschouten.economy.Commands.Balance.RemoveBalanceCMD;
import me.antonschouten.economy.Commands.Balance.ResetBalanceCMD;
import me.antonschouten.economy.Commands.Balance.SetBalanceCMD;
import me.antonschouten.economy.Commands.Balance.TopBalanceCMD;
import me.antonschouten.economy.Commands.Chunk.ChunkCMD;
import me.antonschouten.economy.Commands.Gift.GiftCMD;
import me.antonschouten.economy.Commands.Guild.GuildCMD;
import me.antonschouten.economy.Commands.Guild.GuildChatCMD;
import me.antonschouten.economy.Commands.Jobs.JobCMD;
import me.antonschouten.economy.Commands.Jobs.JobsMainCMD;
import me.antonschouten.economy.Commands.Lootcrate.LootcrateCMD;
import me.antonschouten.economy.Commands.Lootcrate.SetLootcrateBlockCMD;
import me.antonschouten.economy.Commands.Pay.PayAllCMD;
import me.antonschouten.economy.Commands.Pay.PayCMD;
import me.antonschouten.economy.Commands.Salary.SalaryHelpCMD;
import me.antonschouten.economy.Commands.Serverbank.ServerbankCMD;
import me.antonschouten.economy.Commands.Shop.ShopCMD;
import me.antonschouten.economy.Commands.Teleport.BackCMD;
import me.antonschouten.economy.Commands.Teleport.Home.DelHomeCMD;
import me.antonschouten.economy.Commands.Teleport.Home.HomeCMD;
import me.antonschouten.economy.Commands.Teleport.Home.HomeListCMD;
import me.antonschouten.economy.Commands.Teleport.Home.SetHomeCMD;
import me.antonschouten.economy.Commands.Teleport.Spawn.SetSpawnCMD;
import me.antonschouten.economy.Commands.Teleport.Spawn.SpawnCMD;
import me.antonschouten.economy.Commands.Teleport.Tpa.TpAcceptCMD;
import me.antonschouten.economy.Commands.Teleport.Tpa.TpCancelCMD;
import me.antonschouten.economy.Commands.Teleport.Tpa.TpDenyCMD;
import me.antonschouten.economy.Commands.Teleport.Tpa.TpaCMD;
import me.antonschouten.economy.Commands.Teleport.Tpa.TpaHereCMD;
import me.antonschouten.economy.Commands.Teleport.Warp.DelWarpCMD;
import me.antonschouten.economy.Commands.Teleport.Warp.SetWarpCMD;
import me.antonschouten.economy.Commands.Teleport.Warp.WarpCMD;
import me.antonschouten.economy.Commands.Teleport.Warp.WarpsCMD;
import me.antonschouten.economy.Commands.TradeNPC.TradeNPCCMD;
import me.antonschouten.economy.Commands.Transactions.TransactionsCMD;
import me.antonschouten.economy.Data.ConfigData;
import me.antonschouten.economy.Data.DataFiles;
import me.antonschouten.economy.Listeners.Listeners;
import me.antonschouten.economy.Listeners.ATM.ATMInventoryListener;
import me.antonschouten.economy.Listeners.ATM.ATMSign;
import me.antonschouten.economy.Listeners.ATM.Cheque.CreateChequeListener;
import me.antonschouten.economy.Listeners.ATM.Cheque.UseChequeListener;
import me.antonschouten.economy.Listeners.Booster.JobBoosterInventoryListener;
import me.antonschouten.economy.Listeners.Chat.AdminPanelPassword;
import me.antonschouten.economy.Listeners.Chat.ResetAccountsVerify;
import me.antonschouten.economy.Listeners.Chat.ResetJobsVerify;
import me.antonschouten.economy.Listeners.Chunk.ChunkListeners;
import me.antonschouten.economy.Listeners.Jobs.JobsChooseListener;
import me.antonschouten.economy.Listeners.Jobs.JobsInfoScreen;
import me.antonschouten.economy.Listeners.Jobs.Farmer.FarmerListener;
import me.antonschouten.economy.Listeners.Jobs.Fisherman.FishermanListener;
import me.antonschouten.economy.Listeners.Jobs.Hunter.HunterKillAnimalBowListener;
import me.antonschouten.economy.Listeners.Jobs.Hunter.HunterKillAnimalSwordListener;
import me.antonschouten.economy.Listeners.Jobs.Miner.MinerBreakBlocksListener;
import me.antonschouten.economy.Listeners.Jobs.Smith.CraftArmor.SmithCraftArmorListener;
import me.antonschouten.economy.Listeners.Jobs.Smith.CraftArmor.SmithCraftNetheriteArmor;
import me.antonschouten.economy.Listeners.Jobs.Smith.CraftTools.SmithCraftNetheriteTools;
import me.antonschouten.economy.Listeners.Jobs.Smith.CraftTools.SmithCraftToolsListener;
import me.antonschouten.economy.Listeners.Jobs.Smith.Furnace.SmithFurnaceListener;
import me.antonschouten.economy.Listeners.Jobs.Woodcutter.WoodcutterBreakBlocksListener;
import me.antonschouten.economy.Listeners.Join.FirstJoinListener;
import me.antonschouten.economy.Listeners.Join.PlayerJoinListener;
import me.antonschouten.economy.Listeners.Lootcrate.CrateListener;
import me.antonschouten.economy.Listeners.Lootcrate.SetLootcrateBlockListener;
import me.antonschouten.economy.Listeners.Shop.ShopInventoryListener;
import me.antonschouten.economy.Listeners.Signs.RepairSign;
import me.antonschouten.economy.Listeners.Signs.ShopSign;
import me.antonschouten.economy.Listeners.Teleport.TeleportListener;
import me.antonschouten.economy.Listeners.TradeNPC.TradeNPCHitListener;
import me.antonschouten.economy.Listeners.TradeNPC.TradeNPCListener;
import me.antonschouten.economy.Manager.BoosterManager;
import me.antonschouten.economy.Manager.ChunkManager;
import me.antonschouten.economy.Manager.DataSaveManager;
import me.antonschouten.economy.Manager.EconomyManager;
import me.antonschouten.economy.Manager.SalaryManager;
import me.antonschouten.economy.Manager.TradeManager;
import me.antonschouten.economy.Metrics.Metrics;
import me.antonschouten.economy.Migration.DataMigrationManager;
import me.antonschouten.economy.Migration.DataMigrationManager.MigrationResult;
import me.antonschouten.economy.Teleport.TeleportManager;
import me.antonschouten.economy.Transaction.TransactionManager;
import me.antonschouten.economy.Updater.Updater;
import me.antonschouten.economy.Utils.SB;

public final class Main extends JavaPlugin {

    public static Main instance;
    public static final String prefix = "§8[§6Economy++§8] §f";
    public static final String perms = prefix + "You don't have enough permissions.";

    public static ChunkManager chunkClaimManager;
    public static NamespacedKey traderIdKey;

    private Updater updater;

    @Override
    public void onEnable() {
        instance = this;
        log("Enabling Economy++...");

        try {
            MigrationResult migration = DataMigrationManager.migrateIfNeeded(this);
            if (!migration.success()) {
                throw new IllegalStateException(
                        "Data migration safety check failed: " + migration.message());
            }
            if (migration.migrated() && migration.backupFolder() != null) {
                log("Data migration completed. Backup: " + migration.backupFolder().toAbsolutePath());
            }

            setupDataFiles();
            TransactionManager.initialize(this);
            TeleportManager.initialize(this);
            initializeManagers();
            registerCommands();
            registerEvents();
            startSystems();
            startIntegrations();

            log("Plugin successfully enabled.");
            log("If you like our plugin and want to support us, donate:");
            log("PayPal: https://www.paypal.me/economyplusplus");
        } catch (Exception exception) {
            getLogger().log(
                    Level.SEVERE,
                    "Economy++ could not be enabled safely. The plugin is being disabled.",
                    exception);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // Stop manager-owned tasks first, then persist all pending YAML changes.
        SalaryManager.stop();
        EconomyManager.shutdown();
        BoosterManager.shutdown();
        SB.shutdown();
        TeleportManager.shutdown();
        TransactionManager.shutdown();

        if (updater != null) {
            updater.shutdown();
            updater = null;
        }

        if (!DataSaveManager.flushAllAndReport()) {
            getLogger().severe("One or more Economy++ data files could not be saved during shutdown.");
        }

        Bukkit.getScheduler().cancelTasks(this);

        chunkClaimManager = null;
        traderIdKey = null;

        log("Plugin successfully disabled.");
        instance = null;
    }

    private void setupDataFiles() {
        DataFiles.setupAll(this);
    }

    private void initializeManagers() {
        chunkClaimManager = new ChunkManager();
        chunkClaimManager.loadClaims();
        traderIdKey = new NamespacedKey(this, "trader_id");
    }

    private void registerCommands() {
        registerCommand("setbal", new SetBalanceCMD());
        registerCommand("addbal", new AddBalanceCMD());
        registerCommand("removebal", new RemoveBalanceCMD());
        registerCommand("bal", new GetBalanceCMD());
        registerCommand("pay", new PayCMD());
        registerCommand("payall", new PayAllCMD());
        registerCommand("resetbal", new ResetBalanceCMD());
        registerCommand("resetallaccounts", new ResetAllAccountsCMD());
        registerCommand("economy", new HelpCMD());
        registerCommand("jobs", new JobsMainCMD());
        registerCommand("job", new JobCMD());
        registerCommand("resetalljobs", new ResetAllJobsCMD());
        registerCommand("salary", new SalaryHelpCMD());
        registerCommand("lootcrate", new LootcrateCMD());
        registerCommand("setlootcrate", new SetLootcrateBlockCMD());
        registerCommand("shop", new ShopCMD());
        registerCommand("sethome", new SetHomeCMD());
        registerCommand("delhome", new DelHomeCMD());
        registerCommand("home", new HomeCMD());
        registerCommand("homes", new HomeListCMD());
        registerCommand("gift", new GiftCMD());
        registerCommand("serverbank", new ServerbankCMD());
        registerCommand("guild", new GuildCMD());
        registerCommand("g", new GuildChatCMD());
        registerCommand("chunk", new ChunkCMD());
        registerCommand("tradenpc", new TradeNPCCMD());
        registerCommand("baltop", new TopBalanceCMD());
        registerCommand("transactions", new TransactionsCMD());
        registerCommand("spawn", new SpawnCMD());
        registerCommand("setspawn", new SetSpawnCMD());
        registerCommand("warp", new WarpCMD());
        registerCommand("warps", new WarpsCMD());
        registerCommand("setwarp", new SetWarpCMD());
        registerCommand("delwarp", new DelWarpCMD());
        registerCommand("tpa", new TpaCMD());
        registerCommand("tpahere", new TpaHereCMD());
        registerCommand("tpaccept", new TpAcceptCMD());
        registerCommand("tpdeny", new TpDenyCMD());
        registerCommand("tpcancel", new TpCancelCMD());
        registerCommand("back", new BackCMD());
        registerCommand("auction", new AuctionCMD());
    }

    private void registerCommand(String commandName, CommandExecutor executor) {
        Objects.requireNonNull(getCommand(commandName),
                "Command '" + commandName + "' is missing from plugin.yml").setExecutor(executor);
    }

    private void registerEvents() {
        PluginManager pm = Bukkit.getPluginManager();

        pm.registerEvents(new FirstJoinListener(), this);
        pm.registerEvents(new PlayerJoinListener(), this);
        pm.registerEvents(new Listeners(), this);
        pm.registerEvents(new TeleportListener(), this);

        pm.registerEvents(new ResetAccountsVerify(), this);
        pm.registerEvents(new ResetJobsVerify(), this);
        pm.registerEvents(new AdminPanelPassword(), this);

        pm.registerEvents(new ATMSign(), this);
        pm.registerEvents(new ATMInventoryListener(), this);
        pm.registerEvents(new CreateChequeListener(), this);
        pm.registerEvents(new UseChequeListener(), this);

        pm.registerEvents(new JobsChooseListener(), this);
        pm.registerEvents(new JobsInfoScreen(), this);
        pm.registerEvents(new WoodcutterBreakBlocksListener(), this);
        pm.registerEvents(new MinerBreakBlocksListener(), this);
        pm.registerEvents(new FarmerListener(), this);
        pm.registerEvents(new SmithCraftArmorListener(), this);
        pm.registerEvents(new SmithCraftNetheriteArmor(), this);
        pm.registerEvents(new SmithCraftToolsListener(), this);
        pm.registerEvents(new SmithCraftNetheriteTools(), this);
        pm.registerEvents(new SmithFurnaceListener(), this);
        pm.registerEvents(new FishermanListener(), this);
        pm.registerEvents(new HunterKillAnimalSwordListener(), this);
        pm.registerEvents(new HunterKillAnimalBowListener(), this);
        pm.registerEvents(new JobBoosterInventoryListener(), this);

        pm.registerEvents(new CrateListener(), this);
        pm.registerEvents(new SetLootcrateBlockListener(), this);

        pm.registerEvents(new ShopInventoryListener(), this);
        pm.registerEvents(new ShopSign(), this);
        pm.registerEvents(new RepairSign(), this);

        pm.registerEvents(new ChunkListeners(chunkClaimManager), this);
        pm.registerEvents(new TradeNPCListener(), this);
        pm.registerEvents(new TradeNPCHitListener(), this);
    }

    private void startSystems() {
        BoosterManager.restoreActiveBoosters();

        if (ConfigData.getInstance().getData().getBoolean("Settings.Salary.Enabled")) {
            SalaryManager.PaySalary();
        }

        Bukkit.getOnlinePlayers().forEach(SB::updateScoreboard);
        EconomyManager.giveServerbankBalanceToPlayer();
        TradeManager.loadTradersFromConfig();
    }

    private void startIntegrations() {
        new Metrics(this, 25475);

        updater = new Updater(this, Updater.DEFAULT_RESOURCE_ID);
        updater.checkConsoleAsync();
    }

    public static Main getInstance() {
        return instance;
    }

    public static ChunkManager getChunkClaimManager() {
        return chunkClaimManager;
    }

    public static NamespacedKey getTraderIdKey() {
        return traderIdKey;
    }

    private void log(String message) {
        Bukkit.getConsoleSender().sendMessage(prefix + message);
    }
}
