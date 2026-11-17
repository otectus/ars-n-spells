package com.otectus.arsnspells.gametest;

import io.redspace.ironsspellbooks.api.events.ModifySpellLevelEvent;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainerMutable;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Iron's-typed helpers for the 3.4.1 GameTests. Kept out of every {@code @GameTestHolder} class:
 * Forge resolves a holder's synthetic lambda signatures reflectively, and an Iron's type there
 * breaks the whole holder in the Iron's-absent profile.
 */
final class Glyph341IronsSupport {
    private Glyph341IronsSupport() {}

    /** Counts (and optionally changes) Iron's level and cast events for one player. */
    static final class EventProbe implements AutoCloseable {
        final ServerPlayer player;
        final int levelBonus;
        final double costFactor;
        int levelEvents;
        int castEvents;
        int lastCastLevel = -1;
        int lastCastCost = -1;
        private final Consumer<ModifySpellLevelEvent> levels;
        private final Consumer<SpellOnCastEvent> casts;

        EventProbe(ServerPlayer player, int levelBonus, double costFactor) {
            this.player = player;
            this.levelBonus = levelBonus;
            this.costFactor = costFactor;
            this.levels = event -> {
                if (event.getEntity() != player) return;
                levelEvents++;
                if (levelBonus != 0) event.addLevels(levelBonus);
            };
            this.casts = event -> {
                if (event.getEntity() != player) return;
                castEvents++;
                if (costFactor != 1.0) event.setManaCost((int) Math.round(event.getManaCost() * costFactor));
                lastCastLevel = event.getSpellLevel();
                lastCastCost = event.getManaCost();
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ModifySpellLevelEvent.class, levels);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, SpellOnCastEvent.class, casts);
        }

