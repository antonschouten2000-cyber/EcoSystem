package me.antonschouten.economy.Data;

/** Persistent storage for Economy++ spawn, warps and /back locations. */
public final class TeleportData extends AbstractYamlData {

    private static final TeleportData INSTANCE = new TeleportData();

    private TeleportData() {
        super("TeleportData.yml");
    }

    public static TeleportData getInstance() {
        return INSTANCE;
    }
}
