package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.rituals.SpellbookBindingInputs;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossCastValidator;
import com.otectus.arsnspells.spell.CrossModSpell;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import com.otectus.arsnspells.spell.CrossModSpellList;
import com.otectus.arsnspells.spell.CrossSpellType;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import com.otectus.arsnspells.spell.ModDataComponents;
import com.otectus.arsnspells.util.ArsSpellIntegrity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Iron's-less GameTests for the Ars to scroll to spellbook export layer.
 *
 * <p>These run on a real server in the default {@code runGameTestServer} run, where Iron's
 * Spellbooks is <em>not</em> on the runtime classpath (it is {@code compileOnly}). Their job is
 * to prove ANS's own runtime-safe behaviour: classloading safety with Iron's absent, real
 * {@link ItemStack} copy and component mutation, and malformed-item handling.
 *
 * <p>The pure list-level schema contracts belong to the bootstrap-free JUnit suite; this layer
 * covers what JUnit cannot reach - actual {@code ItemStack} behaviour with the mod's data
 * components registered, which only resolve on a loaded server.
 *
 * <p>Vanilla items stand in as generic carrier stacks. They are <em>not</em> pretending to be
 * Iron's items; the point is that ANS's mutation helpers operate correctly on real stacks and
 * that the Iron's-recognition predicates correctly reject non-Iron's items when Iron's is
 * absent.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class ArsIronsExportGameTests {

    private ArsIronsExportGameTests() {}

    private static CompoundTag arsPayload(String body) {
        CompoundTag tag = new CompoundTag();
        tag.putString("recipe", body);
        return tag;
    }

    // ---- Component mutation on a real ItemStack ----

    @GameTest(template = "platform")
    public static void appendArsEntry_onRealItemStack_roundTrips(GameTestHelper helper) {
        ItemStack book = new ItemStack(Items.BOOK);
        book.set(ModDataComponents.EXPORT_MODE.get(), "unrelated_marker");
        CompoundTag payload = arsPayload("glyph_heal");

        if (!IronsBookBindingUtil.appendArsSpellToBook(book, payload)) {
            helper.fail("appendArsSpellToBook returned false for a fresh, valid payload");
            return;
        }
        if (!CrossModSpellComponents.has(book)) {
            helper.fail("expected the cross-spells component to be present after append");
            return;
        }
        CrossModSpellList list = CrossModSpellComponents.get(book);
        if (list.size() != 1) {
            helper.fail("expected exactly one entry, got " + list.size());
            return;
        }
        CrossModSpell entry = list.spells().get(0);
        if (CrossCastValidator.resolveType(entry) != CrossSpellType.ARS_NOUVEAU) {
            helper.fail("entry must resolve as ARS_NOUVEAU");
            return;
        }
        if (!CrossModSpellComponents.ARS_PLACEHOLDER_ID.equals(entry.spellId())) {
            helper.fail("entry must carry the placeholder spell id, got " + entry.spellId());
            return;
        }
        if (!payload.equals(entry.arsSpellTag().orElse(null))) {
            helper.fail("the Ars payload must be stored verbatim");
            return;
        }
        if (!"unrelated_marker".equals(book.get(ModDataComponents.EXPORT_MODE.get()))) {
            helper.fail("unrelated components on the stack must be preserved");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void appendTwoDistinctEntries_onRealItemStack(GameTestHelper helper) {
        ItemStack book = new ItemStack(Items.BOOK);
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("first"));
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("second"));

        CrossModSpellList list = CrossModSpellComponents.get(book);
        if (list.size() != 2) {
            helper.fail("expected two entries, got " + list.size());
            return;
        }
        CompoundTag a = list.spells().get(0).arsSpellTag().orElse(new CompoundTag());
        CompoundTag b = list.spells().get(1).arsSpellTag().orElse(new CompoundTag());
        if (!"first".equals(a.getString("recipe")) || !"second".equals(b.getString("recipe"))) {
            helper.fail("entries must keep insertion order and verbatim payloads, got "
                + a.getString("recipe") + " / " + b.getString("recipe"));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void appendDuplicate_onRealItemStack_isDeduped(GameTestHelper helper) {
        ItemStack book = new ItemStack(Items.BOOK);
        if (!IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("same"))) {
            helper.fail("first append of a unique payload must succeed");
            return;
        }
        if (IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("same"))) {
            helper.fail("re-appending an equal Ars payload must be rejected as a duplicate");
            return;
        }
        if (CrossModSpellComponents.get(book).size() != 1) {
            helper.fail("a duplicate payload must not add a second entry");
            return;
        }
        if (!IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("different"))) {
            helper.fail("a distinct payload must be allowed even with the same placeholder id");
            return;
        }
        if (CrossModSpellComponents.get(book).size() != 2) {
            helper.fail("a distinct payload must add a second entry");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void clearCrossSpells_onRealItemStack_preservesSiblings(GameTestHelper helper) {
        ItemStack book = new ItemStack(Items.BOOK);
        CompoundTag sibling = new CompoundTag();
        sibling.putString("third_party:state", "sibling_value");
        book.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.of(sibling));
        book.set(ModDataComponents.EXPORT_MODE.get(), "ans_owned_marker");
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("glyph_heal"));

        CrossModSpellComponents.clear(book);

        if (book.isEmpty()) {
            helper.fail("clearing inscriptions must not turn the stack into EMPTY");
            return;
        }
        if (CrossModSpellComponents.has(book)) {
            helper.fail("clear must remove the cross-spells component");
            return;
        }
        if (book.has(ModDataComponents.EXPORT_MODE.get()) || book.has(ModDataComponents.SCHEMA_VERSION.get())) {
            helper.fail("public clear must remove ANS-owned export/schema artifacts too"); return;
        }
        var remaining = book.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (remaining == null || !sibling.equals(remaining.copyTag())) {
            helper.fail("clear must not touch unrelated third-party components");
            return;
        }
        helper.succeed();
    }

    // ---- Integrity and validation ----

    /**
     * The serialized {@code recipe} is a LIST of glyph ids, mirroring {@code Spell.CODEC}. The
     * dedup fixtures above use a string under the same key on purpose - it is opaque to the
     * integrity check, which is what lets them exercise dedup without naming real glyphs.
     */
    private static CompoundTag recipeOf(String... glyphIds) {
        CompoundTag tag = new CompoundTag();
        ListTag recipe = new ListTag();
        for (String id : glyphIds) {
            recipe.add(StringTag.valueOf(id));
        }
        tag.put("recipe", recipe);
        return tag;
    }

    @GameTest(template = "platform")
    public static void missingGlyphs_areDetectedBeforeAnythingIsSpent(GameTestHelper helper) {
        // An empty recipe has nothing that can be missing.
        if (!ArsSpellIntegrity.isIntact(recipeOf())) {
            helper.fail("an empty recipe must read as intact");
            return;
        }

        CompoundTag broken = recipeOf("some_mod_that_is_not_installed:impossible_glyph");
        List<String> missing = ArsSpellIntegrity.missingGlyphIds(broken);
        if (missing.size() != 1) {
            helper.fail("the unresolvable glyph must be reported exactly once, got " + missing);
            return;
        }
        if (ArsSpellIntegrity.isIntact(broken)) {
            helper.fail("a payload referencing an uninstalled mod's glyph must not read as intact");
            return;
        }

        // A malformed id is itself a missing glyph, not an exception to propagate into a cast.
        if (ArsSpellIntegrity.isIntact(recipeOf("not a resource location"))) {
            helper.fail("an unparseable glyph id must count as missing, not throw");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void malformedCarrier_isRejectedAndDoesNotThrow(GameTestHelper helper) {
        try {
            // No component at all.
            if (IronsBookBindingUtil.extractSingleEntry(new ItemStack(Items.BOOK)).isPresent()) {
                helper.fail("a stack with no inscription must not yield a carrier");
                return;
            }

            // An entry whose Ars payload is empty is not castable, so not a carrier.
            ItemStack empty = new ItemStack(Items.BOOK);
            CrossModSpellComponents.addArsEntryWithMeta(empty,
                CrossModSpellComponents.ARS_PLACEHOLDER_ID, 1, new CompoundTag(),
                1, null, null, null);
            if (IronsBookBindingUtil.extractSingleEntry(empty).isPresent()) {
                helper.fail("an empty Ars payload must not yield a carrier");
                return;
            }

            // Two entries is ambiguous, so not a carrier either.
            ItemStack two = new ItemStack(Items.BOOK);
            IronsBookBindingUtil.appendArsSpellToBook(two, arsPayload("a"));
            IronsBookBindingUtil.appendArsSpellToBook(two, arsPayload("b"));
            if (IronsBookBindingUtil.extractSingleEntry(two).isPresent()) {
                helper.fail("binding requires an unambiguous single-entry carrier");
                return;
            }

            // Out-of-range selection indices must be refused, not indexed into.
            ItemStack one = new ItemStack(Items.BOOK);
            IronsBookBindingUtil.appendArsSpellToBook(one, arsPayload("only"));
            CrossModSpellList list = CrossModSpellComponents.get(one);
            CrossModSpell entry = list.spells().get(0);
            if (CrossCastValidator.validate(entry, 5, list.size()).ok()) {
                helper.fail("the validator must reject an out-of-range selected index");
                return;
            }
            if (CrossCastValidator.validate(entry, -1, list.size()).ok()) {
                helper.fail("the validator must reject a negative selected index");
                return;
            }
        } catch (Throwable t) {
            helper.fail("malformed-carrier handling must not throw: " + t);
            return;
        }
        helper.succeed();
    }

    // ---- Iron's-absent safety ----

    @GameTest(template = "platform")
    public static void ironAbsent_predicatesAreSafe(GameTestHelper helper) {
        if (IronsCompat.isLoaded()) {
            // This one is specifically about the Iron's-absent path.
            helper.succeed();
            return;
        }
        try {
            ItemStack vanilla = new ItemStack(Items.BOOK);
            if (IronsBookBindingUtil.isIronsScroll(vanilla)) {
                helper.fail("a vanilla book must not be recognised as an Iron's scroll");
                return;
            }
            if (IronsBookBindingUtil.isIronsSpellBook(vanilla)) {
                helper.fail("a vanilla book must not be recognised as an Iron's spellbook");
                return;
            }
            if (!ArsSpellExportUtil.createIronsScrollCarrier(null, "", "", "").isEmpty()) {
                helper.fail("scroll export must return EMPTY when Iron's is absent");
                return;
            }
            Optional<?> extracted = ArsSpellExportUtil.extractArsSpell(vanilla);
            if (extracted.isPresent()) {
                helper.fail("a plain vanilla book carries no Ars spell to extract");
                return;
            }
        } catch (Throwable t) {
            helper.fail("Iron's-absent predicate paths must not throw or classload Iron's: " + t);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void bindingClassifier_handlesVanillaAndEmptyInputsSafely(GameTestHelper helper) {
        SpellbookBindingInputs empty = SpellbookBindingInputs.classify(List.of());
        if (!empty.carrierScrolls.isEmpty() || !empty.spellbooks.isEmpty()
            || !empty.other.isEmpty()) {
            helper.fail("empty input must yield empty buckets");
            return;
        }

        List<ItemEntity> vanilla = List.of(
            new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.BOOK)),
            new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.PAPER)),
            new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.STICK)));

        SpellbookBindingInputs classified = SpellbookBindingInputs.classify(vanilla);
        if (!classified.carrierScrolls.isEmpty()) {
            helper.fail("vanilla items must never classify as carrier scrolls");
            return;
        }
        if (!classified.spellbooks.isEmpty()) {
            helper.fail("vanilla items must never classify as Iron's spellbooks");
            return;
        }
        if (classified.other.size() != 3) {
            helper.fail("all three vanilla items must fall through to 'other', got "
                + classified.other.size());
            return;
        }
        helper.succeed();
    }
}
