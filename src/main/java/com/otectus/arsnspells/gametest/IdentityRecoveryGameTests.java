package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.util.CasterUtil;
import com.hollingsworth.arsnouveau.common.capability.IPlayerCap;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.mojang.authlib.GameProfile;
import com.otectus.arsnspells.block.GlyphPressBlockEntity;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.glyph.GlyphAvailability;
import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.ImportedGlyphDefinition;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistrar;
import com.otectus.arsnspells.glyph.RetainedCatalogs;
import com.otectus.arsnspells.registry.ModBlocksRegistry;
import com.otectus.arsnspells.spell.CrossCastNbt;
import com.otectus.arsnspells.spell.CrossCastingHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 3.4.1 review SAVE-01: the package-removal experiment, one phase per run of the same run
 * directory's world (a disposable fixture world, never a player's save), driven by
 * {@code logs/3.4.1/review-1109/tools/save01_experiment.sh} with {@code -Psave01Phase=<n>}:
 *
 * <ol>
 *   <li>with the {@code save01} catalog package installed (and Iron's loaded), store imported glyphs
 *       of it and of Iron's in a chest, a spell book, an exported carrier, a Glyph Press output slot
 *       and a player's Ars knowledge, in a force-loaded chunk, and snapshot all of it;</li>
 *   <li>with the package <em>and</em> Iron's removed: the retained copy keeps the package's identities
 *       registered (dormant, "package missing"), Iron's glyphs are dormant ("Iron's absent"), and every
 *       payload is identical to the snapshot;</li>
 *   <li>with the retained copy also deleted: the world start is refused before anything loads (checked
 *       by the script: the log's report and unchanged world files);</li>
 *   <li>with the package and Iron's reinstalled: identical payloads, identical identities, the
 *       package's glyphs supported again and Iron's available again;</li>
 *   <li>the same with the package installed as a catalog-only jar instead of a loose config file.</li>
 * </ol>
 * Without the phase property the test records a skip and succeeds.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class IdentityRecoveryGameTests {
    static final String PHASE_PROPERTY = "ans.gametest.save01.phase";
    static final ResourceLocation RELIC = new ResourceLocation("save01", "relic");
    static final ResourceLocation FIREBOLT = new ResourceLocation("irons_spellbooks", "firebolt");
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
                case "4" -> verify(helper, phase, false, "config/ars_n_spells/glyph_catalogs/save01.json");
                case "5" -> verify(helper, phase, false, "save01_glyphs-1.0.0.jar/ans_glyph_catalog/save01.json");
                default -> helper.fail("unknown save01 phase " + phase);
            }
        } catch (IOException failure) {
            helper.fail("save01 phase " + phase + " could not read or write its evidence: " + failure);
        }
    }

    // ---- Phase 1: store glyphs everywhere a world keeps them, and snapshot ---------------------------

    private static void write(GameTestHelper helper) throws IOException {
        ServerLevel level = helper.getLevel();
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.get();
        helper.assertTrue(catalog.registeredMax(RELIC) == 12, "the save01 package registers relic I-12: " + catalog.levelsOf(RELIC).size());
        helper.assertTrue(catalog.levelsOf(RELIC).get(0).executable(), "with the package installed the relic has its contract");
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
        chest.setItem(3, TouchRegressionGameTests.nativeBook(spell));
        chest.setItem(4, TouchRegressionGameTests.genericCarrier(spell));
        chest.setChanged();
        press.getItems().setStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT, glyph(RELIC, 2, 1));
        press.setChanged();
        FakePlayer knower = knower(level);
        IPlayerCap knowledge = CapabilityRegistry.getPlayerDataCap(knower).orElseThrow(IllegalStateException::new);
        knowledge.unlockGlyph(part(RELIC, 2));
        knowledge.unlockGlyph(part(FIREBOLT, 1));
        CompoundTag snapshot = new CompoundTag();
        snapshot.put("chest", chest.saveWithoutMetadata());
        snapshot.put("press_output", press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT).save(new CompoundTag()));
        snapshot.put("player", knower.saveWithoutId(new CompoundTag()));
        snapshot.putString("identity", catalog.identityDigest());
        Files.createDirectories(DIR);
        NbtIo.writeCompressed(snapshot, DIR.resolve("snapshot.dat").toFile());
        Files.writeString(DIR.resolve("snapshot.snbt"), snapshot.toString(), StandardCharsets.UTF_8);
        level.getServer().saveEverything(true, true, true);
        evidence("1", List.of("identity " + catalog.identityDigest(), "relic " + describe(RELIC, 2), "firebolt " + describe(FIREBOLT, 1),
            "stored: chest " + chest.saveWithoutMetadata().getList("Items", Tag.TAG_COMPOUND).size() + " stacks, press output, knowledge of 2 glyphs",
            "retained copies: " + retained()));
        helper.succeed();
    }

    // ---- Phases 2, 4 and 5: everything is still there ------------------------------------------------

    private static void verify(GameTestHelper helper, String phase, boolean expectRetained, String expectSource) throws IOException {
        ServerLevel level = helper.getLevel();
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.get();
        CompoundTag snapshot = NbtIo.readCompressed(DIR.resolve("snapshot.dat").toFile());
        List<String> lines = new ArrayList<>();
        // Identities: every relic level is registered whatever the package's state.
        helper.assertTrue(catalog.registeredMax(RELIC) == 12 && catalog.minLevel(RELIC) == 1, "relic I-12 is registered: " + catalog.levelsOf(RELIC).size());
        ImportedGlyphDefinition relic = catalog.levelsOf(RELIC).get(0);
        GlyphAvailability.Status relicStatus = GlyphAvailability.status(relic);
        if (expectRetained) {
            helper.assertTrue(relic.retired() && relic.source().startsWith(RetainedCatalogs.SOURCE_PREFIX),
                "with the package removed the retained copy keeps the identities, dormant: " + relic.source());
            helper.assertTrue(relicStatus.reason() == GlyphAvailability.Reason.PACKAGE_MISSING, "and says the package is missing: " + relicStatus);
        } else {
            helper.assertTrue(relic.executable() && !relic.source().startsWith(RetainedCatalogs.SOURCE_PREFIX),
                "with the package back the relic has its contract again: " + relic.source());
            helper.assertTrue(catalog.identityDigest().equals(snapshot.getString("identity")), "identical identities after reinstalling");
            helper.assertTrue(catalog.provenance(RELIC).map(p -> p.sources().contains(expectSource)).orElse(false),
                "loaded from " + expectSource + ": " + catalog.provenance(RELIC));
        }
        GlyphAvailability.Status fireboltStatus = GlyphAvailability.status(catalog.levelsOf(FIREBOLT).get(0));
        if (IronsCompat.isLoaded())
            helper.assertTrue(fireboltStatus.available() || fireboltStatus.reason() == GlyphAvailability.Reason.NOT_SYNCED
                || fireboltStatus.provisional(), "with Iron's installed its glyph is usable again: " + fireboltStatus);
        else helper.assertTrue(fireboltStatus.reason() == GlyphAvailability.Reason.IRONS_ABSENT,
            "with Iron's removed its glyph is dormant, not gone: " + fireboltStatus);
        // Payloads: byte-identical to what phase 1 stored, and every stored spell still decodes whole.
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(CHEST);
        GlyphPressBlockEntity press = (GlyphPressBlockEntity) level.getBlockEntity(PRESS);
        helper.assertTrue(chest != null && press != null, "the fixture chunk loaded with its blocks");
        helper.assertTrue(chest.saveWithoutMetadata().equals(snapshot.getCompound("chest")),
            "the chest's glyphs, book and carrier are unchanged:\nnow  " + chest.saveWithoutMetadata() + "\nthen " + snapshot.getCompound("chest"));
        helper.assertTrue(press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT).save(new CompoundTag())
            .equals(snapshot.getCompound("press_output")), "the Press output is unchanged");
        Spell fromBook = CasterUtil.getCaster(chest.getItem(3)).getSpell(0);
        helper.assertTrue(parts(fromBook).equals(List.of("ars_nouveau:glyph_self", part(RELIC, 2).getRegistryName().toString(),
            part(FIREBOLT, 1).getRegistryName().toString())), "the book's spell decodes whole: " + parts(fromBook));
        ListTag carried = CrossCastingHandler.getCrossModSpells(chest.getItem(4));
        Spell fromCarrier = Spell.fromTag(carried.getCompound(0).getCompound(CrossCastNbt.TAG_ARS_SPELL));
        helper.assertTrue(parts(fromCarrier).equals(parts(fromBook)), "the exported carrier's spell decodes whole: " + parts(fromCarrier));
        // Knowledge: a player saved knowing both glyphs still knows them when loaded.
        FakePlayer knower = knower(level);
        knower.load(snapshot.getCompound("player"));
        IPlayerCap knowledge = CapabilityRegistry.getPlayerDataCap(knower).orElseThrow(IllegalStateException::new);
        helper.assertTrue(knowledge.knowsGlyph(part(RELIC, 2)) && knowledge.knowsGlyph(part(FIREBOLT, 1)),
            "learned glyphs survive: " + knowledge.getKnownGlyphs());
        CompoundTag arsNow = arsCaps(knower.saveWithoutId(new CompoundTag()));
        CompoundTag arsThen = arsCaps(snapshot.getCompound("player"));
        // Ars keeps known glyphs in a set, so their saved order follows hashing and differs between runs: compare as sets.
        helper.assertTrue(knownGlyphs(arsNow).equals(knownGlyphs(arsThen)) && withoutGlyphs(arsNow).equals(withoutGlyphs(arsThen)),
            "the knowledge saves back unchanged:\nnow  " + arsNow + "\nthen " + arsThen);
        lines.add("identity " + catalog.identityDigest() + (catalog.identityDigest().equals(snapshot.getString("identity")) ? " (= phase 1)" : " (phase 1 " + snapshot.getString("identity") + ")"));
        lines.add("relic " + describe(RELIC, 2) + ", source " + relic.source());
        lines.add("firebolt " + describe(FIREBOLT, 1));
        lines.add("chest, press output and knowledge identical to phase 1; book and carrier spells decode whole: " + parts(fromBook));
        lines.add("retained copies: " + retained());
        evidence(phase, lines);
        helper.succeed();
    }

    // ---- Helpers ------------------------------------------------------------------------------------

    private static AbstractSpellPart part(ResourceLocation spell, int level) {
        return ImportedGlyphRegistrar.part(new ImportedGlyphKey(spell, level))
            .orElseThrow(() -> new IllegalStateException("no imported glyph for " + spell + " level " + level));
    }

    private static ItemStack glyph(ResourceLocation spell, int level, int count) {
        var item = ForgeRegistries.ITEMS.getValue(new ImportedGlyphKey(spell, level).glyphId());
        if (item == null) throw new IllegalStateException("no glyph item for " + spell + " level " + level);
        return new ItemStack(item, count);
    }

    private static List<String> parts(Spell spell) {
        List<String> ids = new ArrayList<>();
        for (AbstractSpellPart part : spell.recipe) ids.add(part.getRegistryName().toString());
        return ids;
    }

    private static FakePlayer knower(ServerLevel level) {
        return FakePlayerFactory.get(level, new GameProfile(KNOWER, "save01_knower"));
    }

    private static CompoundTag arsCaps(CompoundTag player) {
        CompoundTag caps = player.getCompound("ForgeCaps");
        CompoundTag ars = new CompoundTag();
        for (String key : caps.getAllKeys()) if (key.startsWith("ars_nouveau:")) ars.put(key, caps.get(key).copy());
        return ars;
    }

    private static java.util.Set<String> knownGlyphs(CompoundTag ars) {
        CompoundTag glyphs = ars.getCompound("ars_nouveau:player_data").getCompound("glyphs");
        java.util.Set<String> ids = new java.util.TreeSet<>();
        for (String key : glyphs.getAllKeys()) if (!"size".equals(key)) ids.add(glyphs.getString(key));
        return ids;
    }

    private static CompoundTag withoutGlyphs(CompoundTag ars) {
        CompoundTag copy = ars.copy();
        CompoundTag data = copy.getCompound("ars_nouveau:player_data");
        int size = data.getCompound("glyphs").getInt("size");
        data.remove("glyphs");
        data.putInt("glyph_count", size);
        return copy;
    }

    private static String describe(ResourceLocation spell, int level) {
        ImportedGlyphDefinition definition = ImportedGlyphCatalog.get().byKey(new ImportedGlyphKey(spell, level)).orElseThrow();
        GlyphAvailability.Status status = GlyphAvailability.status(definition);
        return definition.key() + " status=" + definition.status().id() + " adapter_v" + definition.adapterVersion() + " availability=" + status.reason();
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
