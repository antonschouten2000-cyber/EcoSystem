package me.antonschouten.economy.Data;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.plugin.Plugin;

/** Central registry for every Economy++ YAML data file. */
public final class DataFiles {

    private static final List<AbstractYamlData> ALL = List.of(
            ConfigData.getInstance(),
            PlayerData.getInstance(),
            BoosterData.getInstance(),
            LootcrateData.getInstance(),
            ShopData.getInstance(),
            HomeData.getInstance(),
            GuildData.getInstance(),
            ChunkData.getInstance(),
            TradeData.getInstance(),
            TeleportData.getInstance()
    );

    private DataFiles() {
    }

    public static void setupAll(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        for (AbstractYamlData dataFile : ALL) {
            dataFile.setup(plugin);
        }
    }

    /**
     * Reloads all files as one logical operation. Every file is parsed first;
     * if even one YAML file is malformed, none of the in-memory files are
     * replaced.
     */
    public static boolean reloadAll(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");

        Map<AbstractYamlData, AbstractYamlData.PreparedData> prepared = new LinkedHashMap<>();
        for (AbstractYamlData dataFile : ALL) {
            try {
                prepared.put(dataFile, dataFile.prepareForRegistryReload());
            } catch (IOException | InvalidConfigurationException | RuntimeException exception) {
                plugin.getLogger().severe(
                        "Economy++ data reload was cancelled because Data/"
                                + dataFile.getFileName() + " could not be loaded: " + exception.getMessage()
                );
                return false;
            }
        }

        boolean savedDefaultsSuccessfully = true;
        for (Map.Entry<AbstractYamlData, AbstractYamlData.PreparedData> entry : prepared.entrySet()) {
            if (!entry.getKey().commitRegistryReload(entry.getValue())) {
                savedDefaultsSuccessfully = false;
            }
        }

        if (!savedDefaultsSuccessfully) {
            plugin.getLogger().warning(
                    "All Economy++ YAML files were reloaded, but one or more normalized defaults could not be saved."
            );
        }
        return true;
    }

    public static List<AbstractYamlData> all() {
        return ALL;
    }
}
