package me.antonschouten.economy.Migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import me.antonschouten.economy.Data.DataSchema;

/**
 * Transactional, versioned migration system for Economy++ YAML data.
 *
 * <p>No live data file is modified until all migration steps have succeeded in
 * a staging copy and the resulting data passes integrity checks. A complete
 * pre-upgrade backup is created before staging begins.</p>
 */
public final class DataMigrationManager {

    private static final String DATA_DIRECTORY = "Data";
    private static final String CONFIG_FILE = "Config.yml";
    private static final long LEGACY_BOOSTER_DURATION_MILLIS = 60L * 60L * 1000L;
    private static final DateTimeFormatter STAGING_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final List<String> KNOWN_DATA_FILES = List.of(
            "Config.yml",
            "PlayerData.yml",
            "BoosterData.yml",
            "LootcrateData.yml",
            "ShopData.yml",
            "HomeData.yml",
            "GuildData.yml",
            "ChunkData.yml",
            "TradeData.yml",
            "TeleportData.yml"
    );


    private DataMigrationManager() {
    }

    /**
     * Runs before DataFiles.setupAll().
     */
    public static MigrationResult migrateIfNeeded(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");

        Path currentPluginFolder = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path sourcePluginFolder = locateSourcePluginFolder(currentPluginFolder);

        if (sourcePluginFolder == null) {
            plugin.getLogger().info("No existing Economy++ data found. Starting as a new installation.");
            return MigrationResult.success(false, null, 0, DataSchema.CURRENT_VERSION,
                    "New installation; no migration required.");
        }

        int sourceVersion;
        try {
            sourceVersion = readSchemaVersion(sourcePluginFolder.resolve(DATA_DIRECTORY));
        } catch (Exception exception) {
            Path backup = safeEmergencyBackup(plugin, sourcePluginFolder, -1);
            plugin.getLogger().log(Level.SEVERE,
                    "Could not read the existing Economy++ data schema. The plugin will not start "
                            + "because continuing could risk player data. Backup: " + formatPath(backup),
                    exception);
            return MigrationResult.failure(backup, -1, DataSchema.CURRENT_VERSION,
                    "Existing Config.yml could not be parsed.");
        }

        if (sourceVersion > DataSchema.CURRENT_VERSION) {
            Path backup = safeEmergencyBackup(plugin, sourcePluginFolder, sourceVersion);
            return MigrationResult.failure(backup, sourceVersion, DataSchema.CURRENT_VERSION,
                    "Data schema " + sourceVersion + " is newer than this plugin supports ("
                            + DataSchema.CURRENT_VERSION + "). Downgrading is blocked to protect data.");
        }

        boolean importingAlternateFolder = !sourcePluginFolder.equals(currentPluginFolder);
        if (sourceVersion == DataSchema.CURRENT_VERSION && !importingAlternateFolder) {
            return MigrationResult.success(false, null, sourceVersion, sourceVersion,
                    "Data is already on the current schema.");
        }

        Path backupFolder;
        try {
            backupFolder = DataBackupManager.createPreMigrationBackup(
                    sourcePluginFolder,
                    currentPluginFolder,
                    sourceVersion,
                    DataSchema.CURRENT_VERSION);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Economy++ refused to migrate because a verified pre-upgrade backup could not be created.",
                    exception);
            return MigrationResult.failure(null, sourceVersion, DataSchema.CURRENT_VERSION,
                    "Backup creation failed. Live data was not modified.");
        }

        plugin.getLogger().info("Created pre-upgrade backup: " + formatPath(backupFolder));

        Path stagingRoot = currentPluginFolder.resolve(
                ".migration-staging-" + STAGING_TIMESTAMP.format(LocalDateTime.now())
                        + "-" + UUID.randomUUID().toString().substring(0, 8));
        Path stagingData = stagingRoot.resolve(DATA_DIRECTORY);

