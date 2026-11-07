package me.antonschouten.economy.Updater;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import me.antonschouten.economy.Main;

/**
 * Checks SpigotMC for a newer Economy++ release without ever blocking the
 * Minecraft main thread.
 * <p>
 * Results are cached so player joins and manual checks do not repeatedly hit
 * the SpigotMC endpoint. Concurrent checks are coalesced into one HTTP request.
 */
public final class Updater {

    public static final int DEFAULT_RESOURCE_ID = 54958;
    public static final String RESOURCE_URL = "https://www.spigotmc.org/resources/economy.54958/";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SUCCESS_CACHE_TTL = Duration.ofMinutes(30);
    private static final Duration FAILURE_CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration RATE_LIMIT_CACHE_TTL = Duration.ofMinutes(30);

    private static final Pattern CORE_VERSION_PATTERN = Pattern.compile("^(\\d+(?:[._]\\d+)*)");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    private static volatile Updater activeInstance;

    private final JavaPlugin plugin;
    private final int resourceId;
    private final URI apiUri;
    private final HttpClient httpClient;
    private final String currentVersion;

    private final Object callbackLock = new Object();
    private final List<Consumer<UpdateResult>> pendingCallbacks = new ArrayList<>();
    private final AtomicBoolean requestInProgress = new AtomicBoolean(false);

    private volatile CachedResult cachedResult;
    private volatile boolean shutdown;

    public Updater(JavaPlugin plugin, int resourceId) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");

        if (resourceId <= 0) {
            throw new IllegalArgumentException("resourceId must be greater than 0");
        }

