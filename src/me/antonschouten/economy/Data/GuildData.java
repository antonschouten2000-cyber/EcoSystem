package me.antonschouten.economy.Data;

public final class GuildData extends AbstractYamlData {

    private static final GuildData INSTANCE = new GuildData();

    private GuildData() {
        super("GuildData.yml");
    }

    public static GuildData getInstance() {
        return INSTANCE;
    }
}
