package com.otectus.arsnspells.glyph;

import com.otectus.arsnspells.glyph.behavior.GlyphBehaviorSnapshot;
import com.otectus.arsnspells.glyph.catalog.SpellInventoryEntry;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** The bridge when Iron's is not installed: imported glyphs stay registered and dormant. */
public final class AbsentIronGlyphBridge implements IronGlyphBridge {

    public static final AbsentIronGlyphBridge INSTANCE = new AbsentIronGlyphBridge();

    private AbsentIronGlyphBridge() {
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public List<SpellInventoryEntry> inventory() {
        return List.of();
    }

    @Override
    public GlyphBehaviorSnapshot.LiveSpell liveSpell(ResourceLocation spellId) {
        return null;
    }

    @Override
    public boolean hasLearned(net.minecraft.server.level.ServerPlayer player, ResourceLocation spellId) {
        return false;
    }

    private static net.minecraft.network.chat.Component absent() {
        return net.minecraft.network.chat.Component.translatable("ars_n_spells.glyph.dormant.irons_absent");
    }

    @Override
    public net.minecraft.network.chat.Component nativeRefusal(net.minecraft.server.level.ServerPlayer player, ResourceLocation spellId, boolean checkCooldown) {
        return absent();
    }

    @Override
    public boolean hasPendingRecast(net.minecraft.server.level.ServerPlayer player, ResourceLocation spellId) {
        return false;
    }

    @Override
    public int manaCost(ResourceLocation spellId, int level) {
        return 0;
    }

    @Override
    public boolean chargesMana(net.minecraft.server.level.ServerPlayer player) {
        return true;
    }

    @Override
    public int effectiveCastTime(ResourceLocation spellId, int level, net.minecraft.server.level.ServerPlayer player) {
        return 0;
    }

    @Override
    public boolean interruptsCharge(ResourceLocation spellId, net.minecraft.server.level.ServerPlayer player,
                                    net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    @Override
    public void chargeStarted(com.otectus.arsnspells.glyph.runtime.GlyphRequest request) {
    }

    @Override
    public ExecutionResult execute(com.otectus.arsnspells.glyph.runtime.GlyphRequest request, PaymentGate gate) {
        return ExecutionResult.refused(absent());
    }

    @Override
    public com.otectus.arsnspells.glyph.runtime.GlyphChannel startChannel(com.otectus.arsnspells.glyph.runtime.GlyphRequest request,
                                                                          int extraPulses, PaymentGate gate, PulseGate pulses) {
        return null;
    }

    @Override
    public com.otectus.arsnspells.glyph.recipe.ScrollReading readScroll(net.minecraft.world.item.ItemStack stack) {
        return stack.isEmpty() ? com.otectus.arsnspells.glyph.recipe.ScrollReading.EMPTY
            : com.otectus.arsnspells.glyph.recipe.ScrollReading.IRONS_ABSENT;
    }

    @Override
    public boolean isScrollItem(net.minecraft.world.item.ItemStack stack) {
        return false;
    }

    @Override
    public net.minecraft.world.item.ItemStack scrollStack(ResourceLocation spellId, int level) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    @Override
    public int schoolColor(ResourceLocation spellId) {
        return 0x9B6BD6;
    }
}
