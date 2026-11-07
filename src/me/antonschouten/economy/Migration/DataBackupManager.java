package me.antonschouten.economy.Migration;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Creates and restores immutable pre-migration backups for Economy++.
 */
public final class DataBackupManager {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private static final String BACKUP_DIRECTORY = "backups";

    private DataBackupManager() {
    }

    /**
     * Copies the complete plugin data folder (except existing backups and
     * migration staging directories) into a timestamped backup directory and
     * writes a SHA-256 manifest.
     */
    public static Path createPreMigrationBackup(
            Path sourcePluginFolder,
            Path backupOwnerFolder,
            int fromVersion,
            int toVersion) throws IOException {

        Objects.requireNonNull(sourcePluginFolder, "sourcePluginFolder");
        Objects.requireNonNull(backupOwnerFolder, "backupOwnerFolder");

        Path backupsRoot = backupOwnerFolder.resolve(BACKUP_DIRECTORY);
        Files.createDirectories(backupsRoot);

        String baseName = "pre-upgrade-schema-" + fromVersion + "-to-" + toVersion
                + "_" + LocalDateTime.now().format(TIMESTAMP);
        Path backupFolder = uniqueDirectory(backupsRoot, baseName);
        Files.createDirectories(backupFolder);

        copyPluginFolder(sourcePluginFolder, backupFolder);
        writeManifest(backupFolder);
        return backupFolder;
    }

    /**
     * Restores the Data directory from a backup created by this class.
     * Existing live Data is first moved aside so a failed restore does not
     * destroy the only remaining copy.
     */
    public static void restoreDataDirectory(Path backupFolder, Path livePluginFolder)
            throws IOException {

        Objects.requireNonNull(backupFolder, "backupFolder");
        Objects.requireNonNull(livePluginFolder, "livePluginFolder");

        Path backupData = backupFolder.resolve("Data");
        if (!Files.isDirectory(backupData)) {
            throw new IOException("Backup does not contain a Data directory: " + backupFolder);
        }

        Files.createDirectories(livePluginFolder);
        Path liveData = livePluginFolder.resolve("Data");
        Path failedData = livePluginFolder.resolve(
                ".failed-migration-data-" + LocalDateTime.now().format(TIMESTAMP));

        if (Files.exists(liveData)) {
            moveDirectory(liveData, failedData);
        }

        try {
            copyDirectory(backupData, liveData);
            deleteRecursively(failedData);
        } catch (IOException restoreFailure) {
            try {
                deleteRecursively(liveData);
                if (Files.exists(failedData)) {
                    moveDirectory(failedData, liveData);
                }
            } catch (IOException rollbackFailure) {
                restoreFailure.addSuppressed(rollbackFailure);
            }
            throw restoreFailure;
        }
    }

    static void copyDirectory(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            return;
        }

        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                Path relative = source.relativize(dir);
                Files.createDirectories(target.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                if (Files.isSymbolicLink(file)) {
                    return FileVisitResult.CONTINUE;
                }
                Path relative = source.relativize(file);
                Path destination = target.resolve(relative);
                Files.createDirectories(destination.getParent());
                Files.copy(file, destination,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void deleteRecursively(Path path) throws IOException {
        if (path == null || Files.notExists(path)) {
            return;
        }

        try (var stream = Files.walk(path)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            IOException failure = null;
            for (Path current : paths) {
                try {
                    Files.deleteIfExists(current);
                } catch (IOException exception) {
                    if (failure == null) {
                        failure = exception;
                    } else {
                        failure.addSuppressed(exception);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static void copyPluginFolder(Path sourcePluginFolder, Path backupFolder)
            throws IOException {
        if (!Files.isDirectory(sourcePluginFolder)) {
            return;
        }

        Files.walkFileTree(sourcePluginFolder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                Path relative = sourcePluginFolder.relativize(dir);
                if (!relative.toString().isEmpty()) {
                    String firstPart = relative.getName(0).toString();
                    if (firstPart.equals(BACKUP_DIRECTORY)
                            || firstPart.startsWith(".migration-staging-")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                Files.createDirectories(backupFolder.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                if (Files.isSymbolicLink(file)) {
                    return FileVisitResult.CONTINUE;
                }
                Path relative = sourcePluginFolder.relativize(file);
                Path destination = backupFolder.resolve(relative);
                Files.createDirectories(destination.getParent());
                Files.copy(file, destination,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void writeManifest(Path backupFolder) throws IOException {
        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(backupFolder)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals("MANIFEST.sha256"))
                    .sorted()
                    .forEach(files::add);
        }

        Path manifest = backupFolder.resolve("MANIFEST.sha256");
        try (BufferedWriter writer = Files.newBufferedWriter(manifest)) {
            writer.write("# Economy++ pre-migration backup manifest");
            writer.newLine();
            writer.write("# SHA-256 | bytes | relative path");
            writer.newLine();
            for (Path file : files) {
                String hash = sha256(file);
                long size = Files.size(file);
                String relative = backupFolder.relativize(file).toString().replace('\\', '/');
                writer.write(hash + " | " + size + " | " + relative);
                writer.newLine();
            }
        }
    }

    private static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }

        try (InputStream input = Files.newInputStream(file);
             DigestInputStream digestInput = new DigestInputStream(input, digest)) {
            byte[] buffer = new byte[8192];
            while (digestInput.read(buffer) != -1) {
                // DigestInputStream updates the digest while reading.
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Path uniqueDirectory(Path parent, String baseName) {
        Path candidate = parent.resolve(baseName);
        int suffix = 2;
        while (Files.exists(candidate)) {
            candidate = parent.resolve(baseName + "_" + suffix++);
        }
        return candidate;
    }

    private static void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailure) {
            try {
                Files.move(source, target);
            } catch (IOException normalFailure) {
                normalFailure.addSuppressed(atomicFailure);
                throw normalFailure;
            }
        }
    }
}
