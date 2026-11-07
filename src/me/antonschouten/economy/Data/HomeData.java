package me.antonschouten.economy.Data;

public final class HomeData extends AbstractYamlData {

    private static final HomeData INSTANCE = new HomeData();

    private HomeData() {
        super("HomeData.yml");
    }

    public static HomeData getInstance() {
        return INSTANCE;
    }
}
