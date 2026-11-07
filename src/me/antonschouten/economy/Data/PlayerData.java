package me.antonschouten.economy.Data;

public final class PlayerData extends AbstractYamlData {

    private static final PlayerData INSTANCE = new PlayerData();

    private PlayerData() {
        super("PlayerData.yml");
    }

    public static PlayerData getInstance() {
        return INSTANCE;
    }
}
