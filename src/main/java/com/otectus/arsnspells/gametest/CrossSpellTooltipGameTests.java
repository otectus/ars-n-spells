package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.mojang.authlib.GameProfile;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.events.CrossSpellTooltipHandler;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossCastNbt;
import com.otectus.arsnspells.spell.CrossSpellType;
// Iron's-gated helper: the import is compile-time only and every call sits behind an
// IronsCompat.isLoaded() guard, so it is never resolved on the Iron-absent run.
import com.otectus.arsnspells.spell.irons.CarrierReconciler;
import com.otectus.arsnspells.spell.irons.IronsScrollFactory;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Hovering an ANS-inscribed item must never take the client down.
 *
 * <p>A player reported that creating a spell at the Spell Loom and then hovering the
 * resulting scroll crashed their game. {@link CrossSpellTooltipHandler} is the only ANS code
 * on that path and the only thing that distinguishes an ANS carrier from a plain Iron's
 * scroll as far as the tooltip is concerned; a throw inside it propagates out of
 * {@code ItemStack.getTooltipLines} and into the render loop rather than degrading to a
 * missing line. {@code ArsIronsExportGameTests} explicitly listed tooltip behaviour as
 * uncovered — this closes that gap.
 *
 * <p>The handler is invoked directly with a constructed {@link ItemTooltipEvent} rather than
 * through {@code ItemStack#getTooltipLines}. That is deliberate: the dedicated GameTest
 * server has no {@code Minecraft} instance, so Iron's own {@code Scroll.appendHoverText}
 * short-circuits there and would make an end-to-end call vacuous. Driving the handler
 * directly exercises the real code — {@code Spell.fromTag}, {@code buildDisplayLabel}, the
 * component construction and the list mutation — on a real {@link ItemStack}.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class CrossSpellTooltipGameTests {

    private static final GameProfile FAKE_PROFILE =
        new GameProfile(UUID.fromString("0a000000-0000-0000-0000-00000000b17a"), "ans_tooltip_test");

    private CrossSpellTooltipGameTests() {}

    /** The translation key of every translatable line, in order; literals contribute nothing. */
    private static List<String> translationKeys(List<Component> lines) {
        List<String> keys = new ArrayList<>();
        for (Component line : lines) {
            if (line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
                keys.add(tc.getKey());
            }
        }
        return keys;
    }

    private static List<Component> renderTooltip(GameTestHelper helper, ItemStack stack) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), FAKE_PROFILE);
        List<Component> lines = new ArrayList<>();
        ItemTooltipEvent event =
            new ItemTooltipEvent(stack, player, lines, TooltipFlag.Default.NORMAL);
        CrossSpellTooltipHandler.onItemTooltip(event);
        return event.getToolTip();
    }

    /** A well-formed Ars inscription renders header, entry and cast hint. */
    @GameTest(template = "platform")
    public static void tooltip_onInscribedStack_addsTheAffordanceLines(GameTestHelper helper) {
        Spell heal = new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE);
        ItemStack stack = new ItemStack(Items.PAPER);
        CrossCastNbt.addCrossModSpellToTag(stack.getOrCreateTag(),
            new net.minecraft.resources.ResourceLocation("ars_nouveau", "spell"), 1,
            CrossSpellType.ARS_NOUVEAU, heal.serialize());

        List<Component> lines = renderTooltip(helper, stack);

        if (lines.size() != 3) {
            helper.fail("a single-entry inscription must add header + entry + cast hint, got "
                + lines.size() + ": " + lines);
        }
        helper.succeed();
    }

    /** An un-inscribed stack must be left completely alone. */
    @GameTest(template = "platform")
    public static void tooltip_onPlainStack_addsNothing(GameTestHelper helper) {
        List<Component> lines = renderTooltip(helper, new ItemStack(Items.PAPER));
        if (!lines.isEmpty()) {
            helper.fail("an item with no ANS inscription must get no ANS tooltip lines, got " + lines);
        }
        helper.succeed();
    }

    /**
     * A payload that cannot be parsed must cost a tooltip line, not the client.
     *
     * <p>{@code Spell.fromTag} builds a {@code ResourceLocation} straight from each stored
     * glyph id, so a corrupted or foreign-mod payload throws out of it. Before this was
     * guarded, that throw escaped into the render loop.
     */
    @GameTest(template = "platform")
    public static void tooltip_onCorruptArsPayload_survives(GameTestHelper helper) {
        CompoundTag recipe = new CompoundTag();
        recipe.putInt("size", 1);
        recipe.putString("glyph_0", "not a valid resource location!!");
        CompoundTag payload = new CompoundTag();
        payload.put("recipe", recipe);

        ItemStack stack = new ItemStack(Items.PAPER);
        CrossCastNbt.addCrossModSpellToTag(stack.getOrCreateTag(),
            new net.minecraft.resources.ResourceLocation("ars_nouveau", "spell"), 1,
            CrossSpellType.ARS_NOUVEAU, payload);

        List<Component> lines;
        try {
            lines = renderTooltip(helper, stack);
        } catch (Throwable t) {
            helper.fail("a corrupt Ars payload must not throw out of the tooltip handler: " + t);
            return;
        }
        if (lines.size() != 3) {
            helper.fail("a corrupt payload should still render header + generic entry + cast hint, "
                + "got " + lines.size() + ": " + lines);
        }
        helper.succeed();
    }

    /** An entry whose type is unresolvable falls back to the raw id rather than throwing. */
    @GameTest(template = "platform")
    public static void tooltip_onUnknownEntryType_fallsBackToTheRawId(GameTestHelper helper) {
        CompoundTag entry = new CompoundTag();
        entry.putString(CrossCastNbt.TAG_SPELL_ID, "some_other_mod:mystery");
        entry.putString(CrossCastNbt.TAG_SPELL_TYPE, "NOT_A_REAL_TYPE");
        entry.putInt(CrossCastNbt.TAG_SPELL_LEVEL, 2);
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        list.add(entry);

        ItemStack stack = new ItemStack(Items.PAPER);
        stack.getOrCreateTag().put(CrossCastNbt.TAG_CROSS_MOD_SPELLS, list);

        List<Component> lines = renderTooltip(helper, stack);
        if (lines.size() != 3) {
            helper.fail("an unknown entry type must still render three lines, got " + lines);
        }
        helper.succeed();
    }

    /** The exact stack the Spell Loom hands the player must render its tooltip cleanly. */
    @GameTest(template = "platform")
    public static void ironsLoaded_tooltip_onRealLoomCarrier_survives(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        Spell heal = new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE);
        ItemStack carrier = ArsSpellExportUtil.createIronsScrollCarrier(heal, "Test Weave", "arcane", "spark");
        if (carrier.isEmpty()) {
            helper.fail("export must yield a carrier when Iron's is loaded");
            return;
        }

        List<Component> lines;
        try {
            lines = renderTooltip(helper, carrier);
        } catch (Throwable t) {
            helper.fail("hovering a Spell Loom carrier must not throw: " + t);
            return;
        }
        // A loom carrier carries display metadata and the two-route hint on top of the three
        // lines every inscribed stack gets, so this asserts the lines that must be there rather
        // than a total -- a count would break again the next time a line is added.
        List<String> keys = translationKeys(lines);
        for (String required : List.of("tooltip.ars_n_spells.cross_spell.header",
                                       "tooltip.ars_n_spells.cross_spell.entry",
                                       "tooltip.ars_n_spells.cross_spell.cast_hint",
                                       "tooltip.ars_n_spells.carrier.routes")) {
            if (!keys.contains(required)) {
                helper.fail("a loom carrier tooltip is missing '" + required + "', got " + lines);
            }
        }
        if (lines.stream().noneMatch(line -> line.getString().contains("Test Weave"))) {
            helper.fail("the name the player gave the weave must appear on its tooltip, got "
                + lines);
        }
        helper.succeed();
    }

    /**
     * A carrier whose container tag exists but does not decode must be reported unreadable
     * and repaired.
     *
     * <p>{@code ISpellContainer.get} bottoms out in {@code DataResult.getOrThrow}, so such a
     * stack throws on every read Iron's makes of it, the tooltip included. The old
     * presence-only check called it healthy and left it broken.
     */
    @GameTest(template = "platform")
    public static void ironsLoaded_undecodableContainer_isRepaired(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        Spell heal = new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE);
        ItemStack carrier = ArsSpellExportUtil.createIronsScrollCarrier(heal);
        if (carrier.isEmpty()) {
            helper.fail("export must yield a carrier when Iron's is loaded");
            return;
        }

        // Corrupt the container in place: keep the key, destroy the payload.
        carrier.getOrCreateTag().putString("irons_spellbooks:spell_container", "garbage");

        if (!IronsScrollFactory.hasNativeContainer(carrier)) {
            helper.fail("test setup failed: the container key should still be present");
        }
        if (IronsScrollFactory.hasReadableContainer(carrier)) {
            helper.fail("a container that cannot decode must not be reported as readable — "
                + "that is what let a crashing carrier be handed to the player");
        }

        CarrierReconciler.reconcile(carrier);

        if (!IronsScrollFactory.hasReadableContainer(carrier)) {
            helper.fail("reconcile must replace an undecodable container with a valid one");
        }
        helper.succeed();
    }
}
