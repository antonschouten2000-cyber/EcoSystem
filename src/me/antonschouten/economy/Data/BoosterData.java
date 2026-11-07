package me.antonschouten.economy.Data;

public final class BoosterData extends AbstractYamlData {

    private static final BoosterData INSTANCE = new BoosterData();

    private BoosterData() {
        super("BoosterData.yml");
    }

    public static BoosterData getInstance() {
        return INSTANCE;
    }
}
