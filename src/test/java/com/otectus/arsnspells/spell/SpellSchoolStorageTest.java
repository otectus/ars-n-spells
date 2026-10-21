package com.otectus.arsnspells.spell;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the explicit school is stored in the {@code cross_spells} component, what an entry from
 * before the field existed means, and how duplicate detection treats school variants and cosmetic
 * differences. Codec and stream codec, no bootstrap.
 */
class SpellSchoolStorageTest {

    /** A NeoForge 1.21.1-shaped Ars payload: cosmetic keys around a two-part recipe. */
    private static CompoundTag payload(String spellName, int colour) {
        ListTag recipe = new ListTag();
        recipe.add(StringTag.valueOf("ars_nouveau:glyph_projectile"));
        recipe.add(StringTag.valueOf("ars_nouveau:glyph_harm"));
        CompoundTag tag = new CompoundTag();
        tag.putString("name", spellName);
        CompoundTag color = new CompoundTag();
        color.putInt("r", colour);
        tag.put("color", color);
        tag.put("sound", new CompoundTag());
        tag.put("recipe", recipe);
        return tag;
    }

    private static CrossModSpellList withEntry(String school) {
        return CrossModSpellComponents.withArsEntry(CrossModSpellList.EMPTY,
            CrossModSpellComponents.ARS_PLACEHOLDER_ID, 1, payload("Bolt", 1),
            CrossModSpellComponents.NO_PROXY_POOL_ID, "Bolt", "fire", "spark", school);
    }

    private static CrossModSpellList codecRoundTrip(CrossModSpellList list) {
        Tag encoded = CrossModSpellList.CODEC.encodeStart(NbtOps.INSTANCE, list).getOrThrow();
        return CrossModSpellList.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
    }

    @Test
    void explicitSchool_survivesTheSaveCodecAndTheNetworkCodec() {
        CrossModSpellList list = withEntry("irons_spellbooks:ice");
        assertEquals(Optional.of("irons_spellbooks:ice"), codecRoundTrip(list).spells().get(0).school());

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        CrossModSpellList.STREAM_CODEC.encode(buffer, list);
        CrossModSpellList synced = CrossModSpellList.STREAM_CODEC.decode(buffer);
        assertEquals(list, synced, "the client must receive the same entry, school included");
        assertEquals(Optional.of("fire"), synced.spells().get(0).nature(), "the cosmetic background stays separate");
    }

    @Test
    void automatic_writesNoSchoolField() {
        Tag encoded = CrossModSpellList.CODEC.encodeStart(NbtOps.INSTANCE, withEntry(null)).getOrThrow();
        CompoundTag entry = ((CompoundTag) encoded).getList("spells", Tag.TAG_COMPOUND).getCompound(0);
        assertFalse(entry.contains("school"), "Automatic must be absent, exactly like a pre-3.3.7 entry");
        assertEquals(withEntry(null), withEntry(""), "empty and null are both Automatic");
    }

    @Test
    void aLegacyEntryWithANature_decodesAsAutomatic() {
        CompoundTag legacy = new CompoundTag();
        legacy.putString("spell_id", "ars_nouveau:spell");
        legacy.putInt("spell_level", 1);
        legacy.putString("spell_type", "ARS_NOUVEAU");
        legacy.put("ars_spell", payload("Old", 1));
        legacy.putInt("proxy_pool_id", 2);
        legacy.putString("nature", "ice");
        CrossModSpell decoded = CrossModSpell.CODEC.parse(NbtOps.INSTANCE, legacy).getOrThrow();
        assertEquals(Optional.empty(), decoded.school(), "a 3.3.x entry with an ice frame is Automatic");
        assertEquals(Optional.of("ice"), decoded.nature());
        assertEquals(legacy, CrossModSpell.CODEC.encodeStart(NbtOps.INSTANCE, decoded).getOrThrow(),
            "re-saving a legacy entry must not invent a school");
    }

    @Test
    void unknownExtensionFields_arePreservedAlongsideTheSchool() {
        CompoundTag stored = (CompoundTag) CrossModSpell.CODEC.encodeStart(NbtOps.INSTANCE,
            withEntry("irons_spellbooks:holy").spells().get(0)).getOrThrow();
        stored.putString("future_field", "kept");
        CrossModSpell decoded = CrossModSpell.CODEC.parse(NbtOps.INSTANCE, stored).getOrThrow();
        assertEquals(Optional.of("irons_spellbooks:holy"), decoded.school());
        assertEquals(stored, CrossModSpell.CODEC.encodeStart(NbtOps.INSTANCE, decoded).getOrThrow());
    }

    @Test
    void settingTheSchool_touchesOnlyTheSchool() {
        CrossModSpellList list = withEntry(null);
        CrossModSpellList fire = CrossModSpellComponents.withEntrySchool(list, 0, "irons_spellbooks:fire");
        assertEquals(Optional.of("irons_spellbooks:fire"), fire.spells().get(0).school());
        assertEquals(list.spells().get(0), fire.spells().get(0).withSchool(null),
            "the spell, name, background, icon and pool id must not change");
        assertEquals(list, CrossModSpellComponents.withEntrySchool(fire, 0, ""), "clearing restores Automatic exactly");
        assertEquals(list, CrossModSpellComponents.withEntrySchool(list, 5, "irons_spellbooks:fire"),
            "an out-of-range entry changes nothing");
    }

    @Test
    void dedup_cosmeticOnlyDifferencesAreDuplicates_andSchoolVariantsAreDistinct() {
        CrossModSpellList book = CrossModSpellComponents.withArsEntry(CrossModSpellList.EMPTY,
            CrossModSpellComponents.ARS_PLACEHOLDER_ID, 1, payload("Bolt", 1), 1, "Bolt", "", "", "irons_spellbooks:ice");

        assertTrue(CrossModSpellComponents.containsEquivalentArsSpell(book, payload("Renamed", 9), "ice"),
            "a different name or colour must not bypass dedup; 'ice' is the same school as irons_spellbooks:ice");
        assertFalse(CrossModSpellComponents.containsEquivalentArsSpell(book, payload("Bolt", 1), null),
            "the Automatic variant of the same glyphs is a different spell");
        assertFalse(CrossModSpellComponents.containsEquivalentArsSpell(book, payload("Bolt", 1), "irons_spellbooks:fire"),
            "a different chosen school is a different spell");

        assertEquals(IronsBookBindingUtil.AppendResult.DUPLICATE,
            IronsBookBindingUtil.planAppend(book, payload("Copy", 3), "irons_spellbooks:ice", -1).result());
        IronsBookBindingUtil.PlannedAppend variant =
            IronsBookBindingUtil.planAppend(book, payload("Bolt", 1), "irons_spellbooks:fire", -1);
        assertEquals(IronsBookBindingUtil.AppendResult.ADDED, variant.result());
        assertEquals(2, variant.poolId(), "a school variant gets its own wheel slot");
    }

    @Test
    void mechanicalPayload_dropsOnlyCosmeticKeys() {
        CompoundTag mechanical = CrossModSpellComponents.mechanicalPayload(payload("Name", 5));
        assertEquals(1, mechanical.size(), "name, color and sound are cosmetic; the recipe is not");
        assertTrue(mechanical.contains("recipe", Tag.TAG_LIST));
    }
}
