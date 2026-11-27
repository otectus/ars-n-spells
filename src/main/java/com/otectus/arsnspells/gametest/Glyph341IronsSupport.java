package com.otectus.arsnspells.gametest;

import io.redspace.ironsspellbooks.api.events.ModifySpellLevelEvent;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Iron's-typed helpers for the 3.4.1 GameTests, kept out of every {@code @GameTestHolder} class so
 * an Iron's type never appears in a holder's lambda signature (the Iron's-absent profile would
 * fail to load the holder).
 */
final class Glyph341IronsSupport {
    private Glyph341IronsSupport() {
    }

    /** Counts and optionally changes Iron's level and cast events for one player. */
    static final class EventProbe implements AutoCloseable {
        int levelEvents;
        int castEvents;
        int lastCastLevel = -1;
        private final Consumer<ModifySpellLevelEvent> levels;
        private final Consumer<SpellOnCastEvent> casts;

        EventProbe(ServerPlayer player, int levelBonus, int castLevelBonus, double costFactor) {
            levels = event -> {
                if (event.getEntity() != player) return;
                levelEvents++;
                if (levelBonus != 0) event.addLevels(levelBonus);
            };
            casts = event -> {
                if (event.getEntity() != player) return;
                castEvents++;
                if (castLevelBonus != 0) event.setSpellLevel(event.getSpellLevel() + castLevelBonus);
                if (costFactor != 1.0) event.setManaCost((int) Math.round(event.getManaCost() * costFactor));
                lastCastLevel = event.getSpellLevel();
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ModifySpellLevelEvent.class, levels);
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, SpellOnCastEvent.class, casts);
        }

        @Override
        public void close() {
            NeoForge.EVENT_BUS.unregister(levels);
            NeoForge.EVENT_BUS.unregister(casts);
        }
    }

    static AutoCloseable probe(ServerPlayer player, int levelBonus, int castLevelBonus, double costFactor) {
        return new EventProbe(player, levelBonus, castLevelBonus, costFactor);
    }

    static int levelEvents(AutoCloseable probe) { return ((EventProbe) probe).levelEvents; }
    static int castEvents(AutoCloseable probe) { return ((EventProbe) probe).castEvents; }
    static int lastCastLevel(AutoCloseable probe) { return ((EventProbe) probe).lastCastLevel; }

    static void close(AutoCloseable probe) {
        ((EventProbe) probe).close();
    }

    static int maxLevel(String spell) {
        return SpellRegistry.getSpell(ResourceLocation.parse(spell)).getMaxLevel();
    }

    /** Change Iron's effective maximum through its own config holder, as a config-event listener does; returns the previous value. */
    static int setMaxLevel(String spell, int max) {
        AbstractSpell native_ = SpellRegistry.getSpell(ResourceLocation.parse(spell));
        try {
            Class<?> manager = Class.forName("io.redspace.ironsspellbooks.api.config.SpellConfigManager");
            Field config = manager.getDeclaredField("config");
            config.setAccessible(true);
            java.util.Map<?, ?> holders = (java.util.Map<?, ?>) config.get(manager.getField("INSTANCE").get(null));
            Object holder = holders.get(native_);
            if (holder == null) throw new IllegalStateException("Iron's has no config holder for " + spell);
            Class<?> parameterType = Class.forName("io.redspace.ironsspellbooks.api.config.SpellConfigParameter");
            Object parameter = parameterType.getField("MAX_LEVEL").get(null);
            int previous = native_.getMaxLevel();
            holder.getClass().getMethod("set", parameterType, Object.class).invoke(holder, parameter, max);
            native_.resetRarityWeights();
            if (native_.getMaxLevel() != max) throw new IllegalStateException("Iron's still reports " + native_.getMaxLevel());
            return previous;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("could not change Iron's maximum level of " + spell, failure);
        }
    }

    /**
     * Move {@code spell} to another school the way Iron's own configuration does (its
     * {@code SpellConfigHolder}), returning the previous school id. Iron's getSchoolType reads it at
     * once; the caller rebuilds ANS's live layer.
     */
    static String setSchool(String spell, String school) {
        AbstractSpell native_ = SpellRegistry.getSpell(ResourceLocation.parse(spell));
        io.redspace.ironsspellbooks.api.spells.SchoolType target =
            io.redspace.ironsspellbooks.api.registry.SchoolRegistry.REGISTRY.get(ResourceLocation.parse(school));
        if (target == null) throw new IllegalArgumentException("no Iron's school " + school);
        try {
            Class<?> manager = Class.forName("io.redspace.ironsspellbooks.api.config.SpellConfigManager");
            Field config = manager.getDeclaredField("config");
            config.setAccessible(true);
            java.util.Map<?, ?> holders = (java.util.Map<?, ?>) config.get(manager.getField("INSTANCE").get(null));
            Object holder = holders.get(native_);
            if (holder == null) throw new IllegalStateException("Iron's has no config holder for " + spell);
            Class<?> parameterType = Class.forName("io.redspace.ironsspellbooks.api.config.SpellConfigParameter");
            String previous = native_.getSchoolType().getId().toString();
            holder.getClass().getMethod("set", parameterType, Object.class).invoke(holder, parameterType.getField("SCHOOL").get(null), target);
            if (!native_.getSchoolType().getId().toString().equals(school))
                throw new IllegalStateException("Iron's still reports " + native_.getSchoolType().getId());
            return previous;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("could not change Iron's school of " + spell, failure);
        }
    }

    /** The book's native spells in slot order as "id@level", skipping ANS proxies. */
    static List<String> nativeSpells(ItemStack book) {
        List<String> out = new ArrayList<>();
        if (!ISpellContainer.isSpellContainer(book)) return out;
        for (SpellSlot slot : ISpellContainer.get(book).getActiveSpells()) {
            String id = slot.getSpell().getSpellId();
            if (!com.otectus.arsnspells.spell.CrossModSpellComponents.isArsCrossProxyId(id)) out.add(id + "@" + slot.getLevel());
        }
        return out;
    }

    static int proxySlots(ItemStack book) {
        if (!ISpellContainer.isSpellContainer(book)) return 0;
        int count = 0;
        for (SpellSlot slot : ISpellContainer.get(book).getActiveSpells())
            if (com.otectus.arsnspells.spell.CrossModSpellComponents.isArsCrossProxyId(slot.getSpell().getSpellId())) count++;
        return count;
    }

    static int maxSpellCount(ItemStack book) {
        return ISpellContainer.isSpellContainer(book) ? ISpellContainer.get(book).getMaxSpellCount() : 0;
    }
}
