package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.block.tile.RitualBrazierTile;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.mojang.authlib.GameProfile;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.rituals.AnsRitual;
import com.otectus.arsnspells.rituals.ManaInfusionRitual;
import com.otectus.arsnspells.rituals.SpellTranscriptionRitual;
import com.otectus.arsnspells.rituals.SpellUninscriptionRitual;
import com.otectus.arsnspells.rituals.SpellbookBindingRitual;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Drives ANS rituals through their <em>real lifecycle</em> on a real Ritual Brazier: seat the
 * ritual, light it, let the brazier tick, and only then assert.
 *
 * <p><b>Why the whole lifecycle and not {@code onEnd()} directly.</b> From 3.0.0 through 3.2.2
 * every one-shot ANS ritual shipped with an empty {@code tick()}. Ars only calls
 * {@code AbstractRitual.onEnd()} once {@code RitualContext.isDone} flips, and the only thing that
 * flips it is a ritual calling {@code setFinished()} from its own {@code tick()} -- so the brazier
 * lit, burned forever, and the payload never ran. Nothing caught it: the existing end-to-end tests
 * in {@link CrossCastGameTests} drive the binding utilities directly, and a test that called
 * {@code onEnd()} directly would have passed against the broken build. Hence
 * {@link #everyOneShotRitual_actuallyFinishes}, which is the cheap general guard, plus the binding
 * tests below, which are the specific one.
 *
 * <p><b>Two traps for anyone extending this class.</b> First, {@code AbstractRitual.getWorld()} and
 * {@code getPos()} both dereference the ritual's {@code tile} field, and every ANS {@code onEnd()}
 * early-returns when either is null -- so a ritual that was never seated via
 * {@code RitualBrazierTile.setRitual} makes each assertion below pass while binding nothing. Always
 * go through {@link #lightAnsBrazier}. Second, never assert on chat: {@code FakePlayer}s are not in
 * the level's entity list, so both the initiator lookup and the proximity fallback in
 * {@code RitualFeedback} return null here and no message is ever delivered.
 *
 * <p>These run in their own {@code ans_rituals} batch on purpose. The rituals scan a 7x7x7 box that
 * overhangs the 5x5 platform, so a neighbouring test's dropped items would drift into range and trip
 * the strict "unexpected items" guard -- a flake that is very hard to read after the fact.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class RitualLifecycleGameTests {

    /** Long enough for the ritual to finish, short enough to stay inside {@code timeoutTicks}. */
    private static final int RITUAL_SETTLE_TICKS = AnsRitual.DEFAULT_DURATION_TICKS + 20;

    private static final BlockPos BRAZIER = new BlockPos(2, 1, 2);

    private static final GameProfile FAKE_PROFILE =
        new GameProfile(UUID.fromString("0a000000-0000-0000-0000-00000000a11c"), "ans_ritual_gametest");

    private RitualLifecycleGameTests() {
    }

    /**
     * Place a real Ritual Brazier and seat an ANS ritual on it exactly as the tablet does.
     * {@code setRitual} is the same call {@code RitualTablet.useOn} makes, and it is what assigns
     * {@code ritual.tile}, without which the ritual cannot see the world.
     */
    private static RitualBrazierTile lightAnsBrazier(GameTestHelper helper, BlockPos local, String path) {
        Block brazier = BuiltInRegistries.BLOCK.get(
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "ritual_brazier"));
        if (brazier == null) {
            helper.fail("ars_nouveau:ritual_brazier is not registered");
        }
        helper.setBlock(local, brazier.defaultBlockState());
        if (!(helper.getBlockEntity(local) instanceof RitualBrazierTile tile)) {
            helper.fail("placing ars_nouveau:ritual_brazier produced no RitualBrazierTile at " + local);
            return null;
        }
        tile.setRitual(ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, path));
        if (tile.ritual == null) {
            helper.fail(path + " is not registered in Ars's RitualRegistry, so no brazier can run it");
        }
        return tile;
    }

    /**
     * The player who lights every brazier here. Creative and empty-handed so a ritual can only be
     * refused for the reason under test.
     */
    private static ServerPlayer emptyHandedPlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ans_ritual"));
        player.moveTo(helper.absoluteVec(new Vec3(1.0, 2.0, 1.0)));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.setGameMode(GameType.CREATIVE);
        return player;
    }

    /** Find any registered Iron's spellbook item (tier-agnostic), or null if none. */
    private static Item findIronsSpellBook() {
        for (Item item : BuiltInRegistries.ITEM) {
            if (IronsBookBindingUtil.isIronsSpellBook(new ItemStack(item))) {
                return item;
            }
        }
        return null;
    }

    /** Drop a stack with zero motion and no pickup, so the ritual's 3-block scan is deterministic. */
    private static ItemEntity dropNear(GameTestHelper helper, Vec3 local, ItemStack stack) {
        Vec3 abs = helper.absoluteVec(local);
        ItemEntity entity = new ItemEntity(helper.getLevel(), abs.x, abs.y, abs.z, stack, 0.0, 0.0, 0.0);
        entity.setNeverPickUp();
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static ItemStack carrierScrollStack(GameTestHelper helper, int count) {
        Spell heal = new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE);
        ItemStack scroll = ArsSpellExportUtil.createIronsScrollCarrier(heal);
        if (scroll.isEmpty()) {
            helper.fail("exporting an Ars spell must yield a carrier scroll when Iron's is loaded");
        }
        scroll.setCount(count);
        return scroll;
    }

    private static ItemStack ironsBookStack(GameTestHelper helper) {
        Item bookItem = findIronsSpellBook();
        if (bookItem == null) {
            helper.fail("no irons_spellbooks spellbook item is registered despite Iron's being loaded");
        }
        return new ItemStack(bookItem);
    }

    private static int arsEntryCount(ItemEntity entity) {
        return CrossModSpellComponents.countArsEntries(
            CrossModSpellComponents.get(entity.getItem()));
    }

    private static void assertRitualFinished(GameTestHelper helper, RitualBrazierTile tile, String what) {
        if (tile.ritual != null) {
            helper.fail(what + " never finished. Ars runs onEnd() only once a ritual calls "
                + "setFinished() from its own tick(); an empty tick() burns forever.");
        }
    }

    // ------------------------------------------------------------------
    // The general guard: every one-shot ritual must reach onEnd() at all.
    // ------------------------------------------------------------------

    /**
     * Light each one-shot ritual on its own brazier with nothing in range and confirm it ends.
     * No inputs are needed -- the ritual reports an empty range and stops -- so this asserts only
     * the property that was broken: that the ritual completes rather than burning forever.
     */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "ans_rituals")
    public static void everyOneShotRitual_actuallyFinishes(GameTestHelper helper) {
        List<String> paths = new ArrayList<>();
        paths.add(SpellUninscriptionRitual.REGISTRY_PATH);
        if (IronsCompat.isLoaded()) {
            paths.add(SpellTranscriptionRitual.REGISTRY_PATH);
            paths.add(SpellbookBindingRitual.REGISTRY_PATH);
            paths.add(ManaInfusionRitual.REGISTRY_PATH);
        }
        // One brazier per ritual, spread across the platform. Their scan boxes overlap, which is
        // harmless here precisely because no items are dropped.
        BlockPos[] spots = {
            new BlockPos(1, 1, 1), new BlockPos(3, 1, 1),
            new BlockPos(1, 1, 3), new BlockPos(3, 1, 3),
        };
        List<RitualBrazierTile> tiles = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            RitualBrazierTile tile = lightAnsBrazier(helper, spots[i], paths.get(i));
            tile.startRitual(emptyHandedPlayer(helper));
            tiles.add(tile);
        }
        helper.startSequence()
            .thenExecuteAfter(RITUAL_SETTLE_TICKS, () -> {
                for (int i = 0; i < tiles.size(); i++) {
                    assertRitualFinished(helper, tiles.get(i), paths.get(i));
                }
            })
            .thenSucceed();
    }

    // ------------------------------------------------------------------
    // Spellbook Binding, end to end through the brazier.
    // ------------------------------------------------------------------

    /** The happy path: dropped carrier scroll + dropped Iron's book, bound by the ritual itself. */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "ans_rituals")
    public static void bindingRitual_bindsDroppedScrollOntoDroppedBook(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        // Count 2 proves the ritual consumes ONE scroll rather than the whole stack: Iron's scrolls
        // stack to 16, and discarding the entity destroyed the extras.
        ItemEntity scroll = dropNear(helper, new Vec3(2.5, 1.3, 1.5), carrierScrollStack(helper, 2));
        ItemEntity book = dropNear(helper, new Vec3(2.5, 1.3, 3.5), ironsBookStack(helper));
        RitualBrazierTile tile = lightAnsBrazier(helper, BRAZIER, SpellbookBindingRitual.REGISTRY_PATH);

        tile.startRitual(emptyHandedPlayer(helper));

        helper.startSequence()
            .thenExecuteAfter(RITUAL_SETTLE_TICKS, () -> {
                assertRitualFinished(helper, tile, "Spellbook Binding");
                if (arsEntryCount(book) != 1) {
                    helper.fail("the binding ritual must append the carrier's Ars entry to the book, "
                        + "found " + arsEntryCount(book) + " entries");
                }
                if (CrossModSpellComponents.findEntryByProxyPoolId(book.getItem(), 1).isEmpty()) {
                    helper.fail("the first ritual bind must land in native-wheel proxy pool 1");
                }
                if (!scroll.isAlive() || scroll.getItem().getCount() != 1) {
                    helper.fail("the ritual must consume exactly one scroll from the stack, got "
                        + (scroll.isAlive() ? String.valueOf(scroll.getItem().getCount()) : "discarded"));
                }
            })
            .thenSucceed();
    }

    /**
     * A single unrelated item in range aborts the whole ritual rather than risking a bind against
     * the wrong stack. The happy-path test above is the positive control that stops this one from
     * passing for the wrong reason.
     */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "ans_rituals")
    public static void bindingRitual_strayItemInRangeAbortsWithoutMutating(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ItemEntity scroll = dropNear(helper, new Vec3(2.5, 1.3, 1.5), carrierScrollStack(helper, 2));
        ItemEntity book = dropNear(helper, new Vec3(2.5, 1.3, 3.5), ironsBookStack(helper));
        dropNear(helper, new Vec3(1.5, 1.3, 2.5), new ItemStack(Items.TORCH));
        RitualBrazierTile tile = lightAnsBrazier(helper, BRAZIER, SpellbookBindingRitual.REGISTRY_PATH);

        tile.startRitual(emptyHandedPlayer(helper));

        helper.startSequence()
            .thenExecuteAfter(RITUAL_SETTLE_TICKS, () -> {
                // It still RUNS and ends -- it just refuses to bind.
                assertRitualFinished(helper, tile, "Spellbook Binding");
                if (arsEntryCount(book) != 0) {
                    helper.fail("a stray item in range must abort the bind, but the book gained "
                        + arsEntryCount(book) + " Ars entries");
                }
                if (!scroll.isAlive() || scroll.getItem().getCount() != 2) {
                    helper.fail("an aborted bind must not consume the carrier scroll");
                }
            })
            .thenSucceed();
    }

    /**
     * With {@code allow_ars_spells_in_irons_spellbooks=false} the ritual must refuse and leave both
     * items untouched.
     */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "ans_rituals_config")
    public static void bindingRitual_honoursTheConfigKillSwitch(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) {
            return;
        }
        ItemEntity scroll = dropNear(helper, new Vec3(2.5, 1.3, 1.5), carrierScrollStack(helper, 2));
        ItemEntity book = dropNear(helper, new Vec3(2.5, 1.3, 3.5), ironsBookStack(helper));
        RitualBrazierTile tile = lightAnsBrazier(helper, BRAZIER, SpellbookBindingRitual.REGISTRY_PATH);

        boolean previous = AnsConfig.ALLOW_ARS_SPELLS_IN_IRONS_SPELLBOOKS.get();
        AnsConfig.ALLOW_ARS_SPELLS_IN_IRONS_SPELLBOOKS.set(false);
        try {
            tile.startRitual(emptyHandedPlayer(helper));

        helper.startSequence()
            .thenExecuteAfter(RITUAL_SETTLE_TICKS, () -> {
                // Restored FIRST, before any assertion can bail out: the config spec is shared
                // across every test in the run, so leaving this false would silently break
                // unrelated tests later in the batch.
                AnsConfig.ALLOW_ARS_SPELLS_IN_IRONS_SPELLBOOKS.set(previous);
                assertRitualFinished(helper, tile, "Spellbook Binding");
                if (arsEntryCount(book) != 0) {
                    helper.fail("binding is disabled in config, but the book gained "
                        + arsEntryCount(book) + " Ars entries");
                }
                if (!scroll.isAlive() || scroll.getItem().getCount() != 2) {
                    helper.fail("a config-refused bind must not consume the carrier scroll");
                }
            })
            .thenSucceed();
        } catch (Throwable failure) {
            AnsConfig.ALLOW_ARS_SPELLS_IN_IRONS_SPELLBOOKS.set(previous);
            throw failure;
        }
    }
}
