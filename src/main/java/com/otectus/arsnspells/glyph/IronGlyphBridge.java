package com.otectus.arsnspells.glyph;

import com.otectus.arsnspells.glyph.behavior.GlyphBehaviorSnapshot;
import com.otectus.arsnspells.glyph.catalog.SpellInventoryEntry;
import com.otectus.arsnspells.glyph.runtime.GlyphChannel;
import com.otectus.arsnspells.glyph.runtime.GlyphRequest;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Everything imported glyphs need from Iron's Spells 'n Spellbooks, behind one interface.
 *
 * <p>Iron's is optional. Common code (registration, the Glyph Press, codecs, menus, tooltips, the
 * glyph runtime) only ever talks to this interface; {@link IronGlyphBridges#get()} hands out the
 * Iron's-backed implementation when Iron's is loaded and {@link AbsentIronGlyphBridge} otherwise,
 * so no Iron's class is resolved on an install without it.
 */
public interface IronGlyphBridge {

    /** Whether Iron's is loaded and its spell registry can be read. */
    boolean available();

    /** The loaded Iron's spell registry as plain records, sorted by spell id; empty when absent. */
    List<SpellInventoryEntry> inventory();

    /**
     * Live registry and spell-config facts for one spell, or null when Iron's is absent. Rarity is
     * read only for levels {@code 1..registeredMax}: never a list proportional to the configured maximum.
     */
    @Nullable
    GlyphBehaviorSnapshot.LiveSpell liveSpell(ResourceLocation spellId, int registeredMax);

    /**
     * Iron's own level for {@code level} ({@code AbstractSpell.getLevelFor}: Curios affinity and
     * {@code ModifySpellLevelEvent}). Posts the event: call once per occurrence, never to preview.
     */
    int levelFor(ResourceLocation spellId, int level, ServerPlayer player);

    /** Whether {@code player} has learned {@code spellId} in Iron's (only Eldritch spells require it). */
    boolean hasLearned(ServerPlayer player, ResourceLocation spellId);

    // ---- Glyph Press ----

    /** Classify the Press's scroll slot strictly (see {@link com.otectus.arsnspells.glyph.recipe.ScrollReading}). */
    com.otectus.arsnspells.glyph.recipe.ScrollReading readScroll(net.minecraft.world.item.ItemStack stack);

    /** Whether {@code stack} is a native Iron's scroll item, whatever it holds (the Press's slot filter). */
    boolean isScrollItem(net.minecraft.world.item.ItemStack stack);

    /** A real Iron's scroll holding {@code spellId} at {@code level}, or empty when Iron's is absent. */
    net.minecraft.world.item.ItemStack scrollStack(ResourceLocation spellId, int level);

    /** The spell's school colour as {@code 0xRRGGBB}, for the Press's particles; a neutral violet without Iron's. */
    int schoolColor(ResourceLocation spellId);

    // ---- Execution ----

    /** Iron's native refusal for casting this spell now, or null when allowed (learning, adventure mode, cooldown). */
    @Nullable
    Component nativeRefusal(ServerPlayer player, ResourceLocation spellId, boolean checkCooldown);

    /** Whether a native recast of this spell is pending for the player (recasts ignore the cooldown, as in Iron's). */
    boolean hasPendingRecast(ServerPlayer player, ResourceLocation spellId);

    /** Iron's base mana cost of the spell at {@code level}. */
    int manaCost(ResourceLocation spellId, int level);

    /** Whether Iron's charges this player for casts (creative players only when Iron's says so). */
    boolean chargesMana(ServerPlayer player);

    /** Iron's effective cast time in ticks for a charged or continuous spell, cast-time reduction included. */
    int effectiveCastTime(ResourceLocation spellId, int level, ServerPlayer player);

    /**
     * Whether damage from {@code source} interrupts a charge of this spell, by Iron's own rule: a
     * long cast without a Concentration Amulet, and a damage type outside Iron's long-cast-ignore tag.
     */
    boolean interruptsCharge(ResourceLocation spellId, ServerPlayer player, net.minecraft.world.damagesource.DamageSource source);

    /**
     * Start a charged execution on a detached slot in Iron's native order (see
     * {@link com.otectus.arsnspells.glyph.runtime.GlyphCharge}). Null when Iron's refused it at
     * initiation, with the reason in {@code refusalOut[0]}.
     */
    @Nullable
    com.otectus.arsnspells.glyph.runtime.GlyphCharge startCharge(GlyphRequest request, int ticks, Component[] refusalOut);

    /** Run one instant execution. {@code gate} is asked to pay once the native events have run. */
    ExecutionResult execute(GlyphRequest request, PaymentGate gate);

    /** Start a continuous spell. Null when it could not start; {@code gate} paid the first pulse. */
    @Nullable
    GlyphChannel startChannel(GlyphRequest request, int extraPulses, PaymentGate gate, PulseGate pulses);

    /** Pays for one execution after Iron's native cast events, before the effect. */
    @FunctionalInterface
    interface PaymentGate {
        /** @return whether the execution may proceed (paid, or free) */
        boolean pay(int finalIronsCost, int finalLevel);
    }

    /** Pays for each later pulse of a channel. */
    @FunctionalInterface
    interface PulseGate {
        boolean payPulse(int ironsCost);
    }

    /**
     * How far one execution got (3.4.1 review CAST-04). Before the payment boundary nothing was spent;
     * once the effect boundary is crossed the execution is paid, counts as an emission and owes its
     * cooldown, whether the native effect then completed or threw.
     */
    enum Stage {
        /** Refused before anything was paid (a veto, a native condition, an unreadable spell). */
        REFUSED_BEFORE_PAYMENT,
        /** The payment gate refused it; nothing was taken. */
        REFUSED_AT_PAYMENT,
        /** The native effect ran. */
        COMPLETED,
        /** The native effect began and threw; it stays paid. */
        FAILED_AFTER_EFFECT
    }

    /** The result of one execution; {@code finalLevel} is the level Iron's cast event settled on, or -1. */
    record ExecutionResult(Stage stage, @Nullable Component refusal, boolean paid, int finalLevel) {
        public static ExecutionResult refused(Component reason) {
            return new ExecutionResult(Stage.REFUSED_BEFORE_PAYMENT, reason, false, -1);
        }

        /** Whether the effect boundary was crossed: the execution is paid and counted. */
        public boolean applied() {
            return stage == Stage.COMPLETED || stage == Stage.FAILED_AFTER_EFFECT;
        }
    }
}
