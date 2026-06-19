package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.rituals.SpellbookBindingInputs;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossCastNbt;
import com.otectus.arsnspells.spell.CrossCastValidator;
import com.otectus.arsnspells.spell.CrossSpellType;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Phase 1 — Iron-less GameTests for the Ars &rarr; scroll &rarr; spellbook export layer.
 *
 * <p>These run on a real Forge/Minecraft server in the default {@code runGameTestServer}
 * run, where Iron's Spellbooks is <em>not</em> on the runtime classpath (it is
 * {@code compileOnly}). Their job is to prove ANS's own runtime-safe behavior:
 * classloading safety with Iron absent, real {@link ItemStack} copy/tag mutation, and
 * malformed-item handling. Pure NBT/schema contracts are owned by the Bootstrap-free
 * JUnit suite; this layer covers what JUnit cannot — actual {@code ItemStack} behavior on
 * a live server.
 *
 * <p>Vanilla items ({@link Items#BOOK}, {@link Items#PAPER}) stand in as generic carrier
 * stacks. They are <em>not</em> pretending to be Iron items; the point is that ANS's
 * mutation helpers operate correctly on real stacks and that Iron-recognition predicates
 * correctly reject non-Iron items when Iron is absent.
 *
 * <h2>Deferred to a future Iron-loaded integration profile (NOT covered here)</h2>
 * The following require Iron's Spellbooks and its runtime dependency graph to be loaded,
 * which Phase 1 deliberately does not pull into the default GameTest server. They are
 * documented here rather than represented as fake-passing {@code helper.succeed()} stubs:
 * <ul>
 *   <li>Exporting a real Ars parchment/focus/book onto a real {@code irons_spellbooks:scroll}.</li>
 *   <li>Binding a real exported scroll onto a real {@code irons_spellbooks:spell_book}.</li>
 *   <li>A bound Ars entry coexisting with a real {@code ISB_Spells} container.</li>
 *   <li>Casting / sneak-cycling a bound Ars spell from a real spellbook through ANS.</li>
 *   <li>Tooltip rendering on real Iron scrolls/spellbooks with Iron loaded.</li>
 * </ul>
 * These belong in an optional {@code -PwithIronsRuntimeGameTests} profile (future work).
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

    private static CompoundTag firstEntry(ItemStack stack) {
        return stack.getOrCreateTag()
            .getList(CrossCastNbt.TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND).getCompound(0);
    }

    private static int entryCount(ItemStack stack) {
        return stack.getOrCreateTag()
            .getList(CrossCastNbt.TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND).size();
    }

    /** 1 — append an Ars entry onto a real stack and verify schema + sibling preservation. */
    @GameTest(template = "platform")
    public static void appendArsEntry_onRealItemStack_roundTrips(GameTestHelper helper) {
        ItemStack book = new ItemStack(Items.BOOK);
        // Unrelated root NBT a third party might own; must survive untouched.
        CompoundTag foreign = new CompoundTag();
        foreign.putInt("kept", 1);
        book.getOrCreateTag().put("other_mod:data", foreign);

        CompoundTag payload = arsPayload("glyph_touch,glyph_break");
        CompoundTag expectedPayload = payload.copy();

        boolean added = IronsBookBindingUtil.appendArsSpellToBook(book, payload);

        if (!added) {
            helper.fail("appendArsSpellToBook returned false for a fresh, valid payload");
        }
        if (!CrossCastNbt.hasCrossModSpells(book.getOrCreateTag())) {
            helper.fail("expected cross_spells list to be present after append");
        }
        if (entryCount(book) != 1) {
            helper.fail("expected exactly one entry, got " + entryCount(book));
        }
        CompoundTag entry = firstEntry(book);
        if (CrossCastValidator.resolveType(entry) != CrossSpellType.ARS_NOUVEAU) {
            helper.fail("entry must resolve as ARS_NOUVEAU");
        }
        if (!"ars_nouveau:spell".equals(entry.getString(CrossCastNbt.TAG_SPELL_ID))) {
            helper.fail("entry must carry the placeholder spell id");
        }
        // Verbatim, on a REAL ItemStack mutated by the production helper.
        if (!expectedPayload.equals(entry.getCompound(CrossCastNbt.TAG_ARS_SPELL))) {
            helper.fail("ars_spell payload must be stored verbatim");
        }
        if (!foreign.equals(book.getOrCreateTag().getCompound("other_mod:data"))) {
            helper.fail("unrelated root NBT must be preserved");
        }
        helper.succeed();
    }

    /** 2 — two distinct payloads coexist in insertion order with both blobs intact. */
    @GameTest(template = "platform")
    public static void appendTwoDistinctEntries_onRealItemStack(GameTestHelper helper) {
        ItemStack book = new ItemStack(Items.BOOK);
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("a"));
        IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("b"));

        ListTag list = book.getOrCreateTag()
            .getList(CrossCastNbt.TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND);
        if (list.size() != 2) {
            helper.fail("expected two entries, got " + list.size());
        }
        String first = list.getCompound(0).getCompound(CrossCastNbt.TAG_ARS_SPELL).getString("recipe");
        String second = list.getCompound(1).getCompound(CrossCastNbt.TAG_ARS_SPELL).getString("recipe");
        if (!"a".equals(first) || !"b".equals(second)) {
            helper.fail("entries must keep insertion order and verbatim payloads, got "
                + first + "," + second);
        }
        helper.succeed();
    }

    /** 3 — dedup keys off the ars_spell payload, not the shared placeholder id. */
    @GameTest(template = "platform")
    public static void appendDuplicate_onRealItemStack_isDeduped(GameTestHelper helper) {
        ItemStack book = new ItemStack(Items.BOOK);
        boolean first = IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("same"));
        boolean second = IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("same"));

        if (!first) {
            helper.fail("first append of a unique payload must succeed");
        }
        if (second) {
            helper.fail("re-appending an equal ars_spell payload must be rejected as duplicate");
        }
        if (entryCount(book) != 1) {
            helper.fail("duplicate payload must not add a second entry");
        }
        // A different payload sharing the same placeholder id IS allowed.
        if (!IronsBookBindingUtil.appendArsSpellToBook(book, arsPayload("different"))) {
            helper.fail("a distinct payload must be allowed even with the same placeholder id");
        }
        if (entryCount(book) != 2) {
            helper.fail("a distinct payload must add a second entry");
        }
        helper.succeed();
    }

    /** 4 — clear/uninscribe drops only ANS keys and never empties the stack. */
    @GameTest(template = "platform")
    public static void clearCrossSpells_onRealItemStack_preservesSiblings(GameTestHelper helper) {
        ItemStack book = new ItemStack(Items.BOOK);
        CompoundTag tag = book.getOrCreateTag();
        // ANS data.
        CrossCastNbt.addCrossModSpellToTag(tag,
            IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1, CrossSpellType.ARS_NOUVEAU, arsPayload("x"));
        tag.putInt(CrossCastNbt.TAG_SPELL_INDEX, 0);
        // Sibling root data that must survive.
        tag.putInt("Damage", 4);
        CompoundTag isb = new CompoundTag();
        isb.putInt("maxSpells", 3);
        tag.put("ISB_Spells", isb);
        CompoundTag isbBaseline = isb.copy();

        CrossCastNbt.clearCrossModSpells(book);

        if (book.isEmpty()) {
            helper.fail("clearing inscriptions must not turn the stack into EMPTY");
        }
        CompoundTag after = book.getOrCreateTag();
        if (after.contains(CrossCastNbt.TAG_CROSS_MOD_SPELLS)
            || after.contains(CrossCastNbt.TAG_SPELL_INDEX)) {
            helper.fail("clear must remove both ANS keys");
        }
        if (after.getInt("Damage") != 4) {
            helper.fail("unrelated root NBT must survive uninscribe");
        }
        if (!isbBaseline.equals(after.getCompound("ISB_Spells"))) {
            helper.fail("a native ISB_Spells sibling must be untouched by uninscribe");
        }
        helper.succeed();
    }

    /** 5 — malformed carriers are rejected cleanly; helpers never throw and never mutate. */
    @GameTest(template = "platform")
    public static void malformedCarrier_isRejectedAndDoesNotThrow(GameTestHelper helper) {
        try {
            // Wrong tag type at the list key.
            ItemStack wrongType = new ItemStack(Items.PAPER);
            wrongType.getOrCreateTag().putString(CrossCastNbt.TAG_CROSS_MOD_SPELLS, "not-a-list");
            if (IronsBookBindingUtil.extractSingleArsEntry(wrongType).isPresent()) {
                helper.fail("wrong-typed cross_spells key must not yield a carrier");
            }

            // Single entry missing ars_spell.
            ItemStack missingPayload = new ItemStack(Items.PAPER);
            CompoundTag t1 = missingPayload.getOrCreateTag();
            CompoundTag e1 = new CompoundTag();
            e1.putString(CrossCastNbt.TAG_SPELL_TYPE, CrossSpellType.ARS_NOUVEAU.name());
            e1.putString(CrossCastNbt.TAG_SPELL_ID, "ars_nouveau:spell");
            ListTag l1 = new ListTag();
            l1.add(e1);
            t1.put(CrossCastNbt.TAG_CROSS_MOD_SPELLS, l1);
            if (IronsBookBindingUtil.extractSingleArsEntry(missingPayload).isPresent()) {
                helper.fail("entry without ars_spell must not yield a carrier");
            }
            CrossCastValidator.ValidationResult missingRes =
                CrossCastValidator.validate(null, e1, 0, 1);
            if (missingRes.ok()) {
                helper.fail("validator must reject an entry missing ars_spell");
            }

            // Empty ars_spell payload.
            ItemStack emptyPayload = new ItemStack(Items.PAPER);
            CrossCastNbt.addCrossModSpellToTag(emptyPayload.getOrCreateTag(),
                IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1, CrossSpellType.ARS_NOUVEAU, new CompoundTag());
            if (IronsBookBindingUtil.extractSingleArsEntry(emptyPayload).isPresent()) {
                helper.fail("empty ars_spell payload must not yield a carrier");
            }

            // Out-of-range selected index against a valid single-entry list.
            ItemStack okScroll = new ItemStack(Items.PAPER);
            CrossCastNbt.addCrossModSpellToTag(okScroll.getOrCreateTag(),
                IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1, CrossSpellType.ARS_NOUVEAU, arsPayload("ok"));
            CompoundTag okEntry = firstEntry(okScroll);
            if (CrossCastValidator.validate(null, okEntry, 5, 1).ok()) {
                helper.fail("validator must reject an out-of-range selected index");
            }
            if (CrossCastValidator.validate(null, okEntry, -1, 1).ok()) {
                helper.fail("validator must reject a negative selected index");
            }
        } catch (Throwable t) {
            helper.fail("malformed-carrier handling must not throw: "
                + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        helper.succeed();
    }

    /** 6 — with Iron absent, recognition predicates are false and export gates to EMPTY. */
    @GameTest(template = "platform")
    public static void ironAbsent_predicatesAreSafe(GameTestHelper helper) {
        if (IronsCompat.isLoaded()) {
            // This Phase-1 test asserts behavior in the Iron-absent default run. If Iron's is
            // present (an integration profile), skip rather than assert the wrong contract.
            helper.succeed();
            return;
        }
        try {
            ItemStack vanilla = new ItemStack(Items.BOOK);
            if (IronsBookBindingUtil.isIronsScroll(vanilla)) {
                helper.fail("a vanilla book must not be recognized as an Iron's scroll");
            }
            if (IronsBookBindingUtil.isIronsSpellBook(vanilla)) {
                helper.fail("a vanilla book must not be recognized as an Iron's spellbook");
            }
            if (!ArsSpellExportUtil.createIronsScrollCarrier(new Spell()).isEmpty()) {
                helper.fail("scroll export must return EMPTY when Iron's is absent");
            }
            if (ArsSpellExportUtil.extractArsSpell(vanilla).isPresent()) {
                helper.fail("a plain vanilla book carries no Ars spell to extract");
            }
        } catch (Throwable t) {
            helper.fail("Iron-absent predicate paths must not throw or classload Iron: "
                + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        helper.succeed();
    }

    /** 7 — the binding classifier runs on real ItemEntities and never miscategorizes vanilla items. */
    @GameTest(template = "platform")
    public static void bindingClassifier_handlesVanillaAndEmptyInputsSafely(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        // Empty input -> all buckets empty.
        SpellbookBindingInputs empty = SpellbookBindingInputs.classify(List.of());
        if (!empty.carrierScrolls.isEmpty() || !empty.spellbooks.isEmpty() || !empty.other.isEmpty()) {
            helper.fail("empty input must yield empty buckets");
        }

        // Real ItemEntities from vanilla stacks -> everything lands in 'other'.
        ItemEntity book = new ItemEntity(level, 0, 0, 0, new ItemStack(Items.BOOK));
        ItemEntity paper = new ItemEntity(level, 0, 0, 0, new ItemStack(Items.PAPER));
        // Even an inscribed vanilla book must not be mistaken for an Iron carrier/spellbook.
        ItemStack inscribed = new ItemStack(Items.BOOK);
        IronsBookBindingUtil.appendArsSpellToBook(inscribed, arsPayload("z"));
        ItemEntity inscribedEntity = new ItemEntity(level, 0, 0, 0, inscribed);

        SpellbookBindingInputs result =
            SpellbookBindingInputs.classify(List.of(book, paper, inscribedEntity));
        if (!result.carrierScrolls.isEmpty()) {
            helper.fail("vanilla items must never classify as carrier scrolls");
        }
        if (!result.spellbooks.isEmpty()) {
            helper.fail("vanilla items must never classify as Iron spellbooks");
        }
        if (result.other.size() != 3) {
            helper.fail("all three vanilla items must fall through to 'other', got "
                + result.other.size());
        }
        helper.succeed();
    }
}
