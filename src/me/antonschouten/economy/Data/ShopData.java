package me.antonschouten.economy.Data;

public final class ShopData extends AbstractYamlData {

    private static final ShopData INSTANCE = new ShopData();

    private ShopData() {
        super("ShopData.yml");
    }

    public static ShopData getInstance() {
        return INSTANCE;
    }
}
