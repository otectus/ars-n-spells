package com.otectus.arsnspells.gametest;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Objects;

/**
 * Config writes for GameTests that skip unchanged values.
 *
 * <p>Forge autosaves a SERVER config on every {@code set}, non-atomically, and its file watcher
 * reloads the file on another thread. A reload that reads a half-written file "corrects" the
 * missing keys to their defaults, which can flip a value a running test depends on. Tests
 * cannot stop the watcher, so they write only what actually changes.
 */
final class TestConfig {
    /** The value each test last asked for, re-applied if Forge's file reload reverts it. */
    private static final java.util.Map<ForgeConfigSpec.ConfigValue<?>, Object> WANTED = new java.util.concurrent.ConcurrentHashMap<>();

    private TestConfig() {}

    /**
     * Record {@code wanted} as the value a test expects, without writing it: for a test whose
     * subject (the settings service) does the write itself, so a file reload cannot revert it.
     */
    static <T> void want(ForgeConfigSpec.ConfigValue<T> value, T wanted) {
        WANTED.put(value, wanted);
    }

    static <T> void set(ForgeConfigSpec.ConfigValue<T> value, T wanted) {
        WANTED.put(value, wanted);
        // Each set writes the file; never overlap another ANS write (see AnsConfig.SAVE_LOCK).
        synchronized (com.otectus.arsnspells.config.AnsConfig.SAVE_LOCK) {
            if (!Objects.equals(value.get(), wanted)) value.set(wanted);
        }
        // What a file reload would re-derive, now and on this thread: tests never wait on Forge's watcher.
        com.otectus.arsnspells.config.AnsConfig.refreshResolvedPolicies();
    }

    /**
     * 3.4.1: synchronise with the config Forge actually applied, instead of hoping its asynchronous
     * reload does not land mid-test. When the ANS server config is reloaded from disk and a value a
     * test set has been reverted (a reload that read a half-written file "corrects" keys to their
     * defaults), the test's value is put back at once, before anything reads it. Only in GameTest runs.
     */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "ars_n_spells", bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
    static final class Reapply {
        private Reapply() {}

        /**
         * 3.4.1 review: in GameTest runs, stop Forge's file watcher on the ANS server config. Tests change
         * settings many times a second; each change writes the file non-atomically, and the watcher
         * re-reads it on another thread and rewrites a half-read file with defaults, which raced the next
         * write into a file with every table declared twice and reverted values tests had restored (the
         * SAVE-01 experiment saw both). A test's value is in force the moment it is set, so nothing needs
         * the reload; GameTest runs only.
         */
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onLoading(net.minecraftforge.fml.event.config.ModConfigEvent.Loading event) {
            if (!Boolean.getBoolean("ans.gametest.castProbe") || !com.otectus.arsnspells.config.AnsClientConfig.isServerSpec(event.getConfig())) return;
            java.nio.file.Path file = event.getConfig().getFullPath();
            com.electronwill.nightconfig.core.file.FileWatcher.defaultInstance().removeWatch(file);
            org.slf4j.LoggerFactory.getLogger(TestConfig.class).info("[GameTest] stopped Forge's file watcher on {}", file);
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onReload(net.minecraftforge.fml.event.config.ModConfigEvent.Reloading event) {
            if (!Boolean.getBoolean("ans.gametest.castProbe") || !com.otectus.arsnspells.config.AnsClientConfig.isServerSpec(event.getConfig())) return;
            WANTED.forEach(TestConfig::reapply);
        }
    }

    /**
     * Audit ANS-011 tests (and the older native-restriction tests) write Iron's and Ars's own SERVER
     * configs, whose watchers raced the same way: a reload from a half-written file reverted a value a
     * running test had just set (observed 2026-11-18: Iron's reload 4 ms before an adventure-mode
     * assertion read the default). Their configs are loaded when the server starts, so their watchers
     * are stopped then; ModConfigEvent never reaches this mod for another mod's config. GameTest runs only.
     */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "ars_n_spells")
    static final class NativeWatchers {
        private static final java.util.Set<String> MODS = java.util.Set.of("irons_spellbooks", "ars_nouveau");

        private NativeWatchers() {}

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
            if (!Boolean.getBoolean("ans.gametest.castProbe")) return;
            var configs = net.minecraftforge.fml.config.ConfigTracker.INSTANCE.configSets().get(net.minecraftforge.fml.config.ModConfig.Type.SERVER);
            if (configs == null) return;
            for (var config : configs) {
                if (!MODS.contains(config.getModId()) || config.getFullPath() == null) continue;
                com.electronwill.nightconfig.core.file.FileWatcher.defaultInstance().removeWatch(config.getFullPath());
                org.slf4j.LoggerFactory.getLogger(TestConfig.class).info("[GameTest] stopped Forge's file watcher on {}", config.getFullPath());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void reapply(ForgeConfigSpec.ConfigValue<?> value, Object wanted) {
        ForgeConfigSpec.ConfigValue<T> typed = (ForgeConfigSpec.ConfigValue<T>) value;
        synchronized (com.otectus.arsnspells.config.AnsConfig.SAVE_LOCK) {
            if (!Objects.equals(typed.get(), wanted)) typed.set((T) wanted);
        }
    }
}
