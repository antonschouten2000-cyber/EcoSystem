package me.antonschouten.economy.Metrics;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;

import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

/**
 * Lightweight bStats client for Economy++.
 *
 * <p>This implementation intentionally keeps the public API small and clean while
 * remaining compatible with the way Economy++ currently initializes bStats:</p>
 *
 * <pre>
 * new Metrics(this, 25475);
 * </pre>
 *
 * <p>Network traffic is performed on a dedicated daemon thread. Bukkit data is
 * collected on the server thread. Metrics failures never disable or interrupt the
 * plugin.</p>
 */
public final class Metrics implements Listener {

    private static final String PLATFORM = "bukkit";
    private static final String METRICS_VERSION = "3.2.1";
    private static final String REPORT_URL = "https://bStats.org/api/v2/data/" + PLATFORM;

    private static final long SUBMIT_INTERVAL_MINUTES = 30L;
    private static final long DEFAULT_RATE_LIMIT_MINUTES = 60L;

    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 10_000;
    private static final int MAX_RESPONSE_BYTES = 16 * 1024;

    private final Plugin plugin;
    private final int serviceId;

    private final String serverUuid;
    private final boolean enabled;
    private final boolean logFailedRequests;
    private final boolean logSentData;
    private final boolean logResponseStatusText;

    private final Set<CustomChart> customCharts = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private final ScheduledExecutorService scheduler;

    private volatile long rateLimitedUntilMillis;

    /**
     * Creates and starts the metrics client.
     *
     * @param plugin    owning Bukkit plugin
     * @param serviceId bStats service/plugin id
     */
    public Metrics(Plugin plugin, int serviceId) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");

        if (serviceId <= 0) {
            throw new IllegalArgumentException("serviceId must be greater than 0");
        }

        this.serviceId = serviceId;

        MetricsConfiguration configuration = loadConfiguration();
        this.serverUuid = configuration.serverUuid();
        this.enabled = configuration.enabled();
        this.logFailedRequests = configuration.logFailedRequests();
        this.logSentData = configuration.logSentData();
        this.logResponseStatusText = configuration.logResponseStatusText();

        Bukkit.getPluginManager().registerEvents(this, plugin);

