package me.antonschouten.economy.Data;

public final class TradeData extends AbstractYamlData {

    private static final TradeData INSTANCE = new TradeData();

    private TradeData() {
        super("TradeData.yml");
    }

    public static TradeData getInstance() {
        return INSTANCE;
    }
}
