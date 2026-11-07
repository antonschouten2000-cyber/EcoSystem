package me.antonschouten.economy.Listeners.Lootcrate;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.block.Block;

import me.antonschouten.economy.Data.LootcrateData;
import me.antonschouten.economy.Manager.DataSaveManager;

final class LootcrateBlockRegistry {

    private static final String PATH = "CrateBlocks";

    private LootcrateBlockRegistry() {
    }

    static boolean contains(Block block) {
        if (block == null) {
            return false;
        }
        return LootcrateData.getInstance().getData().getStringList(PATH).contains(key(block));
    }

    static void add(Block block) {
        if (block == null) {
            return;
        }

        LootcrateData data = LootcrateData.getInstance();
        List<String> blocks = new ArrayList<>(data.getData().getStringList(PATH));
        String key = key(block);
        if (blocks.contains(key)) {
            return;
        }
        blocks.add(key);
        data.getData().set(PATH, blocks);
        // Setting a crate is an admin action and rare, so persist immediately.
        DataSaveManager.saveNow(DataSaveManager.Target.LOOTCRATE);
    }

    private static String key(Block block) {
        return block.getWorld().getUID() + ";" + block.getX() + ";" + block.getY() + ";" + block.getZ();
    }
}
