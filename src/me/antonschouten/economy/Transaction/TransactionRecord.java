package me.antonschouten.economy.Transaction;

import java.util.Objects;
import java.util.UUID;

public record TransactionRecord(
        UUID id,
        long timestamp,
        TransactionReason reason,
        int amount,
        TransactionParty from,
        TransactionParty to,
        String actorType,
        String actorId,
        String actorName,
        String context) {

    public TransactionRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (amount < 0) {
            throw new IllegalArgumentException("amount cannot be negative");
        }
        actorType = clean(actorType, "SYSTEM");
        actorId = clean(actorId, "system");
        actorName = clean(actorName, "System");
        context = context == null ? "" : context;
    }

    public static TransactionRecord create(
            TransactionReason reason,
            int amount,
            TransactionParty from,
            TransactionParty to,
            String actorType,
            String actorId,
            String actorName,
            String context) {
        return new TransactionRecord(
                UUID.randomUUID(),
                System.currentTimeMillis(),
                reason,
                amount,
                from,
                to,
                actorType,
                actorId,
                actorName,
                context);
    }

    public Integer deltaFor(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        Integer delta = deltaFor(from, playerUuid);
        if (delta != null) {
            return delta;
        }
        return deltaFor(to, playerUuid);
    }

    public TransactionParty partyFor(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        if (playerUuid.equals(from.playerUuid())) {
            return from;
        }
        if (playerUuid.equals(to.playerUuid())) {
            return to;
        }
        return null;
    }

    public TransactionParty counterpartyFor(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        if (playerUuid.equals(from.playerUuid())) {
            return to;
        }
        if (playerUuid.equals(to.playerUuid())) {
            return from;
        }
        return null;
    }

    private static Integer deltaFor(TransactionParty party, UUID playerUuid) {
        if (!playerUuid.equals(party.playerUuid())
                || party.balanceBefore() == null
                || party.balanceAfter() == null) {
            return null;
        }
        return party.balanceAfter() - party.balanceBefore();
    }

    private static String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
