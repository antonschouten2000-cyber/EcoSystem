package me.antonschouten.economy.Data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.logging.Level;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public abstract class AbstractYamlData {

	private static final String DATA_DIRECTORY = "Data";

	private final String fileName;

	private Plugin plugin;
	private Path filePath;
	private volatile YamlConfiguration data;

	protected AbstractYamlData(String fileName) {
		this.fileName = Objects.requireNonNull(fileName, "fileName");
		if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
			throw new IllegalArgumentException("Invalid data file name: " + fileName);
		}
	}

	public final synchronized void setup(Plugin plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");

		try {
			Path dataDirectory = plugin.getDataFolder().toPath().resolve(DATA_DIRECTORY).normalize();
			Files.createDirectories(dataDirectory);

			filePath = dataDirectory.resolve(fileName).normalize();
			if (!filePath.getParent().equals(dataDirectory)) {
				throw new IllegalStateException("Resolved data file escaped the Data directory: " + fileName);
			}

			boolean created = Files.notExists(filePath);
			if (created) {
				Files.createFile(filePath);
				plugin.getLogger().info("Created Data/" + fileName);
			}

			PreparedData prepared = prepareFromDisk();
			data = prepared.configuration();

			if (created || prepared.changed()) {
				if (!save()) {
					throw new IllegalStateException("Could not persist initial data file " + fileName);
				}
			}
		} catch (IOException | InvalidConfigurationException exception) {
			throw new IllegalStateException("Could not initialize Data/" + fileName, exception);
		}
	}

	public final FileConfiguration getData() {
		YamlConfiguration current = data;
		if (current == null) {
			throw new IllegalStateException(fileName + " has not been initialized yet");
		}
		return current;
	}

	public final void saveData() {
		save();
	}

	public final synchronized boolean save() {
		if (data == null || filePath == null) {
			logSevere("Tried to save " + fileName + " before setup completed.", null);
			return false;
		}

		String serialized;
		try {
			serialized = data.saveToString();
		} catch (RuntimeException exception) {
			logSevere("Could not serialize Data/" + fileName + ".", exception);
			return false;
		}

		Path tempPath = filePath.resolveSibling(fileName + ".tmp");
		try {
			Files.writeString(tempPath, serialized, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

			replaceAtomically(tempPath, filePath);
			return true;
		} catch (IOException exception) {
			logSevere("Could not save Data/" + fileName + ".", exception);
			tryDeleteTempFile(tempPath);
			return false;
		}
	}

	/** Compatibility method retained for existing callers. */
	public final void reloadData() {
		reload();
	}

	public final synchronized boolean reload() {
		if (filePath == null) {
			logSevere("Tried to reload " + fileName + " before setup completed.", null);
			return false;
		}

		try {
			PreparedData prepared = prepareFromDisk();
			commit(prepared);
			return true;
		} catch (IOException | InvalidConfigurationException exception) {
			logSevere("Could not reload Data/" + fileName + ". The current in-memory data was kept unchanged.",
					exception);
			return false;
		}
	}

	public final String getFileName() {
		return fileName;
	}

	public final Path getFilePath() {
		return filePath;
	}

	protected boolean applyDefaults(YamlConfiguration configuration) {
		return false;
	}

	protected final boolean ensureBoolean(YamlConfiguration configuration, String path, boolean defaultValue) {
		if (configuration.isBoolean(path)) {
			return false;
		}
		warnInvalidValue(configuration, path, defaultValue);
		configuration.set(path, defaultValue);
		return true;
	}

	protected final boolean ensureInt(YamlConfiguration configuration, String path, int defaultValue, int minimum,
			int maximum) {
		if (configuration.isInt(path)) {
			int value = configuration.getInt(path);
			if (value >= minimum && value <= maximum) {
				return false;
			}
		}
		warnInvalidValue(configuration, path, defaultValue);
		configuration.set(path, defaultValue);
		return true;
	}

	protected final boolean ensureString(YamlConfiguration configuration, String path, String defaultValue,
			boolean allowBlank) {
		if (configuration.isString(path)) {
			String value = configuration.getString(path);
			if (value != null && (allowBlank || !value.isBlank())) {
				return false;
			}
		}
		warnInvalidValue(configuration, path, defaultValue);
		configuration.set(path, defaultValue);
		return true;
	}

	protected final boolean ensureList(YamlConfiguration configuration, String path, java.util.List<?> defaultValue) {
		if (configuration.isList(path)) {
			return false;
		}
		warnInvalidValue(configuration, path, defaultValue);
		configuration.set(path, defaultValue);
		return true;
	}

	final synchronized PreparedData prepareForRegistryReload() throws IOException, InvalidConfigurationException {
		if (filePath == null) {
			throw new IllegalStateException(fileName + " has not been initialized yet");
		}
		return prepareFromDisk();
	}

	final synchronized boolean commitRegistryReload(PreparedData prepared) {
		Objects.requireNonNull(prepared, "prepared");
		data = prepared.configuration();
		return !prepared.changed() || save();
	}

	private PreparedData prepareFromDisk() throws IOException, InvalidConfigurationException {
		YamlConfiguration loaded = new YamlConfiguration();
		loaded.load(filePath.toFile());
		boolean changed = applyDefaults(loaded);
		return new PreparedData(loaded, changed);
	}

	private void commit(PreparedData prepared) {
		data = prepared.configuration();
		if (prepared.changed() && !save()) {
			logSevere("Reloaded " + fileName + " but could not persist normalized defaults.", null);
		}
	}

	private void replaceAtomically(Path tempPath, Path targetPath) throws IOException {
		try {
			Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException atomicFailure) {

			try {
				Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException fallbackFailure) {
				fallbackFailure.addSuppressed(atomicFailure);
				throw fallbackFailure;
			}
		}
	}

	private void tryDeleteTempFile(Path tempPath) {
		try {
			Files.deleteIfExists(tempPath);
		} catch (IOException ignored) {

		}
	}

	private void warnInvalidValue(YamlConfiguration configuration, String path, Object fallback) {
		if (plugin == null) {
			return;
		}
		if (configuration.isSet(path)) {
			plugin.getLogger().warning("Invalid value in Data/" + fileName + " at '" + path + "'. Resetting it to: "
					+ String.valueOf(fallback));
		}
	}

	private void logSevere(String message, Throwable throwable) {
		if (plugin == null) {
			return;
		}
		if (throwable == null) {
			plugin.getLogger().severe(message);
		} else {
			plugin.getLogger().log(Level.SEVERE, message, throwable);
		}
	}

	static record PreparedData(YamlConfiguration configuration, boolean changed) {
	}
}
