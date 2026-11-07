package me.antonschouten.economy.Transaction;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import org.bukkit.plugin.java.JavaPlugin;

import me.antonschouten.economy.Data.ConfigData;

public final class TransactionManager {

    private static final String FILE_PREFIX = "transactions-";
    private static final String FILE_SUFFIX = ".log";
    private static final String FORMAT_VERSION = "1";
    private static final int QUEUE_CAPACITY = 100_000;
    private static final int MAX_BATCH_SIZE = 256;
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private static final Object CACHE_LOCK = new Object();
    private static final Deque<TransactionRecord> GLOBAL_CACHE = new ArrayDeque<>();
    private static final Map<UUID, Deque<TransactionRecord>> PLAYER_CACHE = new HashMap<>();
    private static final ArrayBlockingQueue<TransactionRecord> WRITE_QUEUE = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private static JavaPlugin plugin;
    private static Path transactionDirectory;
    private static ExecutorService writer;

    private static volatile boolean enabled = true;
    private static volatile boolean logJobRewards = true;
    private static volatile int retentionDays = 30;
    private static volatile int globalCacheLimit = 5_000;
    private static volatile int playerCacheLimit = 250;
    private static volatile int pageSize = 8;

    private TransactionManager() {
    }

    public static void initialize(JavaPlugin owningPlugin) {
        if (owningPlugin == null) {
            throw new IllegalArgumentException("owningPlugin cannot be null");
        }
        shutdown();

        plugin = owningPlugin;
        transactionDirectory = owningPlugin.getDataFolder().toPath().resolve("Transactions");
        try {
            Files.createDirectories(transactionDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create transaction directory", exception);
        }

        reloadSettings();
        cleanupExpiredFiles();
        loadRecentCache();

        RUNNING.set(true);
        writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Economy++-TransactionWriter");
            thread.setDaemon(true);
            return thread;
        });
        writer.execute(TransactionManager::writerLoop);
    }

    public static void reloadSettings() {
        ConfigData data = ConfigData.getInstance();
        enabled = data.getData().getBoolean("Transactions.Enabled", true);
        logJobRewards = data.getData().getBoolean("Transactions.LogJobRewards", true);
        retentionDays = bounded(data.getData().getInt("Transactions.RetentionDays", 30), 1, 3650);
        globalCacheLimit = bounded(data.getData().getInt("Transactions.Cache.Global", 5000), 100, 100_000);
        playerCacheLimit = bounded(data.getData().getInt("Transactions.Cache.PerPlayer", 250), 25, 10_000);
        pageSize = bounded(data.getData().getInt("Transactions.PageSize", 8), 1, 20);
        trimCaches();
        cleanupExpiredFiles();
    }

    public static void record(TransactionRecord record) {
        if (record == null || !enabled || !RUNNING.get()) {
            return;
        }
        if (record.reason() == TransactionReason.JOB_REWARD && !logJobRewards) {
            return;
        }

        cache(record);
        if (!WRITE_QUEUE.offer(record)) {
            JavaPlugin currentPlugin = plugin;
            if (currentPlugin != null) {
                currentPlugin.getLogger().severe(
                        "Transaction write queue is full. Transaction " + record.id() + " could not be persisted.");
            }
        }
    }

    public static List<TransactionRecord> getForPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return List.of();
        }
        synchronized (CACHE_LOCK) {
            Deque<TransactionRecord> deque = PLAYER_CACHE.get(playerUuid);
            if (deque == null || deque.isEmpty()) {
                return List.of();
            }
            List<TransactionRecord> result = new ArrayList<>(deque);
            result.sort(Comparator.comparingLong(TransactionRecord::timestamp).reversed());
            return result;
        }
    }

    public static List<TransactionRecord> getRecentGlobal() {
        synchronized (CACHE_LOCK) {
            List<TransactionRecord> result = new ArrayList<>(GLOBAL_CACHE);
            result.sort(Comparator.comparingLong(TransactionRecord::timestamp).reversed());
            return result;
        }
    }

    public static int getPageSize() {
        return pageSize;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static Path getTransactionDirectory() {
        return transactionDirectory;
    }

    public static void shutdown() {
        RUNNING.set(false);

        ExecutorService currentWriter = writer;
        writer = null;
        if (currentWriter != null) {
            currentWriter.shutdown();
            try {
                if (!currentWriter.awaitTermination(10, TimeUnit.SECONDS)) {
                    currentWriter.shutdownNow();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                currentWriter.shutdownNow();
            }
        }

        if (!WRITE_QUEUE.isEmpty()) {
            List<TransactionRecord> remaining = new ArrayList<>();
            WRITE_QUEUE.drainTo(remaining);
            if (!remaining.isEmpty()) {
                try {
                    writeBatch(remaining);
                } catch (IOException exception) {
                    JavaPlugin currentPlugin = plugin;
                    if (currentPlugin != null) {
                        currentPlugin.getLogger().log(Level.SEVERE,
                                "Could not flush remaining Economy++ transactions during shutdown.", exception);
                    }
                }
            }
        }
    }

    private static void writerLoop() {
        while (RUNNING.get() || !WRITE_QUEUE.isEmpty()) {
            try {
                TransactionRecord first = WRITE_QUEUE.poll(500, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }

                List<TransactionRecord> batch = new ArrayList<>(MAX_BATCH_SIZE);
                batch.add(first);
                WRITE_QUEUE.drainTo(batch, MAX_BATCH_SIZE - 1);
                writeBatch(batch);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException | RuntimeException exception) {
                JavaPlugin currentPlugin = plugin;
                if (currentPlugin != null) {
                    currentPlugin.getLogger().log(Level.SEVERE,
                            "Could not persist an Economy++ transaction batch.", exception);
                }
            }
        }
    }

    private static void writeBatch(List<TransactionRecord> records) throws IOException {
        if (records == null || records.isEmpty() || transactionDirectory == null) {
            return;
        }

        Map<LocalDate, StringBuilder> byDate = new LinkedHashMap<>();
        ZoneId zone = ZoneId.systemDefault();
        for (TransactionRecord record : records) {
            LocalDate date = Instant.ofEpochMilli(record.timestamp()).atZone(zone).toLocalDate();
            byDate.computeIfAbsent(date, ignored -> new StringBuilder())
                    .append(serialize(record))
                    .append(System.lineSeparator());
        }

        for (Map.Entry<LocalDate, StringBuilder> entry : byDate.entrySet()) {
            Path file = transactionDirectory.resolve(FILE_PREFIX + FILE_DATE.format(entry.getKey()) + FILE_SUFFIX);
            Files.writeString(
                    file,
                    entry.getValue().toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND);
        }
    }

    private static void cache(TransactionRecord record) {
        synchronized (CACHE_LOCK) {
            GLOBAL_CACHE.addLast(record);
            while (GLOBAL_CACHE.size() > globalCacheLimit) {
                GLOBAL_CACHE.removeFirst();
            }

            cacheForParty(record, record.from());
            UUID fromUuid = record.from().playerUuid();
            UUID toUuid = record.to().playerUuid();
            if (toUuid != null && !toUuid.equals(fromUuid)) {
                cacheForParty(record, record.to());
            }
        }
    }

    private static void cacheForParty(TransactionRecord record, TransactionParty party) {
        UUID uuid = party.playerUuid();
        if (uuid == null) {
            return;
        }
        Deque<TransactionRecord> deque = PLAYER_CACHE.computeIfAbsent(uuid, ignored -> new ArrayDeque<>());
        deque.addLast(record);
        while (deque.size() > playerCacheLimit) {
            deque.removeFirst();
        }
    }

    private static void trimCaches() {
        synchronized (CACHE_LOCK) {
            while (GLOBAL_CACHE.size() > globalCacheLimit) {
                GLOBAL_CACHE.removeFirst();
            }
            PLAYER_CACHE.values().forEach(deque -> {
                while (deque.size() > playerCacheLimit) {
                    deque.removeFirst();
                }
            });
        }
    }

    private static void loadRecentCache() {
        synchronized (CACHE_LOCK) {
            GLOBAL_CACHE.clear();
            PLAYER_CACHE.clear();
        }
        if (transactionDirectory == null || !Files.isDirectory(transactionDirectory)) {
            return;
        }

        List<Path> files = transactionFilesNewestFirst();
        List<TransactionRecord> loaded = new ArrayList<>();
        for (Path file : files) {
            List<TransactionRecord> fileRecords = readFileTail(file, globalCacheLimit);
            if (!fileRecords.isEmpty()) {
                loaded.addAll(0, fileRecords);
                while (loaded.size() > globalCacheLimit) {
                    loaded.remove(0);
                }
            }
            if (loaded.size() >= globalCacheLimit) {
                break;
            }
        }

        loaded.sort(Comparator.comparingLong(TransactionRecord::timestamp));
        for (TransactionRecord record : loaded) {
            cache(record);
        }
    }

    private static List<TransactionRecord> readFileTail(Path file, int limit) {
        Deque<TransactionRecord> records = new ArrayDeque<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                TransactionRecord record = deserialize(line);
                if (record == null) {
                    continue;
                }
                records.addLast(record);
                while (records.size() > limit) {
                    records.removeFirst();
                }
            }
        } catch (IOException exception) {
            JavaPlugin currentPlugin = plugin;
            if (currentPlugin != null) {
                currentPlugin.getLogger().warning("Could not load transaction cache from " + file.getFileName() + ": "
                        + exception.getMessage());
            }
        }
        return new ArrayList<>(records);
    }

    private static List<Path> transactionFilesNewestFirst() {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(transactionDirectory,
                FILE_PREFIX + "*" + FILE_SUFFIX)) {
            for (Path path : stream) {
                files.add(path);
            }
        } catch (IOException exception) {
            JavaPlugin currentPlugin = plugin;
            if (currentPlugin != null) {
                currentPlugin.getLogger().warning("Could not list transaction log files: " + exception.getMessage());
            }
        }
        files.sort(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed());
        return files;
    }

    private static void cleanupExpiredFiles() {
        if (transactionDirectory == null || !Files.isDirectory(transactionDirectory)) {
            return;
        }
        LocalDate cutoff = LocalDate.now().minusDays(Math.max(1, retentionDays));
        for (Path path : transactionFilesNewestFirst()) {
            LocalDate date = dateFromFile(path);
            if (date == null || !date.isBefore(cutoff)) {
                continue;
            }
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                JavaPlugin currentPlugin = plugin;
                if (currentPlugin != null) {
                    currentPlugin.getLogger().warning("Could not delete expired transaction log "
                            + path.getFileName() + ": " + exception.getMessage());
                }
            }
        }
    }

    private static LocalDate dateFromFile(Path path) {
        String name = path.getFileName().toString();
        if (!name.startsWith(FILE_PREFIX) || !name.endsWith(FILE_SUFFIX)) {
            return null;
        }
        String raw = name.substring(FILE_PREFIX.length(), name.length() - FILE_SUFFIX.length());
        try {
            return LocalDate.parse(raw, FILE_DATE);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String serialize(TransactionRecord record) {
        return String.join("\t",
                FORMAT_VERSION,
                record.id().toString(),
                Long.toString(record.timestamp()),
                record.reason().name(),
                Integer.toString(record.amount()),
                record.from().type().name(),
                encode(record.from().id()),
                encode(record.from().name()),
                integer(record.from().balanceBefore()),
                integer(record.from().balanceAfter()),
                record.to().type().name(),
                encode(record.to().id()),
                encode(record.to().name()),
                integer(record.to().balanceBefore()),
                integer(record.to().balanceAfter()),
                encode(record.actorType()),
                encode(record.actorId()),
                encode(record.actorName()),
                encode(record.context()));
    }

    private static TransactionRecord deserialize(String line) {
        if (line == null || line.isBlank() || line.startsWith("#")) {
            return null;
        }
        String[] fields = line.split("\t", -1);
        if (fields.length != 19 || !FORMAT_VERSION.equals(fields[0])) {
            return null;
        }
        try {
            return new TransactionRecord(
                    UUID.fromString(fields[1]),
                    Long.parseLong(fields[2]),
                    TransactionReason.valueOf(fields[3]),
                    Integer.parseInt(fields[4]),
                    new TransactionParty(
                            TransactionAccountType.valueOf(fields[5]),
                            decode(fields[6]),
                            decode(fields[7]),
                            nullableInt(fields[8]),
                            nullableInt(fields[9])),
                    new TransactionParty(
                            TransactionAccountType.valueOf(fields[10]),
                            decode(fields[11]),
                            decode(fields[12]),
                            nullableInt(fields[13]),
                            nullableInt(fields[14])),
                    decode(fields[15]),
                    decode(fields[16]),
                    decode(fields[17]),
                    decode(fields[18]));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String encode(String value) {
        return ENCODER.encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return new String(DECODER.decode(value), StandardCharsets.UTF_8);
    }

    private static String integer(Integer value) {
        return value == null ? "" : Integer.toString(value);
    }

    private static Integer nullableInt(String value) {
        return value == null || value.isEmpty() ? null : Integer.valueOf(value);
    }

    private static int bounded(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
