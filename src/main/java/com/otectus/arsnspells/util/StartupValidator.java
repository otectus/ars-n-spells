package com.otectus.arsnspells.util;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Validates the environment before mod initialization.
 * Checks for common issues that could cause crashes or failures.
 */
public class StartupValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(StartupValidator.class);
    
    /**
     * Run all validation checks
     * @return true if all checks pass, false if any fail
     */
    public static boolean validate() {
        LOGGER.info("========================================");
        LOGGER.info("Ars 'n' Spells - Startup Validation");
        LOGGER.info("========================================");
        
        boolean allChecks = true;
        
        // Check 1: Config directory writable
        allChecks &= checkConfigWritable();
        
        // Check 2: No file locks on our config
        allChecks &= checkFileLocks();
        
        // Check 3: Required mods present
        allChecks &= checkRequiredMods();
        
        // Check 4: Java version
        allChecks &= checkJavaVersion();
        
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
    
    /**
     * Check if config directory is writable
     */
    private static boolean checkConfigWritable() {
        try {
            Path configDir = FMLPaths.CONFIGDIR.get();
            Path testFile = configDir.resolve(".arsnspells_write_test");
            
            // Try to write a test file
            Files.write(testFile, "test".getBytes());
            Files.delete(testFile);
            
            LOGGER.info("OK Config directory writable");
            return true;
        } catch (IOException e) {
            LOGGER.error("FAILED Config directory not writable: {}", e.getMessage());
            LOGGER.error("  This will cause config save failures!");
            return false;
        }
    }
    
    /**
     * Check for file locks on our config file
     */
    private static boolean checkFileLocks() {
        try {
            Path configPath = FMLPaths.CONFIGDIR.get().resolve("ars_n_spells-common.toml");
            
            if (!Files.exists(configPath)) {
                LOGGER.info("OK Config file doesn't exist yet (will be created)");
                return true;
            }
            
            // Try to acquire a lock
            try (FileChannel channel = FileChannel.open(configPath, StandardOpenOption.WRITE)) {
                FileLock lock = channel.tryLock();
                if (lock != null) {
                    lock.release();
                    LOGGER.info("OK Config file not locked");
                    return true;
                } else {
                    LOGGER.warn("FAILED Config file is locked by another process");
                    LOGGER.warn("  This may cause save failures!");
                    return false;
                }
            }
        } catch (IOException e) {
            LOGGER.warn("WARN Could not check file locks: {}", e.getMessage());
            // Don't fail validation for this
            return true;
        }
    }
    
    /**
     * Check if required mods are present
     */
    private static boolean checkRequiredMods() {
        boolean arsNouveau = ModList.get().isLoaded("ars_nouveau");
        boolean ironsSpellbooks = ModList.get().isLoaded("irons_spellbooks");
        
        if (arsNouveau) {
            LOGGER.info("OK Ars Nouveau detected");
        } else {
            LOGGER.error("FAILED Ars Nouveau not found (REQUIRED)");
            LOGGER.error("  Ars 'n' Spells requires Ars Nouveau to function!");
            return false;
        }
        
        if (ironsSpellbooks) {
            LOGGER.info("OK Iron's Spellbooks detected (optional)");
        } else {
            LOGGER.warn("WARN Iron's Spellbooks not found (optional but recommended)");
            LOGGER.warn("  Some features will be unavailable");
        }
        
        return arsNouveau; // Only Ars Nouveau is required
    }
    
    /**
     * Check Java version
     */
    private static boolean checkJavaVersion() {
        String javaVersion = System.getProperty("java.version");
        int majorVersion = getMajorJavaVersion();
        
        if (majorVersion >= 17) {
            LOGGER.info("OK Java version: {} (compatible)", javaVersion);
            return true;
        } else {
            LOGGER.error("FAILED Java version: {} (incompatible)", javaVersion);
            LOGGER.error("  Minecraft 1.20.1 requires Java 17 or higher!");
            return false;
        }
    }
    
    /**
     * Get major Java version number
     */
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
