package com.otectus.arsnspells.util;

import com.otectus.arsnspells.config.AnsConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Centralized logging utility for Ars 'n' Spells.
 * Provides consistent logging format and debug mode support.
 */
public class ModLogger {
    private static final Logger LOGGER = LogManager.getLogger("Ars 'n' Spells");
    private static final String PREFIX = "[Ars 'n' Spells]";

    /**
     * Log an info message.
     */
    public static void info(String message, Object... args) {
        LOGGER.info(PREFIX + " " + message, args);
    }

    /**
     * Log a warning message.
     */
    public static void warn(String message, Object... args) {
        LOGGER.warn(PREFIX + " " + message, args);
    }

    /**
     * Log an error message.
     */
    public static void error(String message, Object... args) {
        LOGGER.error(PREFIX + " " + message, args);
    }

    /**
     * Log an error message with throwable.
     */
    public static void error(String message, Throwable throwable) {
        LOGGER.error(PREFIX + " " + message, throwable);
    }

    /**
     * Log a debug message (only if debug mode is enabled).
     */
    public static void debug(String message, Object... args) {
        if (isDebugEnabled()) {
            LOGGER.info(PREFIX + " [DEBUG] " + message, args);
        }
    }

    /**
     * Log a debug message with throwable (only if debug mode is enabled).
     */
    public static void debug(String message, Throwable throwable) {
        if (isDebugEnabled()) {
            LOGGER.info(PREFIX + " [DEBUG] " + message, throwable);
        }
    }

    /**
     * Check if debug mode is enabled.
     */
    public static boolean isDebugEnabled() {
        try {
            return AnsConfig.DEBUG_MODE != null &&
                   AnsConfig.DEBUG_MODE.get();
        } catch (Exception e) {
            // Config not loaded yet, default to false
            return false;
        }
    }

    /**
     * Log a section header.
     */
    public static void section(String title) {
        info("========================================");
        info(title);
        info("========================================");
    }

    /**
     * Log a subsection header.
     */
    public static void subsection(String title) {
        info("--- " + title + " ---");
    }

    /**
     * Log a success message.
     */
    public static void success(String message, Object... args) {
        info("OK " + message, args);
    }

    /**
     * Log a failure message.
     */
    public static void failure(String message, Object... args) {
        warn("FAILED " + message, args);
    }

    /**
     * Log initialization start.
     */
    public static void initStart(String component) {
        info("Initializing {}...", component);
    }

    /**
     * Log initialization complete.
     */
    public static void initComplete(String component) {
        success("{} initialized", component);
    }

    /**
     * Log initialization failure.
     */
    public static void initFailed(String component, Throwable throwable) {
        error("Failed to initialize {}", component);
        if (isDebugEnabled()) {
            error("Initialization error details:", throwable);
        }
    }
}
