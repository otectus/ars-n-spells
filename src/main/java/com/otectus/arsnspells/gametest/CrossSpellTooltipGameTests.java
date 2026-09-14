package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.events.CrossSpellTooltipHandler;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The cross-spell tooltip must describe what an inscribed item can do, and must never be the
 * thing that throws while a player is simply hovering an item.
 *
 * <p>A tooltip handler is unusually exposed: it runs on every hover, on the client, against
 * whatever data happens to be on the stack - including data written by an older build, or by a
 * mod that has since been removed. So the corrupt-payload cases here matter as much as the
 * happy path.
 *
 * <p>Runs as a GameTest rather than a unit test because the tooltip reads data components,
 * which only resolve on a loaded server.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class CrossSpellTooltipGameTests {

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

    /** Drive the real tooltip handler and return only the lines it added. */
    private static List<Component> renderTooltip(GameTestHelper helper, ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        // 1.21.1 signature: (stack, player, tooltip, flag, context). A null player is exactly
        // the case the handler has to survive - tooltips render before a player is available
        // in several contexts, and JEI renders them with none at all.
        ItemTooltipEvent event = new ItemTooltipEvent(
            stack, null, lines, TooltipFlag.NORMAL, Item.TooltipContext.EMPTY);
        CrossSpellTooltipHandler.onItemTooltip(event);
        return lines;
    }

    private static CompoundTag arsPayload(String body) {
        CompoundTag tag = new CompoundTag();
        tag.putString("recipe", body);
        return tag;
    }

    private static ItemStack inscribed(CompoundTag payload) {
        ItemStack stack = new ItemStack(Items.BOOK);
        CrossModSpellComponents.addArsEntryWithMeta(stack,
            CrossModSpellComponents.ARS_PLACEHOLDER_ID, 1, payload, 1, null, null, null);
        return stack;
    }

    @GameTest(template = "platform")
    public static void tooltip_onInscribedStack_addsTheAffordanceLines(GameTestHelper helper) {
        List<Component> lines = renderTooltip(helper, inscribed(arsPayload("glyph_heal")));
        if (lines.size() < 3) {
            helper.fail("a single-entry inscription must add a header, an entry line and a cast "
                + "hint, got " + lines.size() + ": " + lines);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void tooltip_onPlainStack_addsNothing(GameTestHelper helper) {
        List<Component> lines = renderTooltip(helper, new ItemStack(Items.BOOK));
        if (!lines.isEmpty()) {
            helper.fail("an item with no ANS inscription must get no ANS tooltip lines, got "
                + lines);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void tooltip_onCorruptArsPayload_survives(GameTestHelper helper) {
        // A payload whose glyphs no longer resolve: the mod that owned them is gone. The
        // tooltip must degrade to a generic description, not throw on the render thread.
        CompoundTag corrupt = new CompoundTag();
        corrupt.putString("recipe", "a_mod_that_is_gone:vanished_glyph");
        List<Component> lines;
        try {
            lines = renderTooltip(helper, inscribed(corrupt));
        } catch (Throwable t) {
            helper.fail("a corrupt Ars payload must not throw out of the tooltip handler: " + t);
            return;
        }
        if (lines.size() < 3) {
            helper.fail("a corrupt payload should still render a header, a generic entry and a "
                + "cast hint, got " + lines.size() + ": " + lines);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void tooltip_onUnknownEntryType_fallsBackToTheRawId(GameTestHelper helper) {
        // An entry naming a spell from a mod that is not installed. The type cannot resolve,
        // so the tooltip has nothing to look up - and must say something rather than nothing.
        ItemStack stack = new ItemStack(Items.BOOK);
        CrossModSpellComponents.addCrossModSpell(stack,
            ResourceLocation.fromNamespaceAndPath("some_absent_mod", "mystery_spell"), 3,
            com.otectus.arsnspells.spell.CrossSpellType.IRONS_SPELLBOOKS, null);

        List<Component> lines;
        try {
            lines = renderTooltip(helper, stack);
        } catch (Throwable t) {
            helper.fail("an unresolvable entry type must not throw out of the tooltip: " + t);
            return;
        }
        if (lines.size() < 3) {
            helper.fail("an unknown entry type must still render three lines, got " + lines.size()
                + ": " + lines);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void ironsLoaded_tooltip_onRealLoomCarrier_survives(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        // A real loom carrier, not a book with a payload stapled on: the route lines are
        // driven by ScrollKind, which only calls a genuine Iron's scroll a carrier.
        ItemStack carrier = ArsSpellExportUtil.createIronsScrollCarrier(
            new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE), "Test Weave", "arcane", "spark");
        if (carrier.isEmpty()) {
            helper.fail("the loaded profile must produce an actual exported Iron's scroll");
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
}
