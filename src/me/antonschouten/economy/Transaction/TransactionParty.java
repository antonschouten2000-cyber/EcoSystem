package me.antonschouten.economy.Transaction;

import java.util.Objects;
import java.util.UUID;

public record TransactionParty(
        TransactionAccountType type,
        String id,
        String name,
        Integer balanceBefore,
        Integer balanceAfter) {

    public TransactionParty {
        Objects.requireNonNull(type, "type");
        id = id == null ? "" : id;
        name = name == null ? "" : name;
    }

    public static TransactionParty player(UUID uuid, String name, int before, int after) {
        Objects.requireNonNull(uuid, "uuid");
        return new TransactionParty(TransactionAccountType.PLAYER, uuid.toString(), name, before, after);
    }

    public static TransactionParty guild(String guild, int before, int after) {
        return new TransactionParty(TransactionAccountType.GUILD, guild, guild, before, after);
    }

    public static TransactionParty serverBank(int before, int after) {
        return new TransactionParty(TransactionAccountType.SERVER_BANK, "server-bank", "Server Bank", before, after);
    }

    public static TransactionParty system(String name) {
        return new TransactionParty(TransactionAccountType.SYSTEM, "system", name == null ? "System" : name, null, null);
    }

    public static TransactionParty cheque(String chequeId, String name) {
        return new TransactionParty(TransactionAccountType.CHEQUE, chequeId, name == null ? "Cheque" : name, null, null);
    }

    public UUID playerUuid() {
        if (type != TransactionAccountType.PLAYER) {
            return null;
        }
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