        @Override
        public void close() {
            MinecraftForge.EVENT_BUS.unregister(levels);
            MinecraftForge.EVENT_BUS.unregister(casts);
        }
    }

    /** A probe as a plain handle, so GameTest holders never carry its type in a lambda signature. */
    static AutoCloseable probe(ServerPlayer player, int levelBonus, double costFactor) {
        return new EventProbe(player, levelBonus, costFactor);
    }

    static int levelEvents(AutoCloseable probe) { return ((EventProbe) probe).levelEvents; }
    static int castEvents(AutoCloseable probe) { return ((EventProbe) probe).castEvents; }
    static int lastCastLevel(AutoCloseable probe) { return ((EventProbe) probe).lastCastLevel; }
    static int lastCastCost(AutoCloseable probe) { return ((EventProbe) probe).lastCastCost; }

    static void close(AutoCloseable probe) {
        ((EventProbe) probe).close();
    }

    static int manaCost(String spell, int level) {
        return SpellRegistry.getSpell(new ResourceLocation(spell)).getManaCost(level);
    }

    /**
     * Change Iron's effective maximum level of {@code spell} the way a configuration-event listener
     * does (through Iron's own {@code SpellConfigHolder}), returning the previous value. Iron's
     * getters read it at once; the caller rebuilds ANS's live layer.
     */
    static int setMaxLevel(String spell, int max) {
        AbstractSpell native_ = SpellRegistry.getSpell(new ResourceLocation(spell));
        try {
            Class<?> manager = Class.forName("io.redspace.ironsspellbooks.api.config.SpellConfigManager");
            Field instance = manager.getField("INSTANCE");
            Field config = manager.getDeclaredField("config");
            config.setAccessible(true);
            java.util.Map<?, ?> holders = (java.util.Map<?, ?>) config.get(instance.get(null));
            Object holder = holders.get(native_);
            if (holder == null) throw new IllegalStateException("Iron's has no config holder for " + spell + "; was the config built?");
            Object parameter = maxLevelParameter();
            int previous = native_.getMaxLevel();
            holder.getClass().getMethod("set", Class.forName("io.redspace.ironsspellbooks.api.config.SpellConfigParameter"), Object.class)
                .invoke(holder, parameter, max);
            native_.resetRarityWeights();
            if (native_.getMaxLevel() != max) throw new IllegalStateException("Iron's still reports " + native_.getMaxLevel() + " for " + spell);
            return previous;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("could not change Iron's maximum level of " + spell, failure);
        }
    }

    /** The parameter object Iron's getMaxLevel reads: SpellConfigParameter.MAX_LEVEL (3.16) or IronConfigParameters.MAX_LEVEL (3.15). */
    private static Object maxLevelParameter() throws ReflectiveOperationException {
        return parameter("MAX_LEVEL");
    }

    private static Object parameter(String name) throws ReflectiveOperationException {
        try {
            return Class.forName("io.redspace.ironsspellbooks.api.config.SpellConfigParameter").getField(name).get(null);
        } catch (NoSuchFieldException olderBuild) {
            return Class.forName("io.redspace.ironsspellbooks.api.config.IronConfigParameters").getField(name).get(null);
        }
    }

    /**
     * Move {@code spell} to another school the way Iron's own configuration does (its
     * {@code SpellConfigHolder}), returning the previous school id. Iron's getSchoolType reads it at
     * once; the caller rebuilds ANS's live layer.
     */
    static String setSchool(String spell, String school) {
        AbstractSpell native_ = SpellRegistry.getSpell(new ResourceLocation(spell));
        io.redspace.ironsspellbooks.api.spells.SchoolType target =
            io.redspace.ironsspellbooks.api.registry.SchoolRegistry.REGISTRY.get().getValue(new ResourceLocation(school));
        if (target == null) throw new IllegalArgumentException("no Iron's school " + school);
        try {
            Class<?> manager = Class.forName("io.redspace.ironsspellbooks.api.config.SpellConfigManager");
            Field config = manager.getDeclaredField("config");
            config.setAccessible(true);
            java.util.Map<?, ?> holders = (java.util.Map<?, ?>) config.get(manager.getField("INSTANCE").get(null));
            Object holder = holders.get(native_);
            if (holder == null) throw new IllegalStateException("Iron's has no config holder for " + spell + "; was the config built?");
            String previous = native_.getSchoolType().getId().toString();
            holder.getClass().getMethod("set", Class.forName("io.redspace.ironsspellbooks.api.config.SpellConfigParameter"), Object.class)
                .invoke(holder, parameter("SCHOOL"), target);
            if (!native_.getSchoolType().getId().toString().equals(school))
                throw new IllegalStateException("Iron's still reports " + native_.getSchoolType().getId() + " for " + spell);
            return previous;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("could not change Iron's school of " + spell, failure);
        }
    }

    /** Put native spells into the first free slots of an Iron's book (growing its capacity to fit). */
    static void addNativeSpells(ItemStack book, String[] spells, int[] levels) {
        ISpellContainer container = ISpellContainer.getOrCreate(book);
        ISpellContainerMutable mutable = container.mutableCopy();
        if (mutable.getMaxSpellCount() < spells.length + 2) mutable.setMaxSpellCount(spells.length + 2);
        for (int i = 0; i < spells.length; i++) mutable.addSpell(SpellRegistry.getSpell(new ResourceLocation(spells[i])), levels[i], false);
        ISpellContainer.set(book, mutable.toImmutable());
    }

    /** The book's native spells in slot order as "id@level", skipping ANS proxies. */
    static List<String> nativeSpells(ItemStack book) {
        List<String> out = new ArrayList<>();
        if (!ISpellContainer.isSpellContainer(book)) return out;
        for (SpellSlot slot : ISpellContainer.get(book).getActiveSpells()) {
            String id = slot.getSpell().getSpellId();
            if (!com.otectus.arsnspells.spell.CrossCastNbt.isArsCrossProxyId(id)) out.add(id + "@" + slot.getLevel());
        }
        return out;
    }

    /** How many ANS proxy slots the book's native container holds. */
    static int proxySlots(ItemStack book) {
        if (!ISpellContainer.isSpellContainer(book)) return 0;
        int count = 0;
        for (SpellSlot slot : ISpellContainer.get(book).getActiveSpells())
            if (com.otectus.arsnspells.spell.CrossCastNbt.isArsCrossProxyId(slot.getSpell().getSpellId())) count++;
        return count;
    }

    static int maxSpellCount(ItemStack book) {
        return ISpellContainer.isSpellContainer(book) ? ISpellContainer.get(book).getMaxSpellCount() : 0;
    }

    static void clearCooldowns(ServerPlayer player) {
        IronsGlyphTestSupport.clearCooldowns(player);
    }
}
