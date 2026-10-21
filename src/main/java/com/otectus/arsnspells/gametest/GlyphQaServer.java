package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.registry.SpellCasterRegistry;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectBreak;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHarm;
import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import com.hollingsworth.arsnouveau.setup.registry.BlockRegistry;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import com.otectus.arsnspells.block.GlyphPressBlockEntity;
import com.otectus.arsnspells.bridge.NativeManaAccess;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistration;
import com.otectus.arsnspells.glyph.IronGlyphBridges;
import com.otectus.arsnspells.glyph.runtime.GlyphOutcome;
import com.otectus.arsnspells.glyph.runtime.GlyphSessions;
import com.otectus.arsnspells.registry.ModBlocksRegistry;
import com.otectus.arsnspells.registry.ModItemsRegistry;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Disposable dedicated-server QA for 3.4.0, inert without its Gradle profile. Phase 1
 * ({@code -PwithGlyphQaServer}): when a client running {@code GlyphQaClient} joins, the server
 * builds a small scene in a flat world and checks, from real world state, what the client does
 * with real input events: Touch from a Spell Loom export at a block and at a living target, the
 * Glyph Press menu flow, learning the pressed glyph, and casting it from an Ars spell book. It then
 * starts a second Press job and stops the server mid-job. Phase 2 ({@code -PglyphQaPhase=2})
 * restarts the same world with nobody online and checks that the saved job resumes, waits at its
 * end while Iron's spell config is unbuilt, and completes exactly once when a client
 * ({@code -PglyphQaClientPhase=2}) logs in. Phase 4 ({@code -PglyphQaPhase=4}) puts two clients
 * at one Press. Every verdict is a {@code ANS_GLYPH_QA} log line; any failure throws.
 */