        try {
            Files.createDirectories(stagingRoot);
            DataBackupManager.copyDirectory(sourcePluginFolder.resolve(DATA_DIRECTORY), stagingData);

            Map<String, YamlConfiguration> configs = loadAllKnownFiles(stagingData);
            IntegritySnapshot before = IntegritySnapshot.capture(configs);

            int workingVersion = sourceVersion;
            while (workingVersion < DataSchema.CURRENT_VERSION) {
                switch (workingVersion) {
                    case 0 -> migrateLegacyToV1(configs, plugin);
                    case 1 -> migrateV1ToV2(configs, plugin);
                    default -> throw new MigrationException(
                            "No migration path exists from schema " + workingVersion + ".");
                }
                workingVersion++;
            }

            YamlConfiguration config = configs.get(CONFIG_FILE);
            config.set(DataSchema.VERSION_KEY, DataSchema.CURRENT_VERSION);

            saveAllKnownFiles(stagingData, configs);
            Map<String, YamlConfiguration> verifiedConfigs = loadAllKnownFiles(stagingData);
            IntegritySnapshot after = IntegritySnapshot.capture(verifiedConfigs);
            before.verifyCriticalDataPreserved(after);

            commitStagedData(stagingData, currentPluginFolder.resolve(DATA_DIRECTORY), backupFolder);

            plugin.getLogger().info(
                    "Economy++ data migration completed successfully: schema "
                            + sourceVersion + " -> " + DataSchema.CURRENT_VERSION + ".");
            logSnapshot(plugin, "Before migration", before);
            logSnapshot(plugin, "After migration", after);

            return MigrationResult.success(true, backupFolder, sourceVersion,
                    DataSchema.CURRENT_VERSION,
                    importingAlternateFolder
                            ? "Legacy data folder imported and migrated successfully."
                            : "Data migration completed successfully.");

        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Economy++ data migration failed. Live data was not intentionally changed; "
                            + "the pre-upgrade backup is at " + formatPath(backupFolder),
                    exception);
            return MigrationResult.failure(backupFolder, sourceVersion,
                    DataSchema.CURRENT_VERSION, exception.getMessage());
        } finally {
            try {
                DataBackupManager.deleteRecursively(stagingRoot);
            } catch (IOException cleanupFailure) {
                plugin.getLogger().warning(
                        "Could not remove migration staging directory: " + stagingRoot);
            }
        }
    }

    private static Path locateSourcePluginFolder(Path currentPluginFolder) {
        if (containsEconomyData(currentPluginFolder)) {
            return currentPluginFolder;
        }

        Path parent = currentPluginFolder.getParent();
        if (parent == null) {
            return null;
        }

        // Defensive compatibility with experimental/older builds that may have
        // used the branded folder name instead of plugin.yml name EcoSystem.
        for (String legacyName : List.of("Economy++", "EconomyPlusPlus")) {
            Path candidate = parent.resolve(legacyName).toAbsolutePath().normalize();
            if (!candidate.equals(currentPluginFolder) && containsEconomyData(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean containsEconomyData(Path pluginFolder) {
        Path data = pluginFolder.resolve(DATA_DIRECTORY);
        if (!Files.isDirectory(data)) {
            return false;
        }
        for (String name : KNOWN_DATA_FILES) {
            if (Files.isRegularFile(data.resolve(name))) {
                return true;
            }
        }
        return false;
    }

    private static int readSchemaVersion(Path dataDirectory)
            throws IOException, InvalidConfigurationException {
        Path configFile = dataDirectory.resolve(CONFIG_FILE);
        if (Files.notExists(configFile)) {
            return 0;
        }

        YamlConfiguration config = loadYaml(configFile);
        if (!config.contains(DataSchema.VERSION_KEY)) {
            return 0;
        }
        if (!config.isInt(DataSchema.VERSION_KEY)) {
            throw new InvalidConfigurationException(
                    DataSchema.VERSION_KEY + " must be an integer.");
        }

        int version = config.getInt(DataSchema.VERSION_KEY);
        if (version < 0) {
            throw new InvalidConfigurationException(
                    DataSchema.VERSION_KEY + " cannot be negative.");
        }
        return version;
    }

    private static Map<String, YamlConfiguration> loadAllKnownFiles(Path dataDirectory)
            throws IOException, InvalidConfigurationException {
        Files.createDirectories(dataDirectory);
        Map<String, YamlConfiguration> configs = new LinkedHashMap<>();
        for (String name : KNOWN_DATA_FILES) {
            Path file = dataDirectory.resolve(name);
            YamlConfiguration configuration;
            if (Files.exists(file)) {
                configuration = loadYaml(file);
            } else {
                configuration = new YamlConfiguration();
            }
            configs.put(name, configuration);
        }
        return configs;
    }

    private static YamlConfiguration loadYaml(Path file)
            throws IOException, InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.load(file.toFile());
        return configuration;
    }

    private static void saveAllKnownFiles(
            Path dataDirectory,
            Map<String, YamlConfiguration> configs) throws IOException {
        Files.createDirectories(dataDirectory);
        for (Map.Entry<String, YamlConfiguration> entry : configs.entrySet()) {
            writeYamlAtomically(dataDirectory.resolve(entry.getKey()), entry.getValue());
        }
    }

    private static void writeYamlAtomically(Path target, YamlConfiguration configuration)
            throws IOException {
        String yaml = configuration.saveToString();
        Path temp = target.resolveSibling(target.getFileName() + ".migration.tmp");
        Files.writeString(temp, yaml, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        try {
            try {
                Files.move(temp, target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Legacy release data -> safe normalized base schema. */
    private static void migrateLegacyToV1(
            Map<String, YamlConfiguration> configs,
            Plugin plugin) throws MigrationException {
        migrateHomeLists(configs.get("HomeData.yml"));
        migrateLegacyBoosters(configs.get("BoosterData.yml"));
        migrateLegacyTraders(configs.get("TradeData.yml"), plugin);
    }

    /** Adds the new persistent Jobs progression model without changing old stats. */
    private static void migrateV1ToV2(
            Map<String, YamlConfiguration> configs,
            Plugin plugin) {
        YamlConfiguration playersConfig = configs.get("PlayerData.yml");
        ConfigurationSection players = playersConfig.getConfigurationSection("Players");
        if (players == null) {
            return;
        }

        long initialized = 0;
        for (String uuid : players.getKeys(false)) {
            ConfigurationSection jobs = playersConfig.getConfigurationSection("Players." + uuid + ".Job");
            Set<String> jobsToInitialize = new LinkedHashSet<>();

            if (jobs != null) {
                for (String rawJob : jobs.getKeys(false)) {
                    String canonical = canonicalJob(rawJob);
                    if (canonical != null) {
                        jobsToInitialize.add(canonical);
                    }
                }
            }

            String active = canonicalJob(playersConfig.getString("Players." + uuid + ".Prefix"));
            if (active != null) {
                jobsToInitialize.add(active);
            }

            for (String job : jobsToInitialize) {
                String base = "Players." + uuid + ".JobProgress." + job;
                setIfMissing(playersConfig, base + ".Level", 1);
                setIfMissing(playersConfig, base + ".XP", 0);
                setIfMissing(playersConfig, base + ".LifetimeXP", 0L);
                setIfMissing(playersConfig, base + ".TotalEarned", 0L);
                initialized++;
            }
        }
        if (initialized > 0) {
            plugin.getLogger().info(
                    "Initialized preserved Job progression records for " + initialized + " player/job combinations.");
        }
    }

    private static void migrateHomeLists(YamlConfiguration homesConfig) {
        for (String playerKey : new ArrayList<>(homesConfig.getKeys(false))) {
            ConfigurationSection homes = homesConfig.getConfigurationSection(playerKey + ".Homes");
            List<String> actualHomes = homes == null
                    ? List.of()
                    : new ArrayList<>(homes.getKeys(false));
            homesConfig.set(playerKey + ".HomeList", actualHomes);
        }
    }

    private static void migrateLegacyBoosters(YamlConfiguration boostersConfig) {
        ConfigurationSection players = boostersConfig.getConfigurationSection("Players");
        if (players == null) {
            return;
        }

        long legacyExpiry = System.currentTimeMillis() + LEGACY_BOOSTER_DURATION_MILLIS;
        for (String uuid : players.getKeys(false)) {
            String base = "Players." + uuid + ".Booster";
            boolean x4 = boostersConfig.getBoolean(base + ".x4");
            boolean x3 = boostersConfig.getBoolean(base + ".x3");
            boolean x2 = boostersConfig.getBoolean(base + ".x2");
            int multiplier = x4 ? 4 : x3 ? 3 : x2 ? 2 : 1;

            if (multiplier == 1) {
                continue;
            }

            // Normalize corrupted/multiple legacy flags without losing the
            // strongest active booster.
            boostersConfig.set(base + ".x2", multiplier == 2);
            boostersConfig.set(base + ".x3", multiplier == 3);
            boostersConfig.set(base + ".x4", multiplier == 4);
            if (!boostersConfig.isLong(base + ".ExpiresAt")
                    || boostersConfig.getLong(base + ".ExpiresAt") <= 0L) {
                boostersConfig.set(base + ".ExpiresAt", legacyExpiry);
            }
        }
    }

    private static void migrateLegacyTraders(
            YamlConfiguration tradeConfig,
            Plugin plugin) throws MigrationException {
        ConfigurationSection traders = tradeConfig.getConfigurationSection("traders");
        if (traders == null) {
            tradeConfig.set("TradersList", List.of());
            return;
        }

        Map<String, Map<String, Object>> migrated = new LinkedHashMap<>();
        Map<String, String> renamed = new LinkedHashMap<>();
        Set<String> usedIds = new LinkedHashSet<>();

        for (String oldId : new ArrayList<>(traders.getKeys(false))) {
            ConfigurationSection section = traders.getConfigurationSection(oldId);
            if (section == null) {
                continue;
            }

            String newId = createSafeTraderId(oldId, usedIds);
            usedIds.add(newId.toLowerCase(Locale.ROOT));
            if (!newId.equals(oldId)) {
                renamed.put(oldId, newId);
            }

            Map<String, Object> values = sectionToPlainMap(section);
            Object rawLocation = values.get("location");
            if (rawLocation instanceof String location) {
                values.put("location", migrateLegacyTraderLocation(location));
            }
            migrated.put(newId, values);
        }

        tradeConfig.set("traders", null);
        for (Map.Entry<String, Map<String, Object>> entry : migrated.entrySet()) {
            tradeConfig.set("traders." + entry.getKey(), entry.getValue());
        }
        tradeConfig.set("TradersList", new ArrayList<>(migrated.keySet()));

        for (Map.Entry<String, String> rename : renamed.entrySet()) {
            plugin.getLogger().warning(
                    "Migrated legacy trader ID '" + rename.getKey() + "' to safe ID '"
                            + rename.getValue() + "'.");
        }
    }

    private static String migrateLegacyTraderLocation(String location) throws MigrationException {
        String[] parts = location.split(",");
        if (parts.length == 6) {
            return location;
        }
        if (parts.length != 4) {
            throw new MigrationException("Invalid legacy trader location: " + location);
        }

        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new NumberFormatException("non-finite coordinate");
            }
            // Old TradeManager saved block coordinates and spawned at the
            // block center. Preserve that exact effective location.
            return parts[0] + "," + (x + 0.5D) + "," + y + "," + (z + 0.5D)
                    + ",0.0,0.0";
        } catch (NumberFormatException exception) {
            throw new MigrationException("Invalid legacy trader location: " + location, exception);
        }
    }

    private static Map<String, Object> sectionToPlainMap(ConfigurationSection section) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection child) {
                result.put(key, sectionToPlainMap(child));
            } else if (value instanceof List<?> list) {
                result.put(key, deepCopyList(list));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    private static List<Object> deepCopyList(List<?> source) {
        List<Object> result = new ArrayList<>(source.size());
        for (Object value : source) {
            if (value instanceof ConfigurationSection section) {
                result.add(sectionToPlainMap(section));
            } else if (value instanceof Map<?, ?> map) {
                Map<String, Object> copied = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    copied.put(String.valueOf(entry.getKey()), deepCopyValue(entry.getValue()));
                }
                result.add(copied);
            } else if (value instanceof List<?> list) {
                result.add(deepCopyList(list));
            } else {
                result.add(value);
            }
        }
        return result;
    }

    private static Object deepCopyValue(Object value) {
        if (value instanceof ConfigurationSection section) {
            return sectionToPlainMap(section);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copied = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copied.put(String.valueOf(entry.getKey()), deepCopyValue(entry.getValue()));
            }
            return copied;
        }
        if (value instanceof List<?> list) {
            return deepCopyList(list);
        }
        return value;
    }

    private static String createSafeTraderId(String original, Set<String> usedLowercase) {
        String cleaned = original == null ? "" : original.trim()
                .replaceAll("[^A-Za-z0-9_-]+", "_")
                .replaceAll("_+", "_");
        cleaned = cleaned.replaceAll("^[_-]+|[_-]+$", "");
        if (cleaned.isBlank()) {
            cleaned = "trader";
        }
        if (cleaned.length() > 32) {
            cleaned = cleaned.substring(0, 32);
        }

        String candidate = cleaned;
        int suffix = 2;
        while (usedLowercase.contains(candidate.toLowerCase(Locale.ROOT))) {
            String suffixText = "_" + suffix++;
            int maxBase = Math.max(1, 32 - suffixText.length());
            String base = cleaned.length() > maxBase ? cleaned.substring(0, maxBase) : cleaned;
            candidate = base + suffixText;
        }
        return candidate;
    }

    private static void setIfMissing(YamlConfiguration config, String path, Object value) {
        if (!config.contains(path)) {
            config.set(path, value);
        }
    }

    private static String canonicalJob(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "farmer" -> "Farmer";
            case "miner" -> "Miner";
            case "woodcutter" -> "Woodcutter";
            case "smith" -> "Smith";
            case "hunter" -> "Hunter";
            case "fisherman" -> "Fisherman";
            default -> null;
        };
    }

    private static void commitStagedData(
            Path stagingData,
            Path liveData,
            Path backupFolder) throws IOException {
        Files.createDirectories(liveData);

        try {
            for (String name : KNOWN_DATA_FILES) {
                Path staged = stagingData.resolve(name);
                if (!Files.exists(staged)) {
                    continue;
                }
                Path live = liveData.resolve(name);
                Path temp = live.resolveSibling(name + ".migration-commit.tmp");
                Files.copy(staged, temp, StandardCopyOption.REPLACE_EXISTING);
                try {
                    try {
                        Files.move(temp, live,
                                StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING);
                    } catch (AtomicMoveNotSupportedException exception) {
                        Files.move(temp, live, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(temp);
                }
            }
        } catch (IOException commitFailure) {
            try {
                DataBackupManager.restoreDataDirectory(backupFolder, liveData.getParent());
            } catch (IOException rollbackFailure) {
                commitFailure.addSuppressed(rollbackFailure);
            }
            throw commitFailure;
        }
    }

    private static Path safeEmergencyBackup(Plugin plugin, Path sourcePluginFolder, int fromVersion) {
        try {
            return DataBackupManager.createPreMigrationBackup(
                    sourcePluginFolder,
                    plugin.getDataFolder().toPath().toAbsolutePath().normalize(),
                    fromVersion,
                    DataSchema.CURRENT_VERSION);
        } catch (IOException backupFailure) {
            plugin.getLogger().log(Level.SEVERE,
                    "An emergency backup could not be created.", backupFailure);
            return null;
        }
    }

    private static void logSnapshot(Plugin plugin, String label, IntegritySnapshot snapshot) {
        plugin.getLogger().info(label + ": " + snapshot.summary());
    }

    private static String formatPath(Path path) {
        return path == null ? "<backup unavailable>" : path.toAbsolutePath().toString();
    }

    public record MigrationResult(
            boolean success,
            boolean migrated,
            Path backupFolder,
            int fromVersion,
            int toVersion,
            String message) {

        static MigrationResult success(
                boolean migrated,
                Path backup,
                int from,
                int to,
                String message) {
            return new MigrationResult(true, migrated, backup, from, to, message);
        }

        static MigrationResult failure(
                Path backup,
                int from,
                int to,
                String message) {
            return new MigrationResult(false, false, backup, from, to, message);
        }
    }

    private static final class MigrationException extends Exception {
        private static final long serialVersionUID = 1L;

        MigrationException(String message) {
            super(message);
        }

        MigrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Critical invariants that must never decrease/change during automatic
     * migration. New fields may increase counts, but existing economy values
     * and stored game objects must be preserved.
     */
    private record IntegritySnapshot(
            Map<String, Long> playerBalances,
            Map<String, Long> guildBalances,
            long players,
            long homePlayers,
            long homes,
            long guilds,
            long claims,
            long traders,
            long trades,
            long shopEntries,
            long lootRewards) {

        static IntegritySnapshot capture(Map<String, YamlConfiguration> configs) {
            YamlConfiguration playersConfig = configs.get("PlayerData.yml");
            YamlConfiguration guildConfig = configs.get("GuildData.yml");
            YamlConfiguration homeConfig = configs.get("HomeData.yml");
            YamlConfiguration chunkConfig = configs.get("ChunkData.yml");
            YamlConfiguration tradeConfig = configs.get("TradeData.yml");
            YamlConfiguration shopConfig = configs.get("ShopData.yml");
            YamlConfiguration lootConfig = configs.get("LootcrateData.yml");

            Map<String, Long> playerBalances = new LinkedHashMap<>();
            ConfigurationSection playerSection = playersConfig.getConfigurationSection("Players");
            if (playerSection != null) {
                for (String uuid : playerSection.getKeys(false)) {
                    playerBalances.put(uuid, playersConfig.getLong("Players." + uuid + ".Bal", 0L));
                }
            }

            Map<String, Long> guildBalances = new LinkedHashMap<>();
            ConfigurationSection guildSection = guildConfig.getConfigurationSection("Guild");
            if (guildSection != null) {
                for (String guild : guildSection.getKeys(false)) {
                    guildBalances.put(guild, guildConfig.getLong("Guild." + guild + ".Balance", 0L));
                }
            }

            long homePlayers = 0;
            long homes = 0;
            for (String player : homeConfig.getKeys(false)) {
                ConfigurationSection storedHomes = homeConfig.getConfigurationSection(player + ".Homes");
                if (storedHomes != null) {
                    homePlayers++;
                    homes += storedHomes.getKeys(false).size();
                }
            }

            long traders = 0;
            long trades = 0;
            ConfigurationSection traderSection = tradeConfig.getConfigurationSection("traders");
            if (traderSection != null) {
                traders = traderSection.getKeys(false).size();
                for (String trader : traderSection.getKeys(false)) {
                    trades += tradeConfig.getMapList("traders." + trader + ".trades").size();
                }
            }

            long shopEntries = shopConfig.getKeys(true).stream()
                    .filter(key -> key.matches("(?i).*Contents\\d+$"))
                    .filter(shopConfig::isItemStack)
                    .count();

            return new IntegritySnapshot(
                    Map.copyOf(playerBalances),
                    Map.copyOf(guildBalances),
                    playerBalances.size(),
                    homePlayers,
                    homes,
                    guildBalances.size(),
                    chunkConfig.getMapList("claims").size(),
                    traders,
                    trades,
                    shopEntries,
                    lootConfig.getStringList("Rewards").size());
        }

        void verifyCriticalDataPreserved(IntegritySnapshot after) throws MigrationException {
            if (!playerBalances.equals(after.playerBalances)) {
                throw new MigrationException("Player balances changed during migration.");
            }
            if (!guildBalances.equals(after.guildBalances)) {
                throw new MigrationException("Guild balances changed during migration.");
            }
            requireNotLower("players", players, after.players);
            requireNotLower("players with homes", homePlayers, after.homePlayers);
            requireNotLower("homes", homes, after.homes);
            requireNotLower("guilds", guilds, after.guilds);
            requireNotLower("chunk claims", claims, after.claims);
            requireNotLower("traders", traders, after.traders);
            requireNotLower("trades", trades, after.trades);
            requireNotLower("shop entries", shopEntries, after.shopEntries);
            requireNotLower("loot rewards", lootRewards, after.lootRewards);
        }

        String summary() {
            return "players=" + players
                    + ", homes=" + homes
                    + ", guilds=" + guilds
                    + ", claims=" + claims
                    + ", traders=" + traders
                    + ", trades=" + trades
                    + ", shopEntries=" + shopEntries
                    + ", lootRewards=" + lootRewards;
        }

        private static void requireNotLower(String label, long before, long after)
                throws MigrationException {
            if (after < before) {
                throw new MigrationException(
                        "Integrity check failed: " + label + " decreased from "
                                + before + " to " + after + ".");
            }
        }
    }
}
