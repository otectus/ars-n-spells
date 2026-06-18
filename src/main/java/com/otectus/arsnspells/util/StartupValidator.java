package com.otectus.arsnspells.util;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Validates the environment before mod initialization. The Java-version and
 * required-mods checks always run; the file-I/O checks (config writable,
 * file lock probe) only run in DEBUG_MODE since they're invasive and rarely
 * useful in production.
 */
public class StartupValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(StartupValidator.class);

    public static boolean validate() {
        LOGGER.info("========================================");
        LOGGER.info("Ars 'n' Spells - Startup Validation");
        LOGGER.info("========================================");

        boolean allChecks = true;

        allChecks &= checkRequiredMods();
        allChecks &= checkJavaVersion();

        boolean debugMode = false;
        try {
            debugMode = com.otectus.arsnspells.config.AnsConfig.DEBUG_MODE.get();
        } catch (Exception ignored) {}
        if (debugMode) {
            allChecks &= checkConfigWritable();
            allChecks &= checkFileLocks();
        }

        LOGGER.info("========================================");
        if (allChecks) {
            LOGGER.info("OK Startup Validation: PASSED");
        } else {
            LOGGER.warn("FAILED Startup Validation: FAILED (some checks failed)");
            LOGGER.warn("Mod may not function correctly");
        }
        LOGGER.info("========================================");

        return allChecks;
    }

    private static boolean checkConfigWritable() {
        try {
            Path configDir = FMLPaths.CONFIGDIR.get();
            Path testFile = configDir.resolve(".arsnspells_write_test");
            Files.write(testFile, "test".getBytes());
            Files.delete(testFile);
            LOGGER.info("OK Config directory writable");
            return true;
        } catch (IOException e) {
            LOGGER.error("FAILED Config directory not writable: {}", e.getMessage());
            return false;
        }
    }

    private static boolean checkFileLocks() {
        try {
            Path configPath = FMLPaths.CONFIGDIR.get().resolve("ars_n_spells-server.toml");
            if (!Files.exists(configPath)) {
                LOGGER.info("OK Config file doesn't exist yet (will be created)");
                return true;
            }
            try (FileChannel channel = FileChannel.open(configPath, StandardOpenOption.WRITE)) {
                FileLock lock = channel.tryLock();
                if (lock != null) {
                    lock.release();
                    LOGGER.info("OK Config file not locked");
                    return true;
                } else {
                    LOGGER.warn("FAILED Config file is locked by another process");
                    return false;
                }
            }
        } catch (IOException e) {
            LOGGER.warn("WARN Could not check file locks: {}", e.getMessage());
            return true;
        }
    }

    private static boolean checkRequiredMods() {
        boolean arsNouveau = ModList.get().isLoaded("ars_nouveau");
        boolean ironsSpellbooks = ModList.get().isLoaded("irons_spellbooks");

        if (arsNouveau) {
            LOGGER.info("OK Ars Nouveau detected");
        } else {
            LOGGER.error("FAILED Ars Nouveau not found (REQUIRED)");
            return false;
        }

        if (ironsSpellbooks) {
            LOGGER.info("OK Iron's Spellbooks detected (optional)");
        } else {
            LOGGER.warn("WARN Iron's Spellbooks not found (optional but recommended)");
        }

        return arsNouveau;
    }

    private static boolean checkJavaVersion() {
        String javaVersion = System.getProperty("java.version");
        int majorVersion = getMajorJavaVersion();

        if (majorVersion >= 21) {
            LOGGER.info("OK Java version: {} (compatible)", javaVersion);
            return true;
        } else {
            LOGGER.error("FAILED Java version: {} (incompatible)", javaVersion);
            LOGGER.error("  Minecraft 1.21.1 / NeoForge requires Java 21 or higher!");
            return false;
        }
    }

    private static int getMajorJavaVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf(".");
            if (dot != -1) {
                version = version.substring(0, dot);
            }
        }
        try {
            return Integer.parseInt(version);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
