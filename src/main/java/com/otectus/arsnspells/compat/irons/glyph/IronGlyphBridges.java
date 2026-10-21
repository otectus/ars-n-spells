package com.otectus.arsnspells.compat.irons.glyph;

import com.otectus.arsnspells.compat.IronsCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Chooses the {@link IronGlyphBridge} once: Iron's runtime when Iron's is loaded, otherwise the absent bridge. */
public final class IronGlyphBridges {
    private static volatile IronGlyphBridge bridge;

    private IronGlyphBridges() {}

    public static IronGlyphBridge get() {
        IronGlyphBridge current = bridge;
        if (current == null) {
            synchronized (IronGlyphBridges.class) {
                current = bridge;
                if (current == null) {
                    // FQN inside the gate: the Iron's runtime class is never resolved without Iron's.
                    current = IronsCompat.isLoaded()
                        ? new com.otectus.arsnspells.compat.irons.glyph.IronsGlyphRuntime() : Absent.INSTANCE;
                    bridge = current;
                }
            }
        }
        return current;
    }

    /** Iron's is not installed: every imported glyph is dormant and nothing can be read or cast. */
    static final class Absent implements IronGlyphBridge {
        static final Absent INSTANCE = new Absent();

        @Override public boolean present() { return false; }
        @Override public SpellState spellState(ResourceLocation spell) { return SpellState.MISSING; }
        @Override public String rarity(ResourceLocation spell, int level) { return null; }
        @Override public boolean meetsLearning(Player player, ResourceLocation spell) { return false; }
        @Override public double manaCost(ResourceLocation spell, int level) { return Double.NaN; }
        @Override public int castTimeTicks(ResourceLocation spell, int level) { return -1; }
        @Override public Optional<Component> spellName(ResourceLocation spell) { return Optional.empty(); }
        @Override public ResourceLocation icon(ResourceLocation spell) { return null; }
        @Override public ItemStack filledScroll(ResourceLocation spell, int level) { return ItemStack.EMPTY; }
        @Override public ScrollReading readScroll(ItemStack stack) { return ScrollReading.of(ScrollReading.Kind.NOT_A_SCROLL); }
        @Override public InvocationOutcome invoke(com.otectus.arsnspells.glyph.GlyphInvocation invocation) {
            return InvocationOutcome.rejected("message.ars_n_spells.glyph.dormant.irons_absent");
        }
        @Override public boolean nativeCastInProgress(Player player) { return false; }
        @Override public boolean onCooldown(Player player, ResourceLocation spell) { return false; }
        @Override public boolean hasRecast(Player player, ResourceLocation spell) { return false; }
        @Override public int levelFor(ResourceLocation spell, int level, Player player) { return level; }
        @Override public boolean creativeFree(Player player) { return false; }
        @Override public boolean creativeNoCooldown(Player player) { return false; }
    }
}
