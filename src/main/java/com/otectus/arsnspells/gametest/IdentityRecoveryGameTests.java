package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.registry.SpellCasterRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.capability.IPlayerCap;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import com.mojang.authlib.GameProfile;
import com.otectus.arsnspells.block.GlyphPressBlockEntity;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistration;
import com.otectus.arsnspells.glyph.behavior.Dormancy;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.catalog.CatalogEntry;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.catalog.RetainedCatalogs;
import com.otectus.arsnspells.registry.ModBlocksRegistry;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * 3.4.1 review SAVE-01: the package-removal experiment on NeoForge, one phase per run of the same
 * run directory's world (a disposable fixture world, never a player's save), driven by
 * {@code logs/3.4.1/review-1109/tools/save01_experiment.sh neoforge} with {@code -Psave01Phase=<n>}.
 * The phases are the Forge build's (see its class of the same name): 1 store everywhere and
 * snapshot; 2 package removed, identities kept dormant by the retained copy, payloads identical; 3
 * retained copy removed too, world start refused (checked by the script); 4 package reinstalled as a
 * loose catalog; 5 as a catalog-only jar. Without the phase property the test records a skip.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class IdentityRecoveryGameTests {
    static final String PHASE_PROPERTY = "ans.gametest.save01.phase";
    static final ResourceLocation RELIC = ResourceLocation.fromNamespaceAndPath("save01", "relic");
    static final ResourceLocation FIREBOLT = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "firebolt");
    static final BlockPos CHEST = new BlockPos(1600, 0, 1600);
    static final BlockPos PRESS = new BlockPos(1602, 0, 1600);
    static final Path DIR = Path.of("save01");
    static final UUID KNOWER = UUID.nameUUIDFromBytes("ans_gametest/save01_knower".getBytes(StandardCharsets.UTF_8));

    private IdentityRecoveryGameTests() {}

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void save01_packageRemovalExperimentPhase(GameTestHelper helper) {
        String phase = System.getProperty(PHASE_PROPERTY, "");
        if (phase.isEmpty()) {
            ScenarioReport.skipped("save01_experiment", "IdentityRecoveryGameTests#save01_packageRemovalExperimentPhase");
            helper.succeed();
            return;
        }
        ScenarioReport.executed("IdentityRecoveryGameTests#save01_packageRemovalExperimentPhase");
        try {
            switch (phase) {
                case "1" -> write(helper);
                case "2" -> verify(helper, phase, true, null);
                case "4" -> verify(helper, phase, false, "config/save01.json");
                case "5" -> verify(helper, phase, false, "save01_glyphs-1.0.0.jar/save01.json");
                default -> helper.fail("unknown save01 phase " + phase);
            }
        } catch (IOException failure) {
            helper.fail("save01 phase " + phase + " could not read or write its evidence: " + failure);
        }
    }

    private static void write(GameTestHelper helper) throws IOException {
        ServerLevel level = helper.getLevel();
        HolderLookup.Provider registries = level.registryAccess();
        CatalogEntry relic = ImportedGlyphCatalog.get().entry(RELIC);
        helper.assertTrue(relic != null && relic.minLevel() == 1 && relic.registeredMax() == 12, "the save01 package registers relic I-12: " + relic);
        helper.assertTrue(relic.executable(), "with the package installed the relic has its contract");
        level.setChunkForced(CHEST.getX() >> 4, CHEST.getZ() >> 4, true);
        level.setBlockAndUpdate(CHEST, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(PRESS, ModBlocksRegistry.GLYPH_PRESS.get().defaultBlockState());
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(CHEST);
        GlyphPressBlockEntity press = (GlyphPressBlockEntity) level.getBlockEntity(PRESS);
        helper.assertTrue(chest != null && press != null, "the fixture blocks exist");
        Spell spell = new Spell(MethodSelf.INSTANCE, part(RELIC, 2), part(FIREBOLT, 1));
        chest.setItem(0, glyph(RELIC, 2, 3));
        chest.setItem(1, glyph(RELIC, 12, 1));
        chest.setItem(2, glyph(FIREBOLT, 1, 2));
        ItemStack book = new ItemStack(ItemsRegistry.ARCHMAGE_SPELLBOOK.get());
        SpellCasterRegistry.from(book).setSpell(spell).saveToStack(book);
        chest.setItem(3, book);
        chest.setItem(4, ArsSpellExportUtil.createIronsScrollCarrier(spell));
        chest.setChanged();
        press.getItems().setStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT, glyph(RELIC, 2, 1));
        press.setChanged();
        FakePlayer knower = knower(level);
        IPlayerCap knowledge = CapabilityRegistry.getPlayerDataCap(knower);
        knowledge.unlockGlyph(part(RELIC, 2));
        knowledge.unlockGlyph(part(FIREBOLT, 1));
        CompoundTag snapshot = new CompoundTag();
        snapshot.put("chest", chest.saveWithoutMetadata(registries));
        snapshot.put("press_output", press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT).save(registries));
        snapshot.put("player", knower.saveWithoutId(new CompoundTag()));
        snapshot.putString("identity", ImportedGlyphCatalog.get().hash());
        Files.createDirectories(DIR);
        NbtIo.writeCompressed(snapshot, DIR.resolve("snapshot.dat"));
        Files.writeString(DIR.resolve("snapshot.snbt"), snapshot.toString(), StandardCharsets.UTF_8);
        level.getServer().saveEverything(true, true, true);
        evidence("1", List.of("identity " + ImportedGlyphCatalog.get().hash(), "relic " + describe(RELIC, 2), "firebolt " + describe(FIREBOLT, 1),
            "stored: chest 5 stacks, press output, knowledge of 2 glyphs", "retained copies: " + retained()));
        helper.succeed();
    }

    private static void verify(GameTestHelper helper, String phase, boolean expectRetained, String expectSource) throws IOException {
        ServerLevel level = helper.getLevel();
        HolderLookup.Provider registries = level.registryAccess();
        CompoundTag snapshot = NbtIo.readCompressed(DIR.resolve("snapshot.dat"), NbtAccounter.unlimitedHeap());
        CatalogEntry relic = ImportedGlyphCatalog.get().entry(RELIC);
        helper.assertTrue(relic != null && relic.minLevel() == 1 && relic.registeredMax() == 12, "relic I-12 is registered: " + relic);
        Dormancy relicState = GlyphBehavior.current().variant(new ImportedGlyphKey(RELIC, 2)).dormancy();
        if (expectRetained) {
            helper.assertTrue(relic.retired() && relic.sourceCatalog().startsWith(RetainedCatalogs.SOURCE_PREFIX),
                "with the package removed the retained copy keeps the identities, dormant: " + relic.sourceCatalog());
            helper.assertTrue(relicState == Dormancy.PACKAGE_MISSING, "and says the package is missing: " + relicState);
        } else {
            helper.assertTrue(relic.executable() && !relic.sourceCatalog().startsWith(RetainedCatalogs.SOURCE_PREFIX),
                "with the package back the relic has its contract again: " + relic.sourceCatalog());
            helper.assertTrue(ImportedGlyphCatalog.get().hash().equals(snapshot.getString("identity")), "identical identities after reinstalling");
            helper.assertTrue(relic.sources().contains(expectSource), "loaded from " + expectSource + ": " + relic.sources());
        }
        Dormancy fireboltState = GlyphBehavior.current().variant(new ImportedGlyphKey(FIREBOLT, 1)).dormancy();
        if (IronsCompat.isLoaded())
            helper.assertTrue(fireboltState == Dormancy.AVAILABLE || fireboltState == Dormancy.NOT_SYNCED,
                "with Iron's installed its glyph is usable: " + fireboltState);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(CHEST);
        GlyphPressBlockEntity press = (GlyphPressBlockEntity) level.getBlockEntity(PRESS);
        helper.assertTrue(chest != null && press != null, "the fixture chunk loaded with its blocks");
        CompoundTag chestNow = chest.saveWithoutMetadata(registries);
        helper.assertTrue(chestNow.equals(snapshot.getCompound("chest")),
            "the chest's glyphs, book and carrier are unchanged:\nnow  " + chestNow + "\nthen " + snapshot.getCompound("chest"));
        helper.assertTrue(press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT).save(registries).equals(snapshot.get("press_output")),
            "the Press output is unchanged");
        Spell fromBook = SpellCasterRegistry.from(chest.getItem(3)).getSpell();
        List<String> expected = List.of("ars_nouveau:glyph_self", part(RELIC, 2).getRegistryName().toString(),
            part(FIREBOLT, 1).getRegistryName().toString());
        helper.assertTrue(parts(fromBook).equals(expected), "the book's spell decodes whole: " + parts(fromBook));
        var carried = CrossModSpellComponents.get(chest.getItem(4)).spells();
        helper.assertTrue(!carried.isEmpty() && carried.get(0).arsSpellTag().isPresent(), "the carrier still carries its Ars spell");
        Spell fromCarrier = Spell.CODEC.codec().parse(NbtOps.INSTANCE, carried.get(0).arsSpellTag().get()).result().orElse(null);
        helper.assertTrue(fromCarrier != null && parts(fromCarrier).equals(expected), "the exported carrier's spell decodes whole: "
            + (fromCarrier == null ? "undecodable" : parts(fromCarrier)));
        FakePlayer knower = knower(level);
        knower.load(snapshot.getCompound("player"));
        IPlayerCap knowledge = CapabilityRegistry.getPlayerDataCap(knower);
        helper.assertTrue(knowledge.knowsGlyph(part(RELIC, 2)) && knowledge.knowsGlyph(part(FIREBOLT, 1)), "learned glyphs survive");
        Set<String> now = glyphIds(knower.saveWithoutId(new CompoundTag())), then = glyphIds(snapshot.getCompound("player"));
        helper.assertTrue(now.equals(then) && now.size() >= 2, "the knowledge saves back with the same glyphs: now " + now + " then " + then);
        evidence(phase, List.of(
            "identity " + ImportedGlyphCatalog.get().hash() + (ImportedGlyphCatalog.get().hash().equals(snapshot.getString("identity")) ? " (= phase 1)" : " (phase 1 " + snapshot.getString("identity") + ")"),
            "relic " + describe(RELIC, 2) + ", sources " + relic.sources(),
            "firebolt " + describe(FIREBOLT, 1),
            "chest and press output identical to phase 1; book and carrier spells decode whole: " + parts(fromBook) + "; knowledge " + now,
            "retained copies: " + retained()));
        helper.succeed();
    }

    // ---- Helpers ------------------------------------------------------------------------------------

    private static AbstractSpellPart part(ResourceLocation spell, int level) {
        return ImportedGlyphRegistration.effect(new ImportedGlyphKey(spell, level));
    }

    private static ItemStack glyph(ResourceLocation spell, int level, int count) {
        var item = BuiltInRegistries.ITEM.get(new ImportedGlyphKey(spell, level).itemId());
        if (item == null || item == net.minecraft.world.item.Items.AIR) throw new IllegalStateException("no glyph item for " + spell + " level " + level);
        return new ItemStack(item, count);
    }

    private static List<String> parts(Spell spell) {
        List<String> ids = new ArrayList<>();
        for (AbstractSpellPart part : spell.unsafeList()) ids.add(part.getRegistryName().toString());
        return ids;
    }

    private static FakePlayer knower(ServerLevel level) {
        return FakePlayerFactory.get(level, new GameProfile(KNOWER, "save01_knower"));
    }

    /** Every imported glyph id anywhere in a saved player's data (Ars keeps knowledge in a set, in hashing order). */
    private static Set<String> glyphIds(CompoundTag player) {
        Set<String> ids = new TreeSet<>();
        collect(player, ids);
        return ids;
    }

    private static void collect(Tag tag, Set<String> into) {
        if (tag instanceof CompoundTag compound) for (String key : compound.getAllKeys()) collect(compound.get(key), into);
        else if (tag instanceof net.minecraft.nbt.ListTag list) for (Tag element : list) collect(element, into);
        else if (tag != null && tag.getId() == Tag.TAG_STRING && tag.getAsString().startsWith("ars_n_spells:iron/")) into.add(tag.getAsString());
    }

    private static String describe(ResourceLocation spell, int level) {
        CatalogEntry entry = ImportedGlyphCatalog.get().entry(spell);
        Dormancy state = GlyphBehavior.current().variant(new ImportedGlyphKey(spell, level)).dormancy();
        return spell + "@" + level + " status=" + entry.status() + " adapter_v" + entry.adapterVersion() + " availability=" + state;
    }

    private static String retained() {
        Path dir = RetainedCatalogs.directory();
        if (dir == null || !Files.isDirectory(dir)) return "none";
        try (var files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).sorted().toList().toString();
        } catch (IOException unreadable) {
            return "unreadable: " + unreadable;
        }
    }

    private static void evidence(String phase, List<String> lines) throws IOException {
        Files.createDirectories(DIR);
        Files.writeString(DIR.resolve("phase" + phase + ".txt"), String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }
}