        this.resourceId = resourceId;
        this.currentVersion = Objects.requireNonNullElse(
                plugin.getDescription().getVersion(), "unknown").trim();
        this.apiUri = URI.create(
                "https://api.spigotmc.org/legacy/update.php?resource=" + resourceId);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        activeInstance = this;
    }

    /**
     * Starts one asynchronous update check and reports the result to the server
     * console. Safe to call during onEnable().
     */
    public void checkConsoleAsync() {
        checkAsync(false, result -> {
            switch (result.status()) {
                case UPDATE_AVAILABLE -> plugin.getLogger().info(
                        "A new Economy++ version is available: "
                                + result.latestVersion()
                                + " (installed: " + result.currentVersion() + "). "
                                + "Download: " + RESOURCE_URL);

                case UP_TO_DATE -> plugin.getLogger().info(
                        "Economy++ is up to date (" + result.currentVersion() + ").");

                case DEVELOPMENT_BUILD -> plugin.getLogger().info(
                        "Economy++ is running a version newer than the public Spigot release "
                                + "(installed: " + result.currentVersion()
                                + ", public: " + result.latestVersion() + ").");

                case RATE_LIMITED -> plugin.getLogger().fine(
                        "SpigotMC temporarily rate-limited the Economy++ update check.");

                case FAILED -> plugin.getLogger().warning(
                        "Could not check for Economy++ updates: " + result.message());
            }
        });
    }

    /**
     * Backwards-compatible entry point used by existing commands/listeners.
     * The actual HTTP request runs asynchronously and player messages are sent
     * back on the Bukkit main thread.
     */
    public static void updateCheckerPlayer(Player player) {
        Objects.requireNonNull(player, "player");

        Updater updater = activeInstance;
        if (updater == null || updater.shutdown) {
            player.sendMessage(Main.prefix + "§cThe update checker is not available right now.");
            return;
        }

        updater.checkPlayerAsync(player);
    }

    public void checkPlayerAsync(Player player) {
        Objects.requireNonNull(player, "player");

        checkAsync(false, result -> {
            if (!player.isOnline()) {
                return;
            }

            switch (result.status()) {
                case UPDATE_AVAILABLE -> {
                    player.sendMessage(Main.prefix + "§eA new Economy++ version is available: §a"
                            + result.latestVersion());
                    player.sendMessage(Main.prefix + "§fInstalled version: §7" + result.currentVersion());
                    player.sendMessage(Main.prefix + "§fDownload: §a" + RESOURCE_URL);
                }

                case UP_TO_DATE -> player.sendMessage(
                        Main.prefix + "§aEconomy++ is up to date. §7(" + result.currentVersion() + ")");

                case DEVELOPMENT_BUILD -> player.sendMessage(
                        Main.prefix + "§eYou are running a newer/development build. §7Installed: "
                                + result.currentVersion() + ", public: " + result.latestVersion());

                case RATE_LIMITED -> player.sendMessage(
                        Main.prefix + "§eThe update service is temporarily rate-limited. Try again later.");

                case FAILED -> player.sendMessage(
                        Main.prefix + "§cFailed to check for updates. Please try again later.");
            }
        });
    }

    /**
     * Asynchronously checks for an update. Recent results are cached for 30
     * minutes; failures are cached briefly to avoid hammering the endpoint.
     */
    public void checkAsync(boolean forceRefresh, Consumer<UpdateResult> callback) {
        Objects.requireNonNull(callback, "callback");

        if (shutdown || !plugin.isEnabled()) {
            dispatch(callback, UpdateResult.failed(currentVersion, "Updater is shut down."));
            return;
        }

        CachedResult cache = cachedResult;
        if (!forceRefresh && cache != null && !cache.isExpired()) {
            dispatch(callback, cache.result());
            return;
        }

        synchronized (callbackLock) {
            cache = cachedResult;
            if (!forceRefresh && cache != null && !cache.isExpired()) {
                dispatch(callback, cache.result());
                return;
            }

            pendingCallbacks.add(callback);
            if (!requestInProgress.compareAndSet(false, true)) {
                return;
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::performRequest);
    }

    public void shutdown() {
        shutdown = true;
        cachedResult = null;

        synchronized (callbackLock) {
            pendingCallbacks.clear();
        }

        if (activeInstance == this) {
            activeInstance = null;
        }
    }

    public int getResourceId() {
        return resourceId;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    private void performRequest() {
        UpdateResult result;

        try {
            HttpRequest request = HttpRequest.newBuilder(apiUri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "text/plain")
                    .header("User-Agent", "Economy++/" + currentVersion + " (Spigot resource " + resourceId + ")")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            result = handleResponse(response);
        } catch (HttpTimeoutException exception) {
            result = UpdateResult.failed(currentVersion, "Update request timed out.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            result = UpdateResult.failed(currentVersion, "Update request was interrupted.");
        } catch (IOException | RuntimeException exception) {
            String message = exception.getMessage();
            result = UpdateResult.failed(
                    currentVersion,
                    message == null || message.isBlank()
                            ? exception.getClass().getSimpleName()
                            : message);
        }

        cacheResult(result);
        completeCallbacks(result);
    }

    private UpdateResult handleResponse(HttpResponse<String> response) {
        int statusCode = response.statusCode();

        if (statusCode == 429) {
            return UpdateResult.rateLimited(currentVersion);
        }

        if (statusCode < 200 || statusCode >= 300) {
            return UpdateResult.failed(
                    currentVersion,
                    "SpigotMC returned HTTP " + statusCode + '.');
        }

        String latestVersion = firstLine(response.body());
        if (latestVersion.isBlank()) {
            return UpdateResult.failed(currentVersion, "SpigotMC returned an empty version response.");
        }

        int comparison = compareVersions(currentVersion, latestVersion);

        if (comparison < 0) {
            return new UpdateResult(
                    Status.UPDATE_AVAILABLE,
                    currentVersion,
                    latestVersion,
                    "A newer version is available.");
        }

        if (comparison > 0) {
            return new UpdateResult(
                    Status.DEVELOPMENT_BUILD,
                    currentVersion,
                    latestVersion,
                    "Installed version is newer than the public version.");
        }

        return new UpdateResult(
                Status.UP_TO_DATE,
                currentVersion,
                latestVersion,
                "Plugin is up to date.");
    }

    private void cacheResult(UpdateResult result) {
        Duration ttl = switch (result.status()) {
            case UPDATE_AVAILABLE, UP_TO_DATE, DEVELOPMENT_BUILD -> SUCCESS_CACHE_TTL;
            case RATE_LIMITED -> RATE_LIMIT_CACHE_TTL;
            case FAILED -> FAILURE_CACHE_TTL;
        };

        cachedResult = new CachedResult(result, Instant.now().plus(ttl));
    }

    private void completeCallbacks(UpdateResult result) {
        List<Consumer<UpdateResult>> callbacks;

        synchronized (callbackLock) {
            callbacks = List.copyOf(pendingCallbacks);
            pendingCallbacks.clear();
            requestInProgress.set(false);
        }

        for (Consumer<UpdateResult> callback : callbacks) {
            dispatch(callback, result);
        }
    }

    private void dispatch(Consumer<UpdateResult> callback, UpdateResult result) {
        if (shutdown || !plugin.isEnabled()) {
            return;
        }

        if (Bukkit.isPrimaryThread()) {
            safelyAccept(callback, result);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> safelyAccept(callback, result));
        }
    }

    private void safelyAccept(Consumer<UpdateResult> callback, UpdateResult result) {
        try {
            callback.accept(result);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning(
                    "Update-check callback failed: " + exception.getMessage());
        }
    }

    private static String firstLine(String body) {
        if (body == null) {
            return "";
        }

        int lineBreak = body.indexOf('\n');
        String value = lineBreak >= 0 ? body.substring(0, lineBreak) : body;
        return value.replace("\r", "").trim();
    }

    /**
     * Compares common plugin-version formats without an external dependency.
     * Numeric components are compared first. For equal numeric components,
     * stable releases sort after common prerelease labels such as SNAPSHOT,
     * alpha, beta, rc, pre and dev.
     */
    static int compareVersions(String installedVersion, String remoteVersion) {
        String installed = normalizeVersion(installedVersion);
        String remote = normalizeVersion(remoteVersion);

        if (installed.equalsIgnoreCase(remote)) {
            return 0;
        }

        List<Integer> installedNumbers = numericParts(installed);
        List<Integer> remoteNumbers = numericParts(remote);
        int maxSize = Math.max(installedNumbers.size(), remoteNumbers.size());

        for (int i = 0; i < maxSize; i++) {
            int installedPart = i < installedNumbers.size() ? installedNumbers.get(i) : 0;
            int remotePart = i < remoteNumbers.size() ? remoteNumbers.get(i) : 0;

            int numericComparison = Integer.compare(installedPart, remotePart);
            if (numericComparison != 0) {
                return numericComparison;
            }
        }

        boolean installedPrerelease = isPrerelease(installed);
        boolean remotePrerelease = isPrerelease(remote);

        if (installedPrerelease != remotePrerelease) {
            return installedPrerelease ? -1 : 1;
        }

        // If both versions have the same numeric core and neither side has a
        // meaningful ordering advantage, treat them as equivalent. This avoids
        // false update notices for harmless formatting differences like 2.5 and
        // 2.5.0.
        return 0;
    }

    private static String normalizeVersion(String version) {
        if (version == null) {
            return "";
        }

        String normalized = version.trim();
        if (normalized.length() > 1
                && (normalized.charAt(0) == 'v' || normalized.charAt(0) == 'V')
                && Character.isDigit(normalized.charAt(1))) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static List<Integer> numericParts(String version) {
        List<Integer> parts = new ArrayList<>();

        Matcher coreMatcher = CORE_VERSION_PATTERN.matcher(version);
        String numericSource = coreMatcher.find() ? coreMatcher.group(1) : version;
        Matcher matcher = NUMBER_PATTERN.matcher(numericSource);

        while (matcher.find()) {
            try {
                parts.add(Integer.parseInt(matcher.group()));
            } catch (NumberFormatException ignored) {
                // Extremely large numeric version components are clamped so a
                // malformed remote version can never break the update checker.
                parts.add(Integer.MAX_VALUE);
            }
        }

        return parts;
    }

    private static boolean isPrerelease(String version) {
        String lower = version.toLowerCase(Locale.ROOT);
        return lower.contains("snapshot")
                || lower.contains("alpha")
                || lower.contains("beta")
                || lower.contains("-rc")
                || lower.contains(".rc")
                || lower.contains("-pre")
                || lower.contains(".pre")
                || lower.contains("-dev")
                || lower.contains(".dev");
    }

    public enum Status {
        UPDATE_AVAILABLE,
        UP_TO_DATE,
        DEVELOPMENT_BUILD,
        RATE_LIMITED,
        FAILED
    }

    public record UpdateResult(
            Status status,
            String currentVersion,
            String latestVersion,
            String message) {

        public UpdateResult {
            Objects.requireNonNull(status, "status");
            currentVersion = Objects.requireNonNullElse(currentVersion, "unknown");
            latestVersion = Objects.requireNonNullElse(latestVersion, "unknown");
            message = Objects.requireNonNullElse(message, "");
        }

        public boolean isUpdateAvailable() {
            return status == Status.UPDATE_AVAILABLE;
        }

        private static UpdateResult failed(String currentVersion, String message) {
            return new UpdateResult(Status.FAILED, currentVersion, "unknown", message);
        }

        private static UpdateResult rateLimited(String currentVersion) {
            return new UpdateResult(
                    Status.RATE_LIMITED,
                    currentVersion,
                    "unknown",
                    "SpigotMC temporarily rate-limited the update request.");
        }
    }

    private record CachedResult(UpdateResult result, Instant expiresAt) {
        private boolean isExpired() {
            return !Instant.now().isBefore(expiresAt);
        }
    }
}