        if (enabled) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(new MetricsThreadFactory());
            startSubmitting();
        } else {
            this.scheduler = null;
        }
    }

    /**
     * Adds an optional custom bStats chart.
     *
     * @param chart chart to add
     */
    public void addCustomChart(CustomChart chart) {
        customCharts.add(Objects.requireNonNull(chart, "chart"));
    }

    /**
     * Stops all metrics work. Safe to call more than once.
     */
    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }

        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) {
            shutdown();
        }
    }

    private void startSubmitting() {
        long initialDelaySeconds = ThreadLocalRandom.current().nextLong(180L, 361L);

        scheduler.scheduleWithFixedDelay(
                this::requestSubmission,
                initialDelaySeconds,
                TimeUnit.MINUTES.toSeconds(SUBMIT_INTERVAL_MINUTES),
                TimeUnit.SECONDS
        );
    }

    /**
     * Requests a snapshot on the Bukkit thread and sends it asynchronously.
     */
    private void requestSubmission() {
        if (shutdown.get()) {
            return;
        }

        if (!plugin.isEnabled()) {
            shutdown();
            return;
        }

        if (System.currentTimeMillis() < rateLimitedUntilMillis) {
            return;
        }

        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (shutdown.get() || !plugin.isEnabled()) {
                    return;
                }

                final String payload;

                try {
                    payload = buildPayload();
                } catch (RuntimeException exception) {
                    logFailure("Could not build bStats metrics payload", exception);
                    return;
                }

                try {
                    scheduler.execute(() -> sendPayload(payload));
                } catch (RuntimeException exception) {
                    if (!shutdown.get()) {
                        logFailure("Could not queue bStats metrics request", exception);
                    }
                }
            });
        } catch (RuntimeException exception) {
            if (!shutdown.get()) {
                logFailure("Could not schedule bStats metrics collection", exception);
            }
        }
    }

    private String buildPayload() {
        Map<String, Object> root = new LinkedHashMap<>();

        root.put("playerAmount", Bukkit.getOnlinePlayers().size());
        root.put("onlineMode", Bukkit.getOnlineMode() ? 1 : 0);
        root.put("bukkitVersion", Bukkit.getVersion());
        root.put("bukkitName", Bukkit.getName());
        root.put("javaVersion", System.getProperty("java.version", "unknown"));
        root.put("osName", System.getProperty("os.name", "unknown"));
        root.put("osArch", System.getProperty("os.arch", "unknown"));
        root.put("osVersion", System.getProperty("os.version", "unknown"));
        root.put("coreCount", Runtime.getRuntime().availableProcessors());

        Map<String, Object> service = new LinkedHashMap<>();
        service.put("pluginVersion", plugin.getDescription().getVersion());
        service.put("id", serviceId);
        service.put("customCharts", buildCustomCharts());

        root.put("service", service);
        root.put("serverUUID", serverUuid);
        root.put("metricsVersion", METRICS_VERSION);

        return Json.write(root);
    }

    private List<Map<String, Object>> buildCustomCharts() {
        List<Map<String, Object>> charts = new ArrayList<>();

        for (CustomChart chart : customCharts) {
            try {
                Map<String, Object> request = chart.buildRequest();
                if (request != null) {
                    charts.add(request);
                }
            } catch (Exception exception) {
                logFailure("Failed to collect custom bStats chart '" + chart.chartId + "'", exception);
            }
        }

        return charts;
    }

    private void sendPayload(String payload) {
        if (shutdown.get() || System.currentTimeMillis() < rateLimitedUntilMillis) {
            return;
        }

        HttpURLConnection connection = null;

        try {
            byte[] compressedPayload = gzip(payload);

            connection = openConnection();
            configureConnection(connection, compressedPayload.length);

            try (OutputStream output = connection.getOutputStream()) {
                output.write(compressedPayload);
            }

            int responseCode = connection.getResponseCode();

            if (responseCode == 429) {
                applyRateLimit(connection);
                return;
            }

            String responseBody = readResponseBody(connection, responseCode);

            if (responseCode < 200 || responseCode >= 300) {
                if (logFailedRequests) {
                    plugin.getLogger().warning(
                            "bStats returned HTTP " + responseCode
                                    + (responseBody.isBlank() ? "" : ": " + responseBody)
                    );
                }
                return;
            }

            if (logSentData) {
                plugin.getLogger().info("Sent bStats metrics data: " + payload);
            }

            if (logResponseStatusText && !responseBody.isBlank()) {
                plugin.getLogger().info("bStats response: " + responseBody);
            }
        } catch (IOException | RuntimeException exception) {
            logFailure("Could not submit bStats metrics data", exception);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private HttpURLConnection openConnection() throws IOException {
        try {
            return (HttpURLConnection) new URI(REPORT_URL).toURL().openConnection();
        } catch (URISyntaxException exception) {
            throw new IOException("Invalid bStats report URL", exception);
        }
    }

    private static void configureConnection(HttpURLConnection connection, int contentLength)
            throws IOException {
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setUseCaches(false);

        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Connection", "close");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Content-Encoding", "gzip");
        connection.setRequestProperty("User-Agent", "Economy++ bStats/" + METRICS_VERSION);

        connection.setFixedLengthStreamingMode(contentLength);
    }

    private void applyRateLimit(HttpURLConnection connection) {
        long retryAfterSeconds = parseRetryAfterSeconds(connection.getHeaderField("Retry-After"));
        long cooldownMillis = TimeUnit.SECONDS.toMillis(retryAfterSeconds);

        rateLimitedUntilMillis = System.currentTimeMillis() + cooldownMillis;

        if (logResponseStatusText) {
            plugin.getLogger().info(
                    "bStats rate-limited metrics requests (HTTP 429). Retrying after approximately "
                            + Math.max(1L, TimeUnit.SECONDS.toMinutes(retryAfterSeconds))
                            + " minute(s)."
            );
        }
    }

    private static long parseRetryAfterSeconds(String retryAfterHeader) {
        if (retryAfterHeader != null) {
            try {
                long seconds = Long.parseLong(retryAfterHeader.trim());
                if (seconds > 0) {
                    return seconds;
                }
            } catch (NumberFormatException ignored) {
                // HTTP-date Retry-After values are intentionally handled by the safe fallback below.
            }
        }

        return TimeUnit.MINUTES.toSeconds(DEFAULT_RATE_LIMIT_MINUTES);
    }

    private static String readResponseBody(HttpURLConnection connection, int responseCode)
            throws IOException {
        InputStream stream = responseCode >= 400
                ? connection.getErrorStream()
                : connection.getInputStream();

        if (stream == null) {
            return "";
        }

        try (InputStream input = stream;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1_024];
            int remaining = MAX_RESPONSE_BYTES;

            while (remaining > 0) {
                int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read < 0) {
                    break;
                }

                output.write(buffer, 0, read);
                remaining -= read;
            }

            return output.toString(StandardCharsets.UTF_8).trim();
        }
    }

    private static byte[] gzip(String value) throws IOException {
        byte[] input = value.getBytes(StandardCharsets.UTF_8);

        try (ByteArrayOutputStream output = new ByteArrayOutputStream(input.length);
             GZIPOutputStream gzip = new GZIPOutputStream(output)) {

            gzip.write(input);
            gzip.finish();
            return output.toByteArray();
        }
    }

    private MetricsConfiguration loadConfiguration() {
        File pluginsDirectory = plugin.getDataFolder().getParentFile();
        if (pluginsDirectory == null) {
            plugin.getLogger().warning("Could not locate the plugins directory. bStats is disabled.");
            return MetricsConfiguration.disabled();
        }

        File bStatsDirectory = new File(pluginsDirectory, "bStats");
        File configFile = new File(bStatsDirectory, "config.yml");

        if (!bStatsDirectory.exists() && !bStatsDirectory.mkdirs()) {
            plugin.getLogger().warning("Could not create plugins/bStats. bStats is disabled.");
            return MetricsConfiguration.disabled();
        }

        YamlConfiguration config = new YamlConfiguration();

        if (configFile.exists()) {
            try {
                config.load(configFile);
            } catch (IOException | InvalidConfigurationException exception) {
                plugin.getLogger().log(
                        Level.WARNING,
                        "Could not read plugins/bStats/config.yml safely. bStats is disabled for this session.",
                        exception
                );
                return MetricsConfiguration.disabled();
            }
        }

        boolean changed = false;

        if (!config.isSet("enabled")) {
            config.set("enabled", true);
            changed = true;
        }

        String uuid = config.getString("serverUuid");
        if (!isValidUuid(uuid)) {
            uuid = UUID.randomUUID().toString();
            config.set("serverUuid", uuid);
            changed = true;
        }

        if (!config.isSet("logFailedRequests")) {
            config.set("logFailedRequests", false);
            changed = true;
        }

        if (!config.isSet("logSentData")) {
            config.set("logSentData", false);
            changed = true;
        }

        if (!config.isSet("logResponseStatusText")) {
            config.set("logResponseStatusText", false);
            changed = true;
        }

        if (changed || !configFile.exists()) {
            try {
                config.save(configFile);
            } catch (IOException exception) {
                plugin.getLogger().log(
                        Level.WARNING,
                        "Could not save plugins/bStats/config.yml. bStats is disabled for this session.",
                        exception
                );
                return MetricsConfiguration.disabled();
            }
        }

        return new MetricsConfiguration(
                config.getBoolean("enabled", true),
                uuid,
                config.getBoolean("logFailedRequests", false),
                config.getBoolean("logSentData", false),
                config.getBoolean("logResponseStatusText", false)
        );
    }

    private static boolean isValidUuid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void logFailure(String message, Throwable throwable) {
        if (logFailedRequests && !shutdown.get()) {
            plugin.getLogger().log(Level.WARNING, message, throwable);
        }
    }

    private record MetricsConfiguration(
            boolean enabled,
            String serverUuid,
            boolean logFailedRequests,
            boolean logSentData,
            boolean logResponseStatusText
    ) {
        private static MetricsConfiguration disabled() {
            return new MetricsConfiguration(false, UUID.randomUUID().toString(), false, false, false);
        }
    }

    private static final class MetricsThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Economy++-bStats");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        }
    }

    // -------------------------------------------------------------------------
    // Custom charts
    // -------------------------------------------------------------------------

    public abstract static class CustomChart {
        private final String chartId;

        protected CustomChart(String chartId) {
            if (chartId == null || chartId.isBlank()) {
                throw new IllegalArgumentException("chartId must not be blank");
            }

            this.chartId = chartId;
        }

        private Map<String, Object> buildRequest() throws Exception {
            Map<String, Object> data = getChartData();
            if (data == null || data.isEmpty()) {
                return null;
            }

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("chartId", chartId);
            request.put("data", data);
            return request;
        }

        protected abstract Map<String, Object> getChartData() throws Exception;
    }

    public static final class SimplePie extends CustomChart {
        private final Callable<String> callable;

        public SimplePie(String chartId, Callable<String> callable) {
            super(chartId);
            this.callable = Objects.requireNonNull(callable, "callable");
        }

        @Override
        protected Map<String, Object> getChartData() throws Exception {
            String value = callable.call();
            if (value == null || value.isBlank()) {
                return null;
            }

            return Map.of("value", value);
        }
    }

    public static final class SingleLineChart extends CustomChart {
        private final Callable<Integer> callable;

        public SingleLineChart(String chartId, Callable<Integer> callable) {
            super(chartId);
            this.callable = Objects.requireNonNull(callable, "callable");
        }

        @Override
        protected Map<String, Object> getChartData() throws Exception {
            Integer value = callable.call();
            if (value == null || value == 0) {
                return null;
            }

            return Map.of("value", value);
        }
    }

    public static final class AdvancedPie extends CustomChart {
        private final Callable<Map<String, Integer>> callable;

        public AdvancedPie(String chartId, Callable<Map<String, Integer>> callable) {
            super(chartId);
            this.callable = Objects.requireNonNull(callable, "callable");
        }

        @Override
        protected Map<String, Object> getChartData() throws Exception {
            Map<String, Integer> source = callable.call();
            if (source == null || source.isEmpty()) {
                return null;
            }

            Map<String, Object> values = new LinkedHashMap<>();
            source.forEach((key, value) -> {
                if (key != null && value != null && value != 0) {
                    values.put(key, value);
                }
            });

            return values.isEmpty() ? null : Map.of("values", values);
        }
    }

    public static final class DrilldownPie extends CustomChart {
        private final Callable<Map<String, Map<String, Integer>>> callable;

        public DrilldownPie(String chartId, Callable<Map<String, Map<String, Integer>>> callable) {
            super(chartId);
            this.callable = Objects.requireNonNull(callable, "callable");
        }

        @Override
        protected Map<String, Object> getChartData() throws Exception {
            Map<String, Map<String, Integer>> source = callable.call();
            if (source == null || source.isEmpty()) {
                return null;
            }

            Map<String, Object> outerValues = new LinkedHashMap<>();

            for (Map.Entry<String, Map<String, Integer>> outer : source.entrySet()) {
                if (outer.getKey() == null || outer.getValue() == null || outer.getValue().isEmpty()) {
                    continue;
                }

                Map<String, Object> innerValues = new LinkedHashMap<>();
                outer.getValue().forEach((key, value) -> {
                    if (key != null && value != null) {
                        innerValues.put(key, value);
                    }
                });

                if (!innerValues.isEmpty()) {
                    outerValues.put(outer.getKey(), innerValues);
                }
            }

            return outerValues.isEmpty() ? null : Map.of("values", outerValues);
        }
    }

    public static final class SimpleBarChart extends CustomChart {
        private final Callable<Map<String, Integer>> callable;

        public SimpleBarChart(String chartId, Callable<Map<String, Integer>> callable) {
            super(chartId);
            this.callable = Objects.requireNonNull(callable, "callable");
        }

        @Override
        protected Map<String, Object> getChartData() throws Exception {
            Map<String, Integer> source = callable.call();
            if (source == null || source.isEmpty()) {
                return null;
            }

            Map<String, Object> values = new LinkedHashMap<>();
            source.forEach((key, value) -> {
                if (key != null && value != null) {
                    values.put(key, new int[] { value });
                }
            });

            return values.isEmpty() ? null : Map.of("values", values);
        }
    }

    public static final class AdvancedBarChart extends CustomChart {
        private final Callable<Map<String, int[]>> callable;

        public AdvancedBarChart(String chartId, Callable<Map<String, int[]>> callable) {
            super(chartId);
            this.callable = Objects.requireNonNull(callable, "callable");
        }

        @Override
        protected Map<String, Object> getChartData() throws Exception {
            Map<String, int[]> source = callable.call();
            if (source == null || source.isEmpty()) {
                return null;
            }

            Map<String, Object> values = new LinkedHashMap<>();
            source.forEach((key, value) -> {
                if (key != null && value != null && value.length > 0) {
                    values.put(key, value);
                }
            });

            return values.isEmpty() ? null : Map.of("values", values);
        }
    }

    public static final class MultiLineChart extends CustomChart {
        private final Callable<Map<String, Integer>> callable;

        public MultiLineChart(String chartId, Callable<Map<String, Integer>> callable) {
            super(chartId);
            this.callable = Objects.requireNonNull(callable, "callable");
        }

        @Override
        protected Map<String, Object> getChartData() throws Exception {
            Map<String, Integer> source = callable.call();
            if (source == null || source.isEmpty()) {
                return null;
            }

            Map<String, Object> values = new LinkedHashMap<>();
            source.forEach((key, value) -> {
                if (key != null && value != null && value != 0) {
                    values.put(key, value);
                }
            });

            return values.isEmpty() ? null : Map.of("values", values);
        }
    }

    // -------------------------------------------------------------------------
    // Minimal JSON serializer
    // -------------------------------------------------------------------------

    private static final class Json {

        private Json() {
        }

        private static String write(Object value) {
            StringBuilder builder = new StringBuilder(512);
            appendValue(builder, value);
            return builder.toString();
        }

        private static void appendValue(StringBuilder builder, Object value) {
            if (value == null) {
                builder.append("null");
                return;
            }

            if (value instanceof String string) {
                appendString(builder, string);
                return;
            }

            if (value instanceof Number || value instanceof Boolean) {
                builder.append(value);
                return;
            }

            if (value instanceof Map<?, ?> map) {
                appendMap(builder, map);
                return;
            }

            if (value instanceof Iterable<?> iterable) {
                appendIterable(builder, iterable);
                return;
            }

            if (value.getClass().isArray()) {
                appendArray(builder, value);
                return;
            }

            appendString(builder, String.valueOf(value));
        }

        private static void appendMap(StringBuilder builder, Map<?, ?> map) {
            builder.append('{');
            boolean first = true;

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }

                if (!first) {
                    builder.append(',');
                }

                appendString(builder, String.valueOf(entry.getKey()));
                builder.append(':');
                appendValue(builder, entry.getValue());
                first = false;
            }

            builder.append('}');
        }

        private static void appendIterable(StringBuilder builder, Iterable<?> iterable) {
            builder.append('[');
            boolean first = true;

            for (Object value : iterable) {
                if (!first) {
                    builder.append(',');
                }

                appendValue(builder, value);
                first = false;
            }

            builder.append(']');
        }

        private static void appendArray(StringBuilder builder, Object array) {
            builder.append('[');
            int length = Array.getLength(array);

            for (int index = 0; index < length; index++) {
                if (index > 0) {
                    builder.append(',');
                }

                appendValue(builder, Array.get(array, index));
            }

            builder.append(']');
        }

        private static void appendString(StringBuilder builder, String value) {
            builder.append('"');

            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);

                switch (character) {
                    case '"' -> builder.append("\\\"");
                    case '\\' -> builder.append("\\\\");
                    case '\b' -> builder.append("\\b");
                    case '\f' -> builder.append("\\f");
                    case '\n' -> builder.append("\\n");
                    case '\r' -> builder.append("\\r");
                    case '\t' -> builder.append("\\t");
                    default -> {
                        if (character < 0x20) {
                            builder.append(String.format("\\u%04x", (int) character));
                        } else {
                            builder.append(character);
                        }
                    }
                }
            }

            builder.append('"');
        }
    }
}
