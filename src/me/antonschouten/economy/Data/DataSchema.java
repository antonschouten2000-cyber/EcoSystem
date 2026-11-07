package me.antonschouten.economy.Data;

/**
 * Central schema version for Economy++ persistent YAML data.
 *
 * <p>Increase this only when a release changes persistent data in a way that
 * requires a controlled migration. New installations are created directly at
 * the current version; older installations are migrated before DataFiles are
 * initialized.</p>
 */
public final class DataSchema {

    public static final int CURRENT_VERSION = 2;
    public static final String VERSION_KEY = "DataSchemaVersion";

    private DataSchema() {
    }
}
