package me.antonschouten.economy.Data;

import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;

public final class LootcrateData extends AbstractYamlData {

    private static final LootcrateData INSTANCE = new LootcrateData();

    private LootcrateData() {
        super("LootcrateData.yml");
    }

    public static LootcrateData getInstance() {
        return INSTANCE;
    }

    @Override
    protected boolean applyDefaults(YamlConfiguration config) {
        boolean changed = false;
        changed |= ensureList(config, "Rewards", List.of("DIAMOND", "STONE"));
        changed |= ensureList(config, "CrateBlocks", List.of());
        return changed;
    }
}