@EventBusSubscriber(modid = "ars_n_spells")
public final class GlyphQaServer {
    private static final Logger LOGGER = LoggerFactory.getLogger("ArsNSpells/GlyphQa");
    public static final BlockPos STAND = new BlockPos(0, -60, 0);
    public static final BlockPos DIRT = new BlockPos(0, -59, 2);
    public static final BlockPos PRESS = new BlockPos(-2, -60, 0);
    public static final BlockPos JAR = new BlockPos(-3, -60, 1);
    public static final BlockPos RESTART_PRESS = new BlockPos(-2, -60, -3);
    public static final BlockPos RESTART_JAR = new BlockPos(-3, -60, -3);
    private static final ResourceLocation FIREBOLT = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "firebolt");

    private static int age = -1;
    private static int stage;
    private static Pig pig;
    private static float pigHealth;
    private static int restartTicks, heldTicks, joinedTicks;
    /** Journey two (stages 6 to 10) ends here; the restart job follows once the client has left. */
    private static final int JOURNEY_DONE = 11;
    private static Pig target;
    private static boolean refusalSeen, tierThreeSeen;
    private static int pressedPlates = -1, pressedGems, pressedSource;
    /** Phase 4: two real clients, QaA and QaB, at one Press. */
    private static ServerPlayer duoA, duoB;
    private static int duoTicks;
    private static final java.util.Set<java.util.UUID> duoJobs = new java.util.HashSet<>();
    /** Phase 1 ends after the client has left, so stopping the server never cuts the client's own checks short. */
    private static ServerPlayer qaPlayer;
    private static boolean clientLeft;

    private GlyphQaServer() {
    }

    private static boolean enabled() {
        return Boolean.getBoolean("ans.glyphQa.server");
    }

    private static int phase() {
        return Integer.getInteger("ans.glyphQa.phase", 1);
    }

    @SubscribeEvent
    public static void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (enabled() && phase() == 4 && event.getEntity() instanceof ServerPlayer player) {
            String name = player.getGameProfile().getName();
            if (name.equals("QaA")) duoA = player;
            if (name.equals("QaB")) duoB = player;
            return;
        }
        if (enabled() && event.getEntity() instanceof ServerPlayer player && qaPlayer == null) {
            if (phase() == 1) age = 0;
            qaPlayer = player;
        }
    }

    @SubscribeEvent
    public static void left(PlayerEvent.PlayerLoggedOutEvent event) {
        if (enabled() && phase() == 1 && event.getEntity() == qaPlayer) clientLeft = true;
    }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!enabled() || phase() != 1 || age < 0 || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (++age > 6000) throw new IllegalStateException("Glyph QA server timed out at stage " + stage);
        if (age < 60) return;
        ServerLevel level = player.serverLevel();
        switch (stage) {
            case 0 -> {
                prepare(player, level);
                stage = 1;
            }
            case 1 -> {
                if (level.getBlockState(DIRT).isAir()) {
                    verdict("touch_block", "an exported Touch -> Break scroll broke the dirt it was aimed at");
                    LOGGER.info("ANS_GLYPH_QA pig alive={} removed={} pos={} tracked={} player={}", pig.isAlive(), pig.isRemoved(),
                        pig.position(), level.getEntity(pig.getUUID()) != null, player.position());
                    stage = 2;
                }
            }
            case 2 -> {
                if (pig.getHealth() < pigHealth) {
                    verdict("touch_entity", "an exported Touch -> Harm scroll hurt the pig it was aimed at: " + pigHealth + " -> " + pig.getHealth());
                    pigHealth = pig.getHealth();
                    stage = 3;
                }
            }
            case 3 -> {
                var effect = ImportedGlyphRegistration.effect(new ImportedGlyphKey(FIREBOLT, 1));
                boolean pressed = level.getBlockEntity(PRESS) instanceof GlyphPressBlockEntity press && press.job() == null
                    && press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_SCROLL).isEmpty();
                boolean taken = player.getInventory().contains(new ItemStack(effect.getGlyph()));
                if (pressed && taken) {
                    verdict("press", "the Press consumed the scroll and the player took the Glyph of Firebolt I");
                    stage = 4;
                }
            }
            case 4 -> {
                var effect = ImportedGlyphRegistration.effect(new ImportedGlyphKey(FIREBOLT, 1));
                if (CapabilityRegistry.getPlayerDataCap(player).knowsGlyph(effect)) {
                    verdict("learn", "using the glyph learned it");
                    ItemStack book = new ItemStack(ItemsRegistry.NOVICE_SPELLBOOK.get());
                    SpellCasterRegistry.from(book).setSpell(new Spell(MethodTouch.INSTANCE, effect)).saveToStack(book);
                    player.getInventory().setItem(3, book);
                    GlyphSessions.clearOutcomes(player.getUUID());
                    stage = 5;
                }
            }
            case 5 -> {
                boolean applied = GlyphSessions.outcomes(player.getUUID()).stream()
                    .anyMatch(o -> o.spell().equals(FIREBOLT) && o.outcome() == GlyphOutcome.EFFECT_APPLIED);
                if (applied && (pig.getHealth() < pigHealth || pig.isOnFire())) {
                    verdict("glyph_cast", "Touch -> the learned Glyph of Firebolt I from an Ars spell book struck the pig: "
                        + pigHealth + " -> " + pig.getHealth());
                    stage = 6;
                }
            }
            case 6 -> {
                prepareJourneyTwo(player, level);
                stage = 7;
            }
            case 7 -> {
                GlyphPressBlockEntity press = (GlyphPressBlockEntity) level.getBlockEntity(PRESS);
                var terms = com.otectus.arsnspells.glyph.behavior.GlyphBehavior.current().variant(new ImportedGlyphKey(FIREBOLT, 10));
                if (pressedPlates < 0 && press.job() != null) {
                    pressedPlates = press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_PLATE).getCount();
                    pressedGems = press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_CATALYST).getCount();
                    pressedSource = sourceAround(level, press);
                }
                ItemStack scroll = press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_SCROLL);
                if (!refusalSeen && !scroll.isEmpty()
                        && IronGlyphBridges.get().readScroll(scroll).kind() == com.otectus.arsnspells.glyph.recipe.ScrollReading.Kind.EXPORTED_ARS_SPELL) {
                    var reason = press.plan(player).reason();
                    if (reason != com.otectus.arsnspells.glyph.recipe.GlyphPressPlan.Reason.EXPORTED_ARS_SPELL) {
                        throw new IllegalStateException("An exported scroll was planned as " + reason);
                    }
                    refusalSeen = true;
                    verdict("press_refusal", "the Press refuses a Spell Loom export with \""
                        + net.minecraft.network.chat.Component.translatable(reason.translationKey()).getString() + "\"; nothing is consumed");
                }
                var tierThree = ImportedGlyphRegistration.effect(new ImportedGlyphKey(FIREBOLT, 10));
                if (!tierThreeSeen && pressedPlates >= 0 && press.job() == null && player.getInventory().contains(new ItemStack(tierThree.getGlyph()))) {
                    int plates = press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_PLATE).getCount();
                    int gems = press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_CATALYST).getCount();
                    int source = sourceAround(level, press);
                    if (terms.tier() != 3 || plates != pressedPlates - 1 || gems != pressedGems - terms.pressGems()
                            || source != pressedSource - terms.pressSource()) {
                        throw new IllegalStateException("The tier III job used plates " + pressedPlates + "->" + plates + ", gems " + pressedGems
                            + "->" + gems + ", Source " + pressedSource + "->" + source + " for terms " + terms);
                    }
                    tierThreeSeen = true;
                    verdict("press_tier3", "a Firebolt X scroll (tier III) used exactly one plate, " + terms.pressGems() + " Source Gems and "
                        + terms.pressSource() + " Source (jar and buffer " + pressedSource + " -> " + source + ") and made one Glyph of Firebolt X");
                }
                if (tierThreeSeen && refusalSeen && CapabilityRegistry.getPlayerDataCap(player).knowsGlyph(tierThree)) {
                    verdict("learn_tier3", "using the Glyph of Firebolt X learned it");
                    stage = 8;
                }
            }
            case 8 -> {
                var recipe = SpellCasterRegistry.from(player.getInventory().getItem(4)).getSpell().unsafeList();
                if (recipe.equals(composed())) {
                    verdict("editor_compose", "the Ars spell book editor saved Touch -> Glyph of Firebolt I -> Amplify to the novice book");
                    GlyphSessions.clearOutcomes(player.getUUID());
                    pigHealth = target.getHealth();
                    stage = 9;
                }
            }
            case 9 -> {
                var applied = applied(player);
                if (applied != null && target.getHealth() < pigHealth) {
                    if (applied.level() != 2) throw new IllegalStateException("Amplify did not raise Firebolt I to level 2: " + applied);
                    verdict("augmented_cast", "the composed Touch -> Glyph of Firebolt I -> Amplify ran Firebolt at effective level "
                        + applied.level() + " on the pig: " + pigHealth + " -> " + target.getHealth());
                    GlyphSessions.clearOutcomes(player.getUUID());
                    pigHealth = target.getHealth();
                    stage = 10;
                }
            }
            case 10 -> {
                var applied = applied(player);
                if (applied != null && target.getHealth() < pigHealth) {
                    verdict("nested_cast", "a Spell Loom export (Iron's scroll) of Touch -> Glyph of Firebolt I ran the imported glyph at level "
                        + applied.level() + " on the pig: " + pigHealth + " -> " + target.getHealth());
                    stage = JOURNEY_DONE;
                }
            }
            default -> {
            }
        }
    }

    /** Phase 1 after the client left: start the restart job, then stop the server mid-job. */
    private static void finishPhaseOne(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (stage == JOURNEY_DONE && clientLeft) {
            startRestartJob(qaPlayer, level);
            stage = JOURNEY_DONE + 1;
        } else if (stage == JOURNEY_DONE + 1 && level.getBlockEntity(RESTART_PRESS) instanceof GlyphPressBlockEntity press && press.progress() >= 30) {
            LOGGER.info("ANS_GLYPH_QA stage=restart_saved operation={} progress={} of {}", press.job().operation(),
                press.progress(), press.job().ticks());
            LOGGER.info("ANS_GLYPH_QA_SERVER_PHASE1_PASS");
            stage = JOURNEY_DONE + 2;
            server.halt(false);
        }
    }

    /**
     * Phase 2: the restarted server must find the saved job and resume it. With nobody online
     * Iron's spell config is not built yet, so the finished job must wait, neither cancelled nor
     * completed; when the first player logs in (the runner starts the client on
     * {@code restart_waiting}) it must complete exactly once.
     */
    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        if (!enabled()) return;
        if (phase() == 1) {
            if (stage >= JOURNEY_DONE) finishPhaseOne(event.getServer());
            return;
        }
        if (phase() == 4) {
            duoTick(event.getServer());
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        if (++restartTicks > 6000) throw new IllegalStateException("Glyph QA restart phase timed out at stage " + stage);
        if (restartTicks == 1) {
            // Nobody is online after the restart: keep the Press's chunk ticking, as /forceload or a chunk loader would.
            level.setChunkForced(RESTART_PRESS.getX() >> 4, RESTART_PRESS.getZ() >> 4, true);
        }
        if (restartTicks < 20 || !level.isLoaded(RESTART_PRESS)) return;
        if (!(level.getBlockEntity(RESTART_PRESS) instanceof GlyphPressBlockEntity press)) {
            throw new IllegalStateException("The restarted world has no Press at " + RESTART_PRESS);
        }
        ItemStack output = press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT);
        int players = server.getPlayerList().getPlayerCount();
        boolean pending = com.otectus.arsnspells.glyph.behavior.GlyphBehavior.current().ironsConfigPending();
        if (restartTicks % 20 == 0) {
            LOGGER.info("ANS_GLYPH_QA restart_watch tick={} job={} phase={} progress={} output={} lastEnd={} players={} pending={}",
                restartTicks, press.job() != null, press.phase(), press.progress(), output, press.lastEnd(), players, pending);
        }
        switch (stage) {
            case 0 -> {
                if (press.job() == null || press.progress() < 30) {
                    throw new IllegalStateException("The saved job did not survive the restart: job=" + press.job()
                        + " progress=" + press.progress() + " lastEnd=" + press.lastEnd());
                }
                if (players != 0 || !pending) {
                    throw new IllegalStateException("Expected nobody online and Iron's config pending: players=" + players + " pending=" + pending);
                }
                LOGGER.info("ANS_GLYPH_QA stage=restart_loaded operation={} progress={} of {} reserved={} pending={}",
                    press.job().operation(), press.progress(), press.job().ticks(), press.reservedSource(), pending);
                stage = 1;
            }
            case 1 -> {
                if (press.job() == null) {
                    throw new IllegalStateException("The job ended before anyone logged in: lastEnd=" + press.lastEnd() + " output=" + output);
                }
                if (press.progress() >= press.job().ticks() && ++heldTicks >= 60) {
                    if (!output.isEmpty() || players != 0 || !pending) {
                        throw new IllegalStateException("Expected a waiting job: output=" + output + " players=" + players + " pending=" + pending);
                    }
                    LOGGER.info("ANS_GLYPH_QA stage=restart_waiting progress={} of {} held_ticks={} reserved={} pending=true players=0",
                        press.progress(), press.job().ticks(), heldTicks, press.reservedSource());
                    stage = 2;
                }
            }
            case 2 -> {
                if (press.job() != null) {
                    if (players > 0 && !pending && ++joinedTicks > 100) {
                        throw new IllegalStateException("A player is online but the job still waits: progress=" + press.progress());
                    }
                    return;
                }
                var expected = ImportedGlyphRegistration.effect(new ImportedGlyphKey(FIREBOLT, 2)).getGlyph();
                if (!output.is(expected) || output.getCount() != 1 || !press.lastEnd().isEmpty()) {
                    throw new IllegalStateException("The resumed job produced " + output + " lastEnd=" + press.lastEnd());
                }
                LOGGER.info("ANS_GLYPH_QA stage=restart_completed output={} count={} players={} pending={} reserved={}",
                    output.getItem(), output.getCount(), players, pending, press.reservedSource());
                stage = 3;
            }
            case 3 -> {
                // What the player learned and composed before the restart is still there, and still casts.
                ServerPlayer player = qaPlayer;
                var cap = CapabilityRegistry.getPlayerDataCap(player);
                var caster = SpellCasterRegistry.from(player.getInventory().getItem(4));
                if (!cap.knowsGlyph(ImportedGlyphRegistration.effect(new ImportedGlyphKey(FIREBOLT, 1)))
                        || !cap.knowsGlyph(ImportedGlyphRegistration.effect(new ImportedGlyphKey(FIREBOLT, 10)))
                        || !caster.getSpell().unsafeList().equals(composed())) {
                    throw new IllegalStateException("After the restart: knows I=" + cap.knowsGlyph(ImportedGlyphRegistration.effect(new ImportedGlyphKey(FIREBOLT, 1)))
                        + " X=" + cap.knowsGlyph(ImportedGlyphRegistration.effect(new ImportedGlyphKey(FIREBOLT, 10))) + " book=" + caster.getSpell().unsafeList());
                }
                verdict("restart_knowledge", "after the restart the player still knows Glyph of Firebolt I and X, and the book's selected spell (slot "
                    + caster.getCurrentSlot() + ") is still Touch -> Glyph of Firebolt I -> Amplify");
                target = level.getEntitiesOfClass(Pig.class, new net.minecraft.world.phys.AABB(STAND).inflate(8), Pig::isAlive).stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("The QA pig did not survive the restart"));
                pigHealth = target.getHealth();
                GlyphSessions.clearOutcomes(player.getUUID());
                stage = 4;
            }
            case 4 -> {
                var applied = applied(qaPlayer);
                if (applied != null && target.getHealth() < pigHealth) {
                    verdict("restart_cast", "after the restart the saved book ran Firebolt at effective level " + applied.level()
                        + " on the pig: " + pigHealth + " -> " + target.getHealth());
                    LOGGER.info("ANS_GLYPH_QA_RESTART_PASS");
                    stage = 5;
                }
            }
            case 5 -> {
                // Stop once the client has left, so the client never sees the server vanish under it.
                if (players == 0 || ++joinedTicks > 600) {
                    stage = 4;
                    server.halt(false);
                }
            }
            default -> {
            }
        }
    }

    /**
     * Phase 4: QaA fills the Press and clicks Inscribe twice in one tick while QaB has the same
     * Press open; QaB tries to take the locked scroll and then takes the result. The server checks
     * one job, inputs that never leave during it, exact consumption and one glyph in total.
     */
    private static void duoTick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (++duoTicks > 12000) throw new IllegalStateException("Glyph QA two-client phase timed out at stage " + stage);
        var glyph = ImportedGlyphRegistration.effect(new ImportedGlyphKey(FIREBOLT, 1)).getGlyph();
        switch (stage) {
            case 0 -> {
                if (duoA == null || duoB == null || ++age < 60) return;
                for (int x = -6; x <= 6; x++) {
                    for (int z = -6; z <= 6; z++) {
                        level.setBlockAndUpdate(new BlockPos(x, STAND.getY() - 1, z), Blocks.SMOOTH_STONE.defaultBlockState());
                    }
                }
                for (BlockPos pos : new BlockPos[]{PRESS, JAR}) level.removeBlock(pos, false);
                level.setBlockAndUpdate(PRESS, ModBlocksRegistry.GLYPH_PRESS.get().defaultBlockState());
                level.setBlockAndUpdate(JAR, BlockRegistry.SOURCE_JAR.get().defaultBlockState());
                if (level.getBlockEntity(JAR) instanceof com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile jar) jar.setSource(3000);
                var terms = com.otectus.arsnspells.glyph.behavior.GlyphBehavior.current().variant(new ImportedGlyphKey(FIREBOLT, 1));
                for (ServerPlayer player : new ServerPlayer[]{duoA, duoB}) {
                    player.setGameMode(GameType.SURVIVAL);
                    player.getInventory().clearContent();
                }
                duoA.teleportTo(level, 0.5, STAND.getY(), 0.5, 90, 0);
                duoB.teleportTo(level, 0.5, STAND.getY(), -1.5, 90, 0);
                duoA.getInventory().setItem(9, IronGlyphBridges.get().scrollStack(FIREBOLT, 1));
                duoA.getInventory().setItem(10, new ItemStack(ModItemsRegistry.blankGlyphPlate().get()));
                duoA.getInventory().setItem(11, new ItemStack(ItemsRegistry.SOURCE_GEM.get(), terms.pressGems()));
                duoA.inventoryMenu.broadcastChanges();
                LOGGER.info("ANS_GLYPH_QA stage=duo_prepared a={} b={} terms={}", duoA.getGameProfile().getName(),
                    duoB.getGameProfile().getName(), terms);
                stage = 1;
            }
            case 1 -> {
                GlyphPressBlockEntity press = (GlyphPressBlockEntity) level.getBlockEntity(PRESS);
                boolean bothOpen = duoA.containerMenu instanceof com.otectus.arsnspells.menu.GlyphPressMenu
                    && duoB.containerMenu instanceof com.otectus.arsnspells.menu.GlyphPressMenu;
                if (bothOpen && press.job() == null && !press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_SCROLL).isEmpty()
                        && duoA.getInventory().getItem(20).isEmpty()) {
                    // Tell A that B is watching: a marker in A's inventory, synced through A's open menu.
                    duoA.getInventory().setItem(20, new ItemStack(net.minecraft.world.item.Items.STICK));
                    duoA.containerMenu.broadcastChanges();
                    LOGGER.info("ANS_GLYPH_QA stage=duo_both_viewing");
                }
                if (press.job() != null) {
                    duoJobs.add(press.job().operation());
                    if (duoJobs.size() > 1) throw new IllegalStateException("Two jobs ran for one Press: " + duoJobs);
                    if (press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_SCROLL).isEmpty()) {
                        throw new IllegalStateException("The scroll left the Press while its job ran");
                    }
                    for (ServerPlayer player : new ServerPlayer[]{duoA, duoB}) {
                        if (!player.containerMenu.getCarried().isEmpty() || player.getInventory().contains(IronGlyphBridges.get().scrollStack(FIREBOLT, 1))) {
                            throw new IllegalStateException(player.getGameProfile().getName() + " holds the locked scroll");
                        }
                    }
                    return;
                }
                if (duoJobs.size() == 1) {
                    var items = press.getItems();
                    ItemStack output = items.getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT);
                    if (!items.getStackInSlot(GlyphPressBlockEntity.SLOT_SCROLL).isEmpty() || !items.getStackInSlot(GlyphPressBlockEntity.SLOT_PLATE).isEmpty()
                            || !items.getStackInSlot(GlyphPressBlockEntity.SLOT_CATALYST).isEmpty() || !output.is(glyph) || output.getCount() != 1) {
                        throw new IllegalStateException("After the job: scroll=" + items.getStackInSlot(GlyphPressBlockEntity.SLOT_SCROLL)
                            + " plate=" + items.getStackInSlot(GlyphPressBlockEntity.SLOT_PLATE) + " catalyst="
                            + items.getStackInSlot(GlyphPressBlockEntity.SLOT_CATALYST) + " output=" + output);
                    }
                    verdict("duo_job", "A's two clicks started exactly one job (" + duoJobs.iterator().next()
                        + "); its inputs never left the Press while B tried to take them; it used exactly the scroll, one plate and the catalyst and made one glyph");
                    stage = 2;
                }
            }
            case 2 -> {
                GlyphPressBlockEntity press = (GlyphPressBlockEntity) level.getBlockEntity(PRESS);
                int a = count(duoA, glyph), b = count(duoB, glyph);
                if (press.getItems().getStackInSlot(GlyphPressBlockEntity.SLOT_OUTPUT).isEmpty() && a + b == 1) {
                    verdict("duo_output", "the single glyph went to " + (b == 1 ? "QaB" : "QaA") + " (A " + a + ", B " + b + "); nothing was duplicated");
                    stage = 3;
                }
            }
            case 3 -> {
                if (server.getPlayerList().getPlayerCount() == 0) {
                    LOGGER.info("ANS_GLYPH_QA_DUO_PASS");
                    stage = 4;
                    server.halt(false);
                }
            }
            default -> {
            }
        }
    }

    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        int total = player.containerMenu.getCarried().is(item) ? player.containerMenu.getCarried().getCount() : 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    /** Journey two: a tier III scroll and catalysts, an empty novice book, Amplify, a nested export and a sturdier pig. */
    private static void prepareJourneyTwo(ServerPlayer player, ServerLevel level) {
        var terms = com.otectus.arsnspells.glyph.behavior.GlyphBehavior.current().variant(new ImportedGlyphKey(FIREBOLT, 10));
        if (level.getBlockEntity(JAR) instanceof com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile jar) jar.setSource(10000);
        player.getInventory().setItem(9, IronGlyphBridges.get().scrollStack(FIREBOLT, 10));
        player.getInventory().setItem(10, new ItemStack(ModItemsRegistry.blankGlyphPlate().get()));
        player.getInventory().setItem(11, new ItemStack(ItemsRegistry.SOURCE_GEM.get(), terms.pressGems()));
        player.getInventory().setItem(4, new ItemStack(ItemsRegistry.NOVICE_SPELLBOOK.get()));
        // The creative book's editor lists every spell part, imported glyphs included (editor responsiveness).
        player.getInventory().setItem(5, new ItemStack(ItemsRegistry.CREATIVE_SPELLBOOK.get()));
        player.getInventory().setItem(2, ArsSpellExportUtil.createIronsScrollCarrier(new Spell(MethodTouch.INSTANCE,
            ImportedGlyphRegistration.effect(new ImportedGlyphKey(FIREBOLT, 1)))));
        player.inventoryMenu.broadcastChanges();
        var cap = CapabilityRegistry.getPlayerDataCap(player);
        cap.unlockGlyph(com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify.INSTANCE);
        CapabilityRegistry.EventHandler.syncPlayerCap(player);
        target = EntityType.PIG.create(level);
        target.moveTo(0.5, -60, 3.5, 180, 0);
        target.setNoAi(true);
        target.setPersistenceRequired();
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100.0);
        target.setHealth(100.0f);
        level.addFreshEntity(target);
        GlyphSessions.clearOutcomes(player.getUUID());
        LOGGER.info("ANS_GLYPH_QA stage=journey_two_prepared terms={} jarSource=10000", terms);
    }

    private static java.util.List<com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart> composed() {
        return java.util.List.of(MethodTouch.INSTANCE, ImportedGlyphRegistration.effect(new ImportedGlyphKey(FIREBOLT, 1)),
            com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify.INSTANCE);
    }

    @org.jetbrains.annotations.Nullable
    private static GlyphSessions.OutcomeRecord applied(ServerPlayer player) {
        return GlyphSessions.outcomes(player.getUUID()).stream()
            .filter(o -> o.spell().equals(FIREBOLT) && o.outcome() == GlyphOutcome.EFFECT_APPLIED).findFirst().orElse(null);
    }

    /** Source in the Press (free and reserved) plus its jar: what one job may draw from. */
    private static int sourceAround(ServerLevel level, GlyphPressBlockEntity press) {
        int jar = level.getBlockEntity(JAR) instanceof com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile tile ? tile.getSource() : 0;
        return jar + press.freeSource() + press.reservedSource();
    }

    private static void verdict(String stage, String detail) {
        LOGGER.info("ANS_GLYPH_QA stage={} result=PASS detail={}", stage, detail);
    }

    private static void prepare(ServerPlayer player, ServerLevel level) {
        if (!IronGlyphBridges.get().available()) throw new IllegalStateException("Glyph QA requires Iron's");
        player.setGameMode(GameType.SURVIVAL);
        // The scene brings its own floor: a QA world's generator settings are not trusted to make one.
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                level.setBlockAndUpdate(new BlockPos(x, STAND.getY() - 1, z), Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        player.teleportTo(level, STAND.getX() + 0.5, STAND.getY(), STAND.getZ() + 0.5, 0, 0);
        player.getInventory().clearContent();
        NativeManaAccess.with(player, ResourceUnit.ARS_MANA, () -> {
            var mana = CapabilityRegistry.getMana(player);
            mana.setMaxMana(5000);
            mana.setMana(5000);
            return null;
        });
        IronsProxyCastDriver.setMaxIronsMana(player, 5000);
        IronsProxyCastDriver.setIronsMana(player, 5000);
        level.setBlockAndUpdate(DIRT, Blocks.DIRT.defaultBlockState());
        for (BlockPos pos : new BlockPos[]{PRESS, JAR, RESTART_PRESS, RESTART_JAR}) level.removeBlock(pos, false);
        level.setBlockAndUpdate(PRESS, ModBlocksRegistry.GLYPH_PRESS.get().defaultBlockState());
        level.setBlockAndUpdate(JAR, BlockRegistry.SOURCE_JAR.get().defaultBlockState());
        if (level.getBlockEntity(JAR) instanceof com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile jar) jar.setSource(3000);
        pig = EntityType.PIG.create(level);
        pig.moveTo(0.5, -60, 3.5, 180, 0);
        pig.setNoAi(true);
        level.addFreshEntity(pig);
        pigHealth = pig.getHealth();
        player.getInventory().setItem(0, ArsSpellExportUtil.createIronsScrollCarrier(new Spell(MethodTouch.INSTANCE, EffectBreak.INSTANCE)));
        player.getInventory().setItem(1, ArsSpellExportUtil.createIronsScrollCarrier(new Spell(MethodTouch.INSTANCE, EffectHarm.INSTANCE)));
        player.getInventory().setItem(9, IronGlyphBridges.get().scrollStack(FIREBOLT, 1));
        player.getInventory().setItem(10, new ItemStack(ModItemsRegistry.blankGlyphPlate().get(), 2));
        player.getInventory().setItem(11, new ItemStack(ItemsRegistry.SOURCE_GEM.get(), 4));
        player.getInventory().selected = 0;
        player.inventoryMenu.broadcastChanges();
        LOGGER.info("ANS_GLYPH_QA stage=prepared player={} pig={} jarSource=3000", player.getGameProfile().getName(), pig.getUUID());
    }

    /** A second Press with a Firebolt II job running, so the server can stop mid-job. */
    private static void startRestartJob(ServerPlayer player, ServerLevel level) {
        level.setBlockAndUpdate(RESTART_PRESS, ModBlocksRegistry.GLYPH_PRESS.get().defaultBlockState());
        level.setBlockAndUpdate(RESTART_JAR, BlockRegistry.SOURCE_JAR.get().defaultBlockState());
        if (level.getBlockEntity(RESTART_JAR) instanceof com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile jar) jar.setSource(3000);
        GlyphPressBlockEntity press = (GlyphPressBlockEntity) level.getBlockEntity(RESTART_PRESS);
        press.getItems().setStackInSlot(GlyphPressBlockEntity.SLOT_SCROLL, IronGlyphBridges.get().scrollStack(FIREBOLT, 2));
        press.getItems().setStackInSlot(GlyphPressBlockEntity.SLOT_PLATE, new ItemStack(ModItemsRegistry.blankGlyphPlate().get()));
        press.getItems().setStackInSlot(GlyphPressBlockEntity.SLOT_CATALYST, new ItemStack(ItemsRegistry.SOURCE_GEM.get(), 4));
        var reason = press.inscribe(player);
        if (reason != com.otectus.arsnspells.glyph.recipe.GlyphPressPlan.Reason.OK) {
            throw new IllegalStateException("The restart job could not start: " + reason);
        }
        LOGGER.info("ANS_GLYPH_QA stage=restart_job_started operation={}", press.job().operation());
    }
}
