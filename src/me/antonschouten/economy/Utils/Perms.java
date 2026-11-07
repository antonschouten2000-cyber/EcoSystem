package me.antonschouten.economy.Utils;

/**
 * Central permission-node registry for Economy++.
 *
 * <p>All permission strings used by the plugin should be declared here so
 * they stay in sync with plugin.yml and are not duplicated throughout the
 * codebase.</p>
 *
 * <p>The deprecated aliases at the bottom are intentionally kept for
 * backwards compatibility with existing Economy++ source code. New code
 * should use the uppercase constants.</p>
 */
public final class Perms {

    private Perms() {
        throw new IllegalStateException("Utility class");
    }

    /* ---------------------------------------------------------
     * Permission groups
     * --------------------------------------------------------- */

    public static final String ALL = "economy++.*";
    public static final String PLAYER = "economy++.player";
    public static final String ADMIN = "economy++.admin";

    /* ---------------------------------------------------------
     * Balance / account administration
     * --------------------------------------------------------- */

    public static final String SET_BALANCE = "economy++.setbal";
    public static final String ADD_BALANCE = "economy++.addbal";
    public static final String REMOVE_BALANCE = "economy++.removebal";
    public static final String VIEW_OTHER_BALANCE = "economy++.getbaltarget";
    public static final String RESET_BALANCE = "economy++.resetbal";
    public static final String RESET_ALL_BALANCES = "economy++.resetbalall";
    public static final String TRANSACTIONS = "economy++.transactions";
    public static final String TRANSACTIONS_OTHERS = "economy++.transactions.others";

    /* ---------------------------------------------------------
     * Jobs
     * --------------------------------------------------------- */

    public static final String RESET_ALL_JOBS = "economy++.resetjobs";
    public static final String DELETE_JOB = "economy++.deletejob";

    /* ---------------------------------------------------------
     * General / administration
     * --------------------------------------------------------- */

    public static final String HELP = "economy++.help";
    public static final String RELOAD = "economy++.reload";
    public static final String SET_WORLD = "economy++.setworld";
    public static final String DEBUG = "economy++.debug";
    public static final String UPDATE_NOTIFICATION = "economy++.update";
    public static final String ADMIN_PANEL = "economy++.adminpanel";

    /* ---------------------------------------------------------
     * Gameplay / feature administration
     * --------------------------------------------------------- */

    public static final String SIGNS = "economy++.signs";
    public static final String LOOTCRATE_CREATE = "economy++.cratecreate";
    public static final String SHOP_EDIT = "economy++.shop";
    public static final String HOME = "economy++.home";
    public static final String CHUNK = "economy++.chunk";
    public static final String TRADE_NPC = "economy++.tradenpc";

    /* ---------------------------------------------------------
     * Teleport Core
     * --------------------------------------------------------- */

    public static final String SPAWN = "economy++.spawn";
    public static final String SET_SPAWN = "economy++.setspawn";
    public static final String WARP = "economy++.warp";
    public static final String WARP_ADMIN = "economy++.warp.admin";
    public static final String TPA = "economy++.tpa";
    public static final String TPA_HERE = "economy++.tpahere";
    public static final String BACK = "economy++.back";
    public static final String TELEPORT_BYPASS_DELAY = "economy++.teleport.bypass.delay";
    public static final String TELEPORT_BYPASS_COOLDOWN = "economy++.teleport.bypass.cooldown";
    
     /* ---------------------------------------------------------
     * Teleport Core
     * --------------------------------------------------------- */
    
    public static final String AUCTION = "economy++.auction";

    /* ---------------------------------------------------------
     * Legacy field aliases
     * ---------------------------------------------------------
     *
     * Kept so the existing Economy++ command/listener source continues to
     * compile without requiring an all-at-once rename. These can be removed
     * in a future major version after every caller uses the constants above.
     */

    /** @deprecated Use {@link #SET_BALANCE}. */
    @Deprecated
    public static final String setBal = SET_BALANCE;

    /** @deprecated Use {@link #ADD_BALANCE}. */
    @Deprecated
    public static final String addBal = ADD_BALANCE;

    /** @deprecated Use {@link #REMOVE_BALANCE}. */
    @Deprecated
    public static final String removeBal = REMOVE_BALANCE;

    /** @deprecated Use {@link #VIEW_OTHER_BALANCE}. */
    @Deprecated
    public static final String getBalTarget = VIEW_OTHER_BALANCE;

    /** @deprecated Use {@link #RESET_BALANCE}. */
    @Deprecated
    public static final String resetBal = RESET_BALANCE;

    /** @deprecated Use {@link #RESET_ALL_BALANCES}. */
    @Deprecated
    public static final String resetBallAll = RESET_ALL_BALANCES;

    /** @deprecated Use {@link #RESET_ALL_JOBS}. */
    @Deprecated
    public static final String resetJobs = RESET_ALL_JOBS;

    /** @deprecated Use {@link #HELP}. */
    @Deprecated
    public static final String acces = HELP;

    /** @deprecated Use {@link #RELOAD}. */
    @Deprecated
    public static final String reload = RELOAD;

    /** @deprecated Use {@link #SET_WORLD}. */
    @Deprecated
    public static final String setWorld = SET_WORLD;

    /** @deprecated Use {@link #DELETE_JOB}. */
    @Deprecated
    public static final String deleteJob = DELETE_JOB;

    /** @deprecated Use {@link #DEBUG}. */
    @Deprecated
    public static final String debugPlugin = DEBUG;

    /** @deprecated Use {@link #UPDATE_NOTIFICATION}. */
    @Deprecated
    public static final String updateMessage = UPDATE_NOTIFICATION;

    /** @deprecated Use {@link #ADMIN_PANEL}. */
    @Deprecated
    public static final String adminPanel = ADMIN_PANEL;

    /** @deprecated Use {@link #SIGNS}. */
    @Deprecated
    public static final String signs = SIGNS;

    /** @deprecated Use {@link #LOOTCRATE_CREATE}. */
    @Deprecated
    public static final String lootcrateCreate = LOOTCRATE_CREATE;

    /** @deprecated Use {@link #SHOP_EDIT}. */
    @Deprecated
    public static final String ShopEdit = SHOP_EDIT;

    /** @deprecated Use {@link #HOME}. */
    @Deprecated
    public static final String Home = HOME;

    /** @deprecated Use {@link #CHUNK}. */
    @Deprecated
    public static final String Chunk = CHUNK;

    /** @deprecated Use {@link #TRADE_NPC}. */
    @Deprecated
    public static final String TradeNPC = TRADE_NPC;
}
