package com.otectus.arsnspells.gametest;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Objects;

/**
 * Config writes for GameTests that skip unchanged values.
 *
 * <p>NeoForge autosaves a SERVER config on every {@code set}, non-atomically, and its file watcher
 * reloads the file on another thread. A reload that reads a half-written file "corrects" the
 * missing keys to their defaults, which can flip a value a running test depends on. Tests
 * cannot stop the watcher, so they write only what actually changes.
 */
final class TestConfig {
    private TestConfig() {}

    static <T> void set(ModConfigSpec.ConfigValue<T> value, T wanted) {
        if (!Objects.equals(value.get(), wanted)) value.set(wanted);
    }
}
