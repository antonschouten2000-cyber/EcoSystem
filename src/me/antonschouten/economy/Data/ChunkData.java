package me.antonschouten.economy.Data;

public final class ChunkData extends AbstractYamlData {

    private static final ChunkData INSTANCE = new ChunkData();

    private ChunkData() {
        super("ChunkData.yml");
    }

    public static ChunkData getInstance() {
        return INSTANCE;
    }
}
