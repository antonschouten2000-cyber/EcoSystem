package me.antonschouten.economy.Transaction;

public enum TransactionReason {
    PLAYER_PAYMENT("Player payment"),
    PAY_ALL("Pay all"),
    ADMIN_SET("Admin set balance"),
    ADMIN_ADD("Admin add balance"),
    ADMIN_REMOVE("Admin remove balance"),
    ADMIN_RESET("Admin reset balance"),
    ADMIN_RESET_ALL("Admin reset all"),
    JOB_REWARD("Job reward"),
    JOB_CHANGE("Job change"),
    SALARY("Salary"),
    FIRST_JOIN_REWARD("First join reward"),
    JOIN_REWARD("Join reward"),
    SHOP_PURCHASE("Shop purchase"),
    LOOTCRATE_PURCHASE("Lootcrate purchase"),
    BOOSTER_PURCHASE("Booster purchase"),
    CHUNK_CLAIM("Chunk claim"),
    REPAIR("Repair"),
    CHEQUE_CREATE("Cheque created"),
    CHEQUE_REDEEM("Cheque redeemed"),
    GIFT_FEE("Gift fee"),
    GUILD_DEPOSIT("Guild deposit"),
    GUILD_WITHDRAW("Guild withdraw"),
    SERVER_BANK_PAYOUT("Server bank payout"),
    SYSTEM_ADJUSTMENT("System adjustment");

    private final String displayName;

    TransactionReason(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
