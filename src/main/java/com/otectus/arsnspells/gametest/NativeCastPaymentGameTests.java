package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.event.SpellCastEvent;
import com.hollingsworth.arsnouveau.api.spell.*;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.spell.*;
import com.otectus.arsnspells.util.ManaUtil;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.gametest.*;
import java.util.function.Consumer;

/** Real native resolvers, real mana capabilities, and observable healing at the payment boundary. */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class NativeCastPaymentGameTests {
    private static final String[] MODES = {"disabled", "separate", "ars_primary", "iss_primary", "hybrid"};

    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void arsNative_successAndLateVetoHaveExactPoolDeltas(GameTestHelper helper) {
        withSettings(() -> {
            for (String mode : IronsCompat.isLoaded() ? MODES : new String[]{"disabled"}) {
                for (double rate : new double[]{0.01, 1, 3}) {
                    settings(mode, rate);
                    for (boolean cross : new boolean[]{false, true}) {
                        ServerPlayer player = player(helper, "ars_payment_" + mode + rate + cross);
                        Spell heal = new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE);
                        var resolver = new SpellResolver(SpellContext.fromEntity(heal, player, new ItemStack(Items.STICK)));
                        if (cross) CrossCastContext.begin(player, CrossSpellType.ARS_NOUVEAU, player.level().getGameTime());
                        try {
                            float ars = balance(player, ResourceUnit.ARS_MANA);
                            float irons = IronsCompat.isLoaded() ? balance(player, ResourceUnit.IRONS_MANA) : 0;
                            int first = resolver.getResolveCost();
                            for (int read = 0; read < 10; read++) helper.assertTrue(first == resolver.getResolveCost(), "fresh cost events must agree");
                            equal(helper, ars, balance(player, ResourceUnit.ARS_MANA), "Ars preview debit");
                            if (IronsCompat.isLoaded()) equal(helper, irons, balance(player, ResourceUnit.IRONS_MANA), "Iron preview debit");
                            helper.assertTrue(resolver.onCast(new ItemStack(Items.STICK), player.level()), "native Ars cast refused: " + mode);
                            helper.assertTrue(player.getHealth() > 10, "successful Ars cast did not heal");
                            equal(helper, ars - owed(mode, ResourceUnit.ARS_MANA, ResourceUnit.ARS_MANA, heal.getCost(), rate, cross),
                                balance(player, ResourceUnit.ARS_MANA), "Ars successful debit " + mode);
                            if (IronsCompat.isLoaded()) equal(helper, irons - owed(mode, ResourceUnit.ARS_MANA, ResourceUnit.IRONS_MANA, heal.getCost(), rate, cross),
                                balance(player, ResourceUnit.IRONS_MANA), "Iron successful debit " + mode);
                        } finally { CrossCastContext.clear(player); }
                    }
                }
            }
            settings(IronsCompat.isLoaded() ? "separate" : "disabled", 3);
            ServerPlayer player = player(helper, "ars_payment_late_cancel");
            Spell heal = new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE);
            SpellResolver resolver = new SpellResolver(SpellContext.fromEntity(heal, player, new ItemStack(Items.STICK)));
            Consumer<SpellCastEvent> veto = event -> { if (event.context == resolver.spellContext) event.setCanceled(true); };
            float ars = balance(player, ResourceUnit.ARS_MANA);
            float irons = IronsCompat.isLoaded() ? balance(player, ResourceUnit.IRONS_MANA) : 0;
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, SpellCastEvent.class, veto);
            CrossCastContext.begin(player, CrossSpellType.ARS_NOUVEAU, player.level().getGameTime());
            try {
                helper.assertTrue(!resolver.onCast(new ItemStack(Items.STICK), player.level()), "late veto must refuse native Ars cast");
                equal(helper, 10, player.getHealth(), "late veto must prevent healing");
                equal(helper, ars, balance(player, ResourceUnit.ARS_MANA), "late veto Ars debit");
                if (IronsCompat.isLoaded()) equal(helper, irons, balance(player, ResourceUnit.IRONS_MANA), "late veto Iron debit");
            } finally { NeoForge.EVENT_BUS.unregister(veto); CrossCastContext.clear(player); }
        });
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_nativeCastPaysBeforeHealingAndRefusesBalanceLoss(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withSettings(() -> Loaded.irons(helper));
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void publicArsMutationsMoveOnlyTheAuthoritativePool(GameTestHelper helper) {
        withSettings(() -> {
            for (String mode : IronsCompat.isLoaded() ? MODES : new String[]{"disabled"}) {
                settings(mode, 1);
                ServerPlayer player = player(helper, "public_api_" + mode);
                var cap = com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry.getMana(player);
                boolean ironsPays = mode.equals("iss_primary") || mode.equals("hybrid");
                ResourceUnit payer = ironsPays ? ResourceUnit.IRONS_MANA : ResourceUnit.ARS_MANA;
                ResourceUnit dormant = ironsPays ? ResourceUnit.ARS_MANA : ResourceUnit.IRONS_MANA;
                float dormantBefore = IronsCompat.isLoaded() ? balance(player, dormant) : 0;
                cap.setMana(500);
                equal(helper, 500, balance(player, payer), "public set must apply once " + mode);
                cap.addMana(125);
                equal(helper, 625, balance(player, payer), "public add must apply once " + mode);
                cap.removeMana(40);
                equal(helper, 585, balance(player, payer), "public remove must apply once " + mode);
                if (IronsCompat.isLoaded()) equal(helper, dormantBefore, balance(player, dormant), "public operations must preserve dormant native balance " + mode);
                if (ironsPays) {
                    com.otectus.arsnspells.bridge.ArsRegenTickScope.enter(player.getUUID());
                    try { cap.addMana(100); }
                    finally { com.otectus.arsnspells.bridge.ArsRegenTickScope.exit(); }
                    equal(helper, 585, balance(player, payer), "only the native regen tick is suppressed");
                    cap.addMana(100);
                    equal(helper, 685, balance(player, payer), "non-regen grant still applies after scope");
                }
                if (IronsCompat.isLoaded()) {
                    BridgeManager.getNativeArsBridge().setMana(player, 123);
                    BridgeManager.getNativeIronsBridge().setMana(player, 456);
                    var nativeData = player.getData(com.hollingsworth.arsnouveau.setup.registry.AttachmentsRegistry.MANA_ATTACHMENT);
                    var display = com.otectus.arsnspells.bridge.ArsManaDisplay.snapshot(player, nativeData);
                    equal(helper, ironsPays ? 456 : 123, display.getDouble("current"), "native Ars HUD sync must project the authoritative balance");
                    equal(helper, 123, nativeData.getMana(), "display synchronization must not change persisted native Ars data");
                    settings("disabled", 1);
                    equal(helper, 123, balance(player, ResourceUnit.ARS_MANA), "disable preserves independent Ars balance");
                    equal(helper, 456, balance(player, ResourceUnit.IRONS_MANA), "disable preserves independent Iron balance");
                }
            }
        });
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_hotfixNativeBookAndFinalPrices(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withSettings(() -> Loaded.finalPrices(helper)); helper.succeed();
    }
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_hotfixChannelFirstAndLaterFailure(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withSettings(() -> Loaded.channels(helper)); helper.succeed();
    }
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_hotfixVetoAndCeiling(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withSettings(() -> Loaded.vetoAndCeiling(helper)); helper.succeed();
    }
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_hotfixNativeRestrictionsAndModeChange(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withSettings(() -> Loaded.restrictions(helper)); helper.succeed();
    }

    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_hotfixArsPrecision(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withSettings(() -> Loaded.precision(helper));helper.succeed();
    }
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_hotfixRecastPaymentAndCooldown(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withSettings(() -> Loaded.recasts(helper));helper.succeed();
    }
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_hotfixReentrantEventsAndEffectFailure(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withSettings(() -> Loaded.edges(helper)); helper.succeed();
    }
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_channelEndsOnLastAffordablePulse(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withSettings(() -> Loaded.channelExhaustion(helper)); helper.succeed();
    }
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_swordChannelUsesFinalCostModifier(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withSettings(() -> Loaded.swordChannel(helper)); helper.succeed();
    }
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_swordDelayedCastPaysFinalPriceOnce(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withSettings(() -> Loaded.swordDelayed(helper)); helper.succeed();
    }
    /** The reported Scorch failure: another mod's check inside Iron's mana block must see the undebited pool. */
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_thirdPartyAffordabilityCheckSeesUndebitedPool(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withSettings(() -> Loaded.thirdPartyAffordability(helper)); helper.succeed();
    }
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_thirdPartyTopUpBeforeNativeWriteIsHonoured(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withSettings(() -> Loaded.thirdPartyTopUp(helper)); helper.succeed();
    }
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_thirdPartyVetoBeforeEffectRefundsSilently(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withSettings(() -> Loaded.thirdPartyVeto(helper)); helper.succeed();
    }
    @GameTest(template = "platform", batch = "ans_mana_config")
    public static void ironsLoaded_channelPulsesPayAtNativeWrite(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        withSettings(() -> Loaded.channelProbe(helper)); helper.succeed();
    }
    private static ServerPlayer player(GameTestHelper helper, String key) {
        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), key.substring(0, Math.min(key.length(), 16))));
        player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)));
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(10);
        com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry.getMana(player).setMaxMana(10000);
        if (IronsCompat.isLoaded()) Loaded.prepare(player);
        BridgeManager.getNativeArsBridge().setMana(player, 9000);
        return player;
    }
    private static float balance(ServerPlayer player, ResourceUnit unit) { return BridgeManager.getNativeBridge(unit).getMana(player); }
    private static void equal(GameTestHelper helper, double expected, double actual, String label) {
        helper.assertTrue(Math.abs(expected - actual) < .02, label + ": expected " + expected + ", got " + actual);
    }
    private static double owed(String mode, ResourceUnit origin, ResourceUnit target, int base, double rate, boolean cross) {
        double cost = base * (cross ? 1.5 : 1);
        if (mode.equals("disabled") || (mode.equals("separate") && !cross)) return origin == target ? Math.round(cost) : 0;
        if (mode.equals("separate")) return Math.round(cost * .5 * (origin == target ? 1 : rate));
        ResourceUnit payer = mode.equals("ars_primary") ? ResourceUnit.ARS_MANA : ResourceUnit.IRONS_MANA;
        return target == payer ? Math.round(cost * (origin == payer ? 1 : rate)) : 0;
    }
    private static void settings(String mode, double rate) {
        TestConfig.set(AnsConfig.MANA_UNIFICATION_MODE, mode);
        TestConfig.set(AnsConfig.CONVERSION_RATE_ARS_TO_IRON, rate);
        TestConfig.set(AnsConfig.CONVERSION_RATE_IRON_TO_ARS, rate);
        TestConfig.set(AnsConfig.CROSS_CAST_COST_MULTIPLIER, 1.5);
        TestConfig.set(AnsConfig.DUAL_COST_ARS_PERCENTAGE, .5);
        TestConfig.set(AnsConfig.DUAL_COST_ISS_PERCENTAGE, .5);
        BridgeManager.refreshMode();
    }
    private static void withSettings(Runnable body) {
        String mode = AnsConfig.MANA_UNIFICATION_MODE.get();
        double toIron = AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get(), toArs = AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get();
        double cross = AnsConfig.CROSS_CAST_COST_MULTIPLIER.get(), arsShare = AnsConfig.DUAL_COST_ARS_PERCENTAGE.get(), ironShare = AnsConfig.DUAL_COST_ISS_PERCENTAGE.get();
        boolean cooldown = AnsConfig.ENABLE_COOLDOWN_SYSTEM.get(), unified = AnsConfig.ENABLE_UNIFIED_COOLDOWNS.get(), across = AnsConfig.ENABLE_CROSS_MOD_COOLDOWNS.get();
        try { body.run(); } finally {
            TestConfig.set(AnsConfig.ENABLE_COOLDOWN_SYSTEM, cooldown); TestConfig.set(AnsConfig.ENABLE_UNIFIED_COOLDOWNS, unified); TestConfig.set(AnsConfig.ENABLE_CROSS_MOD_COOLDOWNS, across);
            TestConfig.set(AnsConfig.MANA_UNIFICATION_MODE, mode);
            TestConfig.set(AnsConfig.CONVERSION_RATE_ARS_TO_IRON, toIron);
            TestConfig.set(AnsConfig.CONVERSION_RATE_IRON_TO_ARS, toArs);
            TestConfig.set(AnsConfig.CROSS_CAST_COST_MULTIPLIER, cross);
            TestConfig.set(AnsConfig.DUAL_COST_ARS_PERCENTAGE, arsShare);
            TestConfig.set(AnsConfig.DUAL_COST_ISS_PERCENTAGE, ironShare);
            BridgeManager.refreshMode();
        }
    }
    private static final class Loaded {
        private static io.redspace.ironsspellbooks.api.spells.AbstractSpell heal() {
            return io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell("irons_spellbooks:heal");
        }
        private static io.redspace.ironsspellbooks.api.magic.MagicData data(ServerPlayer player) {
            return io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player);
        }
        private static ItemStack nativeBook(io.redspace.ironsspellbooks.api.spells.AbstractSpell spell) {
            ItemStack book = IronsTableDriver.freshBook();
            var container = io.redspace.ironsspellbooks.api.spells.ISpellContainer.getOrCreate(book).mutableCopy();
            for (int i=0;i<container.getMaxSpellCount();i++) container.removeSpellAtIndex(i);
            container.setMaxSpellCount(1);
            if (!container.addSpellAtIndex(spell,1,0,false)) throw new IllegalStateException("Cannot prepare native book");
            io.redspace.ironsspellbooks.api.spells.ISpellContainer.set(book,container.toImmutable());
            return book;
        }
        private static void ticker(ServerPlayer player) {
            try {
                var manager = io.redspace.ironsspellbooks.api.magic.MagicHelper.MAGIC_MANAGER;
                var tick = manager.getClass().getDeclaredMethod("lambda$tick$0", boolean.class, net.minecraft.world.entity.player.Player.class);
                tick.setAccessible(true); tick.invoke(manager, false, player);
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Native ticker failed",error); }
        }
        private static void drainWindup(ServerPlayer player) {
            int guard=0;
            while(data(player).isCasting() && guard++ < 500) ticker(player);
            if(data(player).isCasting()) throw new IllegalStateException("Native cast did not terminate");
        }
        static void finalPrices(GameTestHelper helper) {
            var spell=heal();
            for(String mode:MODES) for(int finalCost:new int[]{0,1,spell.getManaCost(1)+10}) {
                settings(mode,1);
                ServerPlayer player=player(helper,"hotfix_price_"+mode+finalCost);
                double initial = mode.equals("ars_primary") ? 32768.001 : Math.max(1,finalCost);
                com.otectus.arsnspells.bridge.NativeManaAccess.with(player,ResourceUnit.ARS_MANA,()->{
                    com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry.getMana(player).setMaxMana(100000);
                    com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry.getMana(player).setMana(initial);return null;
                });
                BridgeManager.getNativeIronsBridge().setMana(player,(float)initial);
                int[] calls={0};
                Consumer<io.redspace.ironsspellbooks.api.events.SpellOnCastEvent> cost=event->{
                    if(event.getEntity()==player) { calls[0]++;event.setManaCost(finalCost); }
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,false,io.redspace.ironsspellbooks.api.events.SpellOnCastEvent.class,cost);
                try {
                    helper.assertTrue(IronsProxyCastDriver.initiateViaSpellSelection(player,nativeBook(spell),0),"ordinary native book initiation failed "+mode);
                    drainWindup(player);
                    helper.assertTrue(player.getHealth()>10,"ordinary native book produced no healing "+mode);
                    helper.assertTrue(calls[0]==1,"final cost event must dispatch exactly once");
                    ResourceUnit payer=mode.equals("ars_primary")?ResourceUnit.ARS_MANA:ResourceUnit.IRONS_MANA;
                    double actual=BridgeManager.getNativeBridge(payer).transactionMana(player);
                    helper.assertTrue(Math.abs(actual-(initial-finalCost))<1e-8,"final price debit "+mode+": "+actual);
                    helper.assertTrue(data(player).getPlayerCooldowns().isOnCooldown(spell),"successful book cast needs native cooldown");
                } finally {NeoForge.EVENT_BUS.unregister(cost);}
            }
        }
        private static void categories() {
            TestConfig.set(AnsConfig.ENABLE_COOLDOWN_SYSTEM, true); TestConfig.set(AnsConfig.ENABLE_UNIFIED_COOLDOWNS, true); TestConfig.set(AnsConfig.ENABLE_CROSS_MOD_COOLDOWNS, true);
        }
        private static boolean categoryCooldown(ServerPlayer player, io.redspace.ironsspellbooks.api.spells.AbstractSpell spell) {
            return com.otectus.arsnspells.cooldown.UnifiedCooldownManager.isOnCooldown(player,
                com.otectus.arsnspells.cooldown.SpellCategorizer.categorizeIronsSpell(spell.getSchoolType().getId()));
        }
        static void channels(GameTestHelper helper) {
            settings("iss_primary",1); categories();
            String scrollMode=AnsConfig.SCROLL_COST_MODE.get();
            boolean crossCooldown=AnsConfig.ENABLE_CROSS_MOD_COOLDOWNS.get();
            TestConfig.set(AnsConfig.SCROLL_COST_MODE, "full");TestConfig.set(AnsConfig.ENABLE_CROSS_MOD_COOLDOWNS, true);
            try {
                var spell=io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell("irons_spellbooks:fire_breath");
                helper.assertTrue(spell.getCastType()==io.redspace.ironsspellbooks.api.spells.CastType.CONTINUOUS,"fixture must be a channel");
                for(boolean scroll:new boolean[]{false,true}) for(boolean priorEffect:new boolean[]{false,true}) {
                    ServerPlayer player=player(helper,"hotfix_channel_"+scroll+priorEffect);
                    var source=scroll?io.redspace.ironsspellbooks.api.spells.CastSource.SCROLL:io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK;
                    ItemStack item=scroll?new ItemStack(io.redspace.ironsspellbooks.registries.ItemRegistry.SCROLL.get()):nativeBook(spell);
                    if(scroll) io.redspace.ironsspellbooks.api.spells.ISpellContainer.createScrollContainer(spell,1,item);
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,item);
                    data(player).initiateCast(spell,1,20,source,"mainhand");data(player).setPlayerCastingItem(item);
                    BridgeManager.getNativeIronsBridge().setMana(player,priorEffect?spell.getManaCost(1)*5:0);
                    int before=player.serverLevel().getEntitiesOfClass(io.redspace.ironsspellbooks.entity.spells.fire_breath.FireBreathProjectile.class,player.getBoundingBox().inflate(10)).size();
                    ticker(player);
                    if(priorEffect) {
                        int after=player.serverLevel().getEntitiesOfClass(io.redspace.ironsspellbooks.entity.spells.fire_breath.FireBreathProjectile.class,player.getBoundingBox().inflate(10)).size();
                        helper.assertTrue(after>before,"paid first channel step must create native projectile");
                        equal(helper,spell.getManaCost(1)*4,balance(player,ResourceUnit.IRONS_MANA),"first channel step price");
                        BridgeManager.getNativeIronsBridge().setMana(player,0);
                        drainWindup(player);
                    } else {
                        helper.assertTrue(player.serverLevel().getEntitiesOfClass(io.redspace.ironsspellbooks.entity.spells.fire_breath.FireBreathProjectile.class,player.getBoundingBox().inflate(10)).size()==before,"rejected first channel step created projectile");
                    }
                    helper.assertTrue(categoryCooldown(player,spell)==(!scroll && priorEffect),"category cooldown must follow native channel/scroll timing");
                    helper.assertTrue(!data(player).isCasting(),"channel failure must clear cast state");
                    equal(helper,0,balance(player,ResourceUnit.IRONS_MANA),"failed channel step must not charge/refund previous effect");
                    if(scroll) helper.assertTrue(item.getCount()==(priorEffect?0:1),"scroll outcome must distinguish first rejection from later interruption");
                    else helper.assertTrue(data(player).getPlayerCooldowns().isOnCooldown(spell)==priorEffect,"channel cooldown must depend on earlier output");
                }
            } finally {TestConfig.set(AnsConfig.SCROLL_COST_MODE, scrollMode);TestConfig.set(AnsConfig.ENABLE_CROSS_MOD_COOLDOWNS, crossCooldown);}
        }
        /** The pool that pays a native Iron's cast in {@code mode}, funded to exactly {@code amount}. */
        private static ResourceUnit fundPayer(ServerPlayer player, String mode, double amount) {
            ResourceUnit payer = mode.equals("ars_primary") ? ResourceUnit.ARS_MANA : ResourceUnit.IRONS_MANA;
            if (payer == ResourceUnit.ARS_MANA) com.otectus.arsnspells.bridge.NativeManaAccess.with(player, ResourceUnit.ARS_MANA, () -> {
                com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry.getMana(player).setMana(amount); return null;
            });
            else BridgeManager.getNativeIronsBridge().setMana(player, (float) amount);
            return payer;
        }
        /** One pulse's price in the paying unit, from the same quote policy the cast uses. */
        private static double pulsePrice(int nativeCost, ResourceUnit payer) {
            return com.otectus.arsnspells.contract.StandardQuotePolicy.INSTANCE.quote(
                new com.otectus.arsnspells.contract.ResourceAmount(ResourceUnit.IRONS_MANA, nativeCost),
                com.otectus.arsnspells.casting.QuoteService.currentRules(),
                com.otectus.arsnspells.contract.CarrierPolicy.NATIVE_ONLY, 10000, 10000).total(payer);
        }
        /**
         * A channel funded for exactly N pulses (plus less than one more) pays N pulses, then
         * finishes as a native final pulse: no payment failure, native cooldown, cast cleared.
         */
        static void channelExhaustion(GameTestHelper helper) {
            var spell=io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell("irons_spellbooks:fire_breath");
            helper.assertTrue(spell.getCastType()==io.redspace.ironsspellbooks.api.spells.CastType.CONTINUOUS,"fixture must be a channel");
            for(String mode:new String[]{"iss_primary","ars_primary","hybrid","separate","disabled"}) for(int pulses:new int[]{1,2,3}) {
                settings(mode,mode.equals("ars_primary")?3:1);
                ServerPlayer player=player(helper,"exhaust_"+mode+pulses);
                ResourceUnit payer=mode.equals("ars_primary")?ResourceUnit.ARS_MANA:ResourceUnit.IRONS_MANA;
                double price=pulsePrice(spell.getManaCost(1),payer);
                double initial=price*pulses+price/2;
                fundPayer(player,mode,initial);
                long failed=com.otectus.arsnspells.casting.IronsCastLifecycle.failedSessions();
                long exhausted=com.otectus.arsnspells.casting.IronsCastLifecycle.exhaustedChannels();
                helper.assertTrue(IronsProxyCastDriver.initiateViaSpellSelection(player,nativeBook(spell),0),"channel initiation "+mode);
                drainWindup(player);
                equal(helper,initial-price*pulses,BridgeManager.getNativeBridge(payer).transactionMana(player),"affordable pulses paid "+mode+" x"+pulses);
                helper.assertTrue(com.otectus.arsnspells.casting.IronsCastLifecycle.failedSessions()==failed,"channel must not end in a payment failure "+mode+" x"+pulses);
                helper.assertTrue(com.otectus.arsnspells.casting.IronsCastLifecycle.exhaustedChannels()==exhausted+1,"channel must finish as a native final pulse "+mode+" x"+pulses);
                helper.assertTrue(data(player).getPlayerCooldowns().isOnCooldown(spell),"exhausted channel keeps the native cooldown "+mode);
                helper.assertTrue(!data(player).isCasting(),"exhausted channel must clear cast state "+mode);
                helper.assertTrue(com.otectus.arsnspells.casting.CastLedger.ledger().openFor(player.getUUID()).isEmpty(),"exhausted channel leaked a reservation "+mode);
            }
        }
        /** A SWORD-source channel whose final cost a listener halves runs for as many pulses as the halved price affords. */
        static void swordChannel(GameTestHelper helper) {
            settings("iss_primary",1);
            boolean consume=io.redspace.ironsspellbooks.config.ServerConfigs.SWORDS_CONSUME_MANA.get();
            var spell=io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell("irons_spellbooks:fire_breath");
            ServerPlayer player=player(helper,"sword_channel_half");
            int finalCost=Math.max(1,spell.getManaCost(1)/2);
            Consumer<io.redspace.ironsspellbooks.api.events.SpellOnCastEvent> halve=event->{
                if(event.getEntity()==player && event.getSpellId().equals(spell.getSpellId())) event.setManaCost(finalCost);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL,false,io.redspace.ironsspellbooks.api.events.SpellOnCastEvent.class,halve);
            try {
                TestConfig.set(io.redspace.ironsspellbooks.config.ServerConfigs.SWORDS_CONSUME_MANA, true);
                double initial=finalCost*3+finalCost/2.0;
                BridgeManager.getNativeIronsBridge().setMana(player,(float)initial);
                long failed=com.otectus.arsnspells.casting.IronsCastLifecycle.failedSessions();
                ItemStack sword=new ItemStack(Items.IRON_SWORD);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,sword);
                helper.assertTrue(spell.attemptInitiateCast(sword,1,player.level(),player,io.redspace.ironsspellbooks.api.spells.CastSource.SWORD,true,"mainhand"),"sword channel initiation");
                drainWindup(player);
                equal(helper,initial-finalCost*3,balance(player,ResourceUnit.IRONS_MANA),"halved sword channel pays three halved pulses");
                helper.assertTrue(com.otectus.arsnspells.casting.IronsCastLifecycle.failedSessions()==failed,"sword channel must not end in a payment failure");
                helper.assertTrue(data(player).getPlayerCooldowns().isOnCooldown(spell),"sword channel respects the native cooldown");
            } finally {
                TestConfig.set(io.redspace.ironsspellbooks.config.ServerConfigs.SWORDS_CONSUME_MANA, consume);
                NeoForge.EVENT_BUS.unregister(halve);
            }
        }
        /** A delayed SWORD cast whose final cost a listener raises is priced at that cost exactly once. */
        static void swordDelayed(GameTestHelper helper) {
            settings("iss_primary",1);
            boolean consume=io.redspace.ironsspellbooks.config.ServerConfigs.SWORDS_CONSUME_MANA.get();
            var spell=io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell("irons_spellbooks:fireball");
            helper.assertTrue(spell.getCastType()==io.redspace.ironsspellbooks.api.spells.CastType.LONG,"fixture must be a delayed cast");
            int raised=spell.getManaCost(1)*2;
            try {
                TestConfig.set(io.redspace.ironsspellbooks.config.ServerConfigs.SWORDS_CONSUME_MANA, true);
                for(boolean affordable:new boolean[]{true,false}) {
                    ServerPlayer player=player(helper,"sword_delayed_"+affordable);
                    int[] calls={0};
                    Consumer<io.redspace.ironsspellbooks.api.events.SpellOnCastEvent> raise=event->{
                        if(event.getEntity()==player && event.getSpellId().equals(spell.getSpellId())) {calls[0]++;event.setManaCost(raised);}
                    };
                    NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL,false,io.redspace.ironsspellbooks.api.events.SpellOnCastEvent.class,raise);
                    try {
                        double initial=affordable?raised+7:raised-1;
                        BridgeManager.getNativeIronsBridge().setMana(player,(float)initial);
                        long failed=com.otectus.arsnspells.casting.IronsCastLifecycle.failedSessions();
                        int before=player.serverLevel().getEntitiesOfClass(io.redspace.ironsspellbooks.entity.spells.fireball.MagicFireball.class,player.getBoundingBox().inflate(16)).size();
                        ItemStack sword=new ItemStack(Items.IRON_SWORD);
                        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,sword);
                        helper.assertTrue(spell.attemptInitiateCast(sword,1,player.level(),player,io.redspace.ironsspellbooks.api.spells.CastSource.SWORD,true,"mainhand"),"delayed sword initiation "+affordable);
                        drainWindup(player);
                        int after=player.serverLevel().getEntitiesOfClass(io.redspace.ironsspellbooks.entity.spells.fireball.MagicFireball.class,player.getBoundingBox().inflate(16)).size();
                        helper.assertTrue(calls[0]==1,"final cost event must dispatch exactly once");
                        equal(helper,affordable?initial-raised:initial,balance(player,ResourceUnit.IRONS_MANA),"delayed sword final price "+affordable);
                        helper.assertTrue((after>before)==affordable,"delayed sword effect only when paid");
                        helper.assertTrue(data(player).getPlayerCooldowns().isOnCooldown(spell)==affordable,"no phantom cooldown for an unpaid cast");
                        helper.assertTrue(com.otectus.arsnspells.casting.IronsCastLifecycle.failedSessions()==failed+(affordable?0:1),"only the unaffordable cast reports a shortage");
                    } finally {NeoForge.EVENT_BUS.unregister(raise);}
                }
            } finally {TestConfig.set(io.redspace.ironsspellbooks.config.ServerConfigs.SWORDS_CONSUME_MANA, consume);}
        }
        private static long failures() { return com.otectus.arsnspells.casting.IronsCastLifecycle.failedSessions(); }
        private static long vetoes() { return com.otectus.arsnspells.casting.IronsCastLifecycle.vetoedInvocations(); }
        private static ResourceUnit payer(String mode) { return mode.equals("ars_primary") ? ResourceUnit.ARS_MANA : ResourceUnit.IRONS_MANA; }
        private static void probeRan(GameTestHelper helper, ServerPlayer player, int expected, String label) {
            int observed = CastProbe.observed(player);
            helper.assertTrue(observed == expected, "cast probe ran " + observed + " times, expected " + expected + " (" + label
                + "); 0 means Iron's mana block was never reached, or MixinCastProbe is not applied (the GameTest run must set -D"
                + CastProbe.PROPERTY + "=true)");
        }
        /**
         * A mod that checks affordability inside Iron's mana block, as Animus does, must see the
         * pool before ANS takes the price. The reported Scorch 9 cast cost 90 of a 100 pool: ANS
         * took 90 first, the check then saw 10, cancelled the cast, and ANS refunded and warned.
         * Every mode and conversion rate, with the price more than half the pool.
         */
        static void thirdPartyAffordability(GameTestHelper helper) {
            var spell = heal();
            for (String mode : MODES) for (double rate : new double[]{0.5, 1, 3}) {
                settings(mode, rate);
                String label = mode + " rate " + rate;
                ServerPlayer player = player(helper, "probe_afford_" + mode + rate);
                ResourceUnit payer = payer(mode);
                double price = pulsePrice(spell.getManaCost(1), payer);
                double initial = price * 1.2;
                fundPayer(player, mode, initial);
                long failed = failures(), vetoed = vetoes();
                CastProbe.arm(player, CastProbe.Mode.AFFORDABILITY);
                try {
                    helper.assertTrue(IronsProxyCastDriver.initiateViaSpellSelection(player, nativeBook(spell), 0), "initiation " + label);
                    drainWindup(player);
                    probeRan(helper, player, 1, label);
                    helper.assertTrue(player.getHealth() > 10, "an affordable cast was cancelled by a check inside Iron's mana block " + label);
                    equal(helper, initial - price, BridgeManager.getNativeBridge(payer).transactionMana(player), "price paid exactly once " + label);
                    helper.assertTrue(failures() == failed, "an affordable cast must not report a payment failure " + label);
                    helper.assertTrue(vetoes() == vetoed, "an affordable cast must not be vetoed " + label);
                    helper.assertTrue(data(player).getPlayerCooldowns().isOnCooldown(spell), "native cooldown " + label);
                    helper.assertTrue(com.otectus.arsnspells.casting.CastLedger.ledger().openFor(player.getUUID()).isEmpty(), "reservation leaked " + label);
                } finally { CastProbe.disarm(player); }
            }
        }
        /** A mod that pays a shortfall inside Iron's mana block and tops the pool up (Animus blood casting) funds the cast. */
        static void thirdPartyTopUp(GameTestHelper helper) {
            var spell = heal();
            for (String mode : MODES) {
                settings(mode, 1);
                ServerPlayer player = player(helper, "probe_topup_" + mode);
                ResourceUnit payer = payer(mode);
                double price = pulsePrice(spell.getManaCost(1), payer);
                fundPayer(player, mode, price / 2);
                long failed = failures();
                CastProbe.arm(player, CastProbe.Mode.TOP_UP);
                try {
                    helper.assertTrue(IronsProxyCastDriver.initiateViaSpellSelection(player, nativeBook(spell), 0), "initiation " + mode);
                    drainWindup(player);
                    probeRan(helper, player, 1, mode);
                    helper.assertTrue(player.getHealth() > 10, "a cast another mod funded inside Iron's mana block must resolve " + mode);
                    equal(helper, 0, BridgeManager.getNativeBridge(payer).transactionMana(player), "the topped-up price is paid once " + mode);
                    helper.assertTrue(failures() == failed, "a funded cast must not report a payment failure " + mode);
                } finally { CastProbe.disarm(player); }
            }
        }
        /** A mod that cancels the cast inside Iron's mana block decides the cast; ANS charges nothing and says nothing. */
        static void thirdPartyVeto(GameTestHelper helper) {
            settings("iss_primary", 1);
            var spell = heal();
            ServerPlayer player = player(helper, "probe_veto");
            long failed = failures(), vetoed = vetoes();
            CastProbe.arm(player, CastProbe.Mode.VETO);
            try {
                helper.assertTrue(IronsProxyCastDriver.initiateViaSpellSelection(player, nativeBook(spell), 0), "veto fixture initiation");
                drainWindup(player);
                probeRan(helper, player, 1, "veto");
                equal(helper, 10, player.getHealth(), "a vetoed cast must not heal");
                equal(helper, 9000, balance(player, ResourceUnit.IRONS_MANA), "a vetoed cast must not be charged");
                helper.assertTrue(!data(player).getPlayerCooldowns().isOnCooldown(spell), "Iron's adds no cooldown to a cast cancelled before its effect");
                helper.assertTrue(failures() == failed, "another mod's veto is not an ANS payment failure");
                helper.assertTrue(vetoes() == vetoed + 1, "the veto must be recorded once");
                helper.assertTrue(!data(player).isCasting(), "Iron's own follow-through ends the cast");
                helper.assertTrue(com.otectus.arsnspells.casting.CastLedger.ledger().openFor(player.getUUID()).isEmpty(), "veto leaked a reservation");
            } finally { CastProbe.disarm(player); }
        }
        /** A channel under an affordability check pays every affordable pulse and still ends as a native final pulse. */
        static void channelProbe(GameTestHelper helper) {
            settings("iss_primary", 1);
            var spell = io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell("irons_spellbooks:fire_breath");
            helper.assertTrue(spell.getCastType() == io.redspace.ironsspellbooks.api.spells.CastType.CONTINUOUS, "fixture must be a channel");
            ServerPlayer player = player(helper, "probe_channel");
            double price = pulsePrice(spell.getManaCost(1), ResourceUnit.IRONS_MANA);
            double initial = price * 3 + price / 2;
            fundPayer(player, "iss_primary", initial);
            long failed = failures(), vetoed = vetoes();
            long exhausted = com.otectus.arsnspells.casting.IronsCastLifecycle.exhaustedChannels();
            CastProbe.arm(player, CastProbe.Mode.AFFORDABILITY);
            try {
                helper.assertTrue(IronsProxyCastDriver.initiateViaSpellSelection(player, nativeBook(spell), 0), "channel initiation");
                drainWindup(player);
                probeRan(helper, player, 3, "channel");
                equal(helper, initial - price * 3, balance(player, ResourceUnit.IRONS_MANA), "three affordable pulses paid");
                helper.assertTrue(failures() == failed, "the last affordable pulse must not fail its payment");
                helper.assertTrue(vetoes() == vetoed, "no pulse may be vetoed");
                helper.assertTrue(com.otectus.arsnspells.casting.IronsCastLifecycle.exhaustedChannels() == exhausted + 1, "channel must finish as a native final pulse");
                helper.assertTrue(data(player).getPlayerCooldowns().isOnCooldown(spell), "exhausted channel keeps the native cooldown");
                helper.assertTrue(!data(player).isCasting(), "exhausted channel must clear cast state");
                helper.assertTrue(com.otectus.arsnspells.casting.CastLedger.ledger().openFor(player.getUUID()).isEmpty(), "channel leaked a reservation");
            } finally { CastProbe.disarm(player); }
        }
        static void vetoAndCeiling(GameTestHelper helper) {
            settings("iss_primary",1);var spell=heal();
            for(boolean veto:new boolean[]{true,false}) {
                ServerPlayer player=player(helper,"hotfix_veto_"+veto);
                int[] writes={0};
                Consumer<io.redspace.ironsspellbooks.api.events.ChangeManaEvent> listener=event->{
                    if(event.getEntity()==player) {writes[0]++;if(veto)event.setCanceled(true);}
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,false,io.redspace.ironsspellbooks.api.events.ChangeManaEvent.class,listener);
                try {
                    helper.assertTrue(IronsProxyCastDriver.initiateViaSpellSelection(player,nativeBook(spell),0),"native book admission");
                    drainWindup(player);
                    helper.assertTrue((player.getHealth()>10)==!veto,"mana veto must prevent effects");
                    helper.assertTrue(writes[0]==1,"native replacement must not issue a second zero-delta write");
                    equal(helper,veto?9000:9000-spell.getManaCost(1),balance(player,ResourceUnit.IRONS_MANA),"veto resource state");
                    helper.assertTrue(data(player).getPlayerCooldowns().isOnCooldown(spell)==!veto,"veto cooldown state");
                } finally {NeoForge.EVENT_BUS.unregister(listener);}
            }
            // A ceiling below the balance, as a pack can leave Iron's max_mana under a full pool
            // for a few ticks. The payment takes exactly the price whatever the surplus: the Iron's
            // adapter keeps the ceiling clamp off its write, and applying the ceiling stays Iron's
            // regeneration's job, which still does it on its next tick. 3.3.4 and 3.3.5 refused
            // every cast in this state (the reported CEILING_INCONSISTENT at 240 mana).
            int price=spell.getManaCost(1);
            for(boolean small:new boolean[]{true,false}) {
                ServerPlayer player=player(helper,"hotfix_ceiling_"+small);
                double ceiling=small?9000-price/2.0:1000;
                var max=player.getAttribute(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA);
                max.setBaseValue(ceiling);
                helper.assertTrue(IronsProxyCastDriver.initiateViaSpellSelection(player,nativeBook(spell),0),"ceiling admission");
                drainWindup(player);
                helper.assertTrue(player.getHealth()>10,"a balance above the ceiling must not refuse the cast");
                equal(helper,9000-price,balance(player,ResourceUnit.IRONS_MANA),"payment above the ceiling takes exactly the price");
                helper.assertTrue(data(player).getPlayerCooldowns().isOnCooldown(spell),"cast above the ceiling keeps the native cooldown");
                helper.assertTrue(!com.otectus.arsnspells.bridge.IronsBridge.debitGuardActive(player),"the debit guard must not outlive the write");
                equal(helper,ceiling,max.getValue(),"the ceiling must read as before the payment");
                ((io.redspace.ironsspellbooks.capabilities.magic.MagicManager)io.redspace.ironsspellbooks.api.magic.MagicHelper.MAGIC_MANAGER)
                    .regenPlayerMana(player,data(player));
                float regenerated=balance(player,ResourceUnit.IRONS_MANA);
                helper.assertTrue(regenerated<=Math.floor(ceiling)+.02,"Iron's regeneration, not the payment, applies the ceiling: got "+regenerated);
                if(!small) equal(helper,1000,regenerated,"a surplus larger than the price is clamped by the next regeneration tick");
            }
        }
        static void restrictions(GameTestHelper helper) {
            settings("iss_primary",1);var spell=heal();
            var source=io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK;
            ServerPlayer player=player(helper,"hotfix_native_restrictions");
            data(player).getPlayerCooldowns().addCooldown(spell,100);
            helper.assertTrue(!spell.canBeCastedBy(1,source,data(player),player).isSuccess(),"existing native cooldown must remain a failure");
            data(player).getPlayerCooldowns().clearCooldowns();
            boolean adventure=io.redspace.ironsspellbooks.config.ServerConfigs.DISABLE_ADVENTURE_MODE_CASTING.get();
            try {
                io.redspace.ironsspellbooks.config.ServerConfigs.DISABLE_ADVENTURE_MODE_CASTING.set(true);
                player.setGameMode(GameType.ADVENTURE);
                helper.assertTrue(!spell.canBeCastedBy(1,source,data(player),player).isSuccess(),"adventure restriction must remain a failure");
                helper.assertTrue(!com.otectus.arsnspells.spell.CastValidationScope.isActive(data(player)),"validation scope leaked");
            } finally {io.redspace.ironsspellbooks.config.ServerConfigs.DISABLE_ADVENTURE_MODE_CASTING.set(adventure);player.setGameMode(GameType.SURVIVAL);}
            helper.assertTrue(IronsProxyCastDriver.initiateViaSpellSelection(player,nativeBook(spell),0),"mode-change fixture initiation");
            settings("ars_primary",1);drainWindup(player);
            equal(helper,10,player.getHealth(),"mode change must cancel latched cast");
            equal(helper,9000,balance(player,ResourceUnit.ARS_MANA),"mode change must not debit new pool");
        }
        static void precision(GameTestHelper helper) {
            settings("ars_primary",1);var spell=heal();
            ServerPlayer player=player(helper,"hotfix_precision");
            com.otectus.arsnspells.bridge.NativeManaAccess.with(player,ResourceUnit.ARS_MANA,()->{
                com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry.getMana(player).setMaxMana(100000);
                com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry.getMana(player).setMana(32768.001);return null;
            });
            Consumer<io.redspace.ironsspellbooks.api.events.SpellOnCastEvent> cost=event->{if(event.getEntity()==player)event.setManaCost(1);};
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,false,io.redspace.ironsspellbooks.api.events.SpellOnCastEvent.class,cost);
            try {
                helper.assertTrue(IronsProxyCastDriver.initiateViaSpellSelection(player,nativeBook(spell),0),"precision cast admission");
                drainWindup(player);
                helper.assertTrue(player.getHealth()>10,"one-mana debit from 32768.001 falsely rejected");
                double actual=BridgeManager.getNativeArsBridge().transactionMana(player);
                helper.assertTrue(Math.abs(actual-32767.001)<1e-8,"double native balance must lose exactly one mana: "+actual);
            } finally {NeoForge.EVENT_BUS.unregister(cost);}
        }
        static void recasts(GameTestHelper helper) {
            settings("iss_primary",1); categories();
            var spell=io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell("irons_spellbooks:flaming_barrage");
            var source=io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK;
            ServerPlayer player=player(helper,"hotfix_recast");ItemStack book=nativeBook(spell);
            helper.assertTrue(spell.attemptInitiateCast(book,1,player.level(),player,source,false,"mainhand"),"initial recast fixture admission");
            int before=player.serverLevel().getEntitiesOfClass(io.redspace.ironsspellbooks.entity.spells.fireball.SmallMagicFireball.class,player.getBoundingBox().inflate(10)).size();
            spell.castSpell(player.level(),1,player,source,true);
            spell.onServerCastComplete(player.level(),1,player,data(player),false);
            int after=player.serverLevel().getEntitiesOfClass(io.redspace.ironsspellbooks.entity.spells.fireball.SmallMagicFireball.class,player.getBoundingBox().inflate(10)).size();
            helper.assertTrue(after>before,"native recast spell must create a real projectile");
            double paid=balance(player,ResourceUnit.IRONS_MANA);
            equal(helper,9000-spell.getManaCost(1),paid,"initial recast cost");
            helper.assertTrue(data(player).getPlayerRecasts().hasRecastForSpell(spell.getSpellId()),"native recast sequence must open");
            helper.assertTrue(!data(player).getPlayerCooldowns().isOnCooldown(spell),"recast cooldown must wait for sequence end");
            helper.assertTrue(!categoryCooldown(player,spell),"category cooldown must wait for native recast sequence end");
            int guard=0;
            while(data(player).getPlayerRecasts().hasRecastForSpell(spell.getSpellId()) && guard++<40) {
                helper.assertTrue(spell.attemptInitiateCast(book,1,player.level(),player,source,false,"mainhand"),"native recast admission");
                spell.castSpell(player.level(),1,player,source,true);
                spell.onServerCastComplete(player.level(),1,player,data(player),false);
                equal(helper,paid,balance(player,ResourceUnit.IRONS_MANA),"native recasts must remain exempt");
            }
            helper.assertTrue(!data(player).getPlayerRecasts().hasRecastForSpell(spell.getSpellId()),"recasts must terminate");
            helper.assertTrue(data(player).getPlayerCooldowns().isOnCooldown(spell),"native recast sequence ends on cooldown");
            helper.assertTrue(categoryCooldown(player,spell),"category cooldown must follow native recast sequence end");
        }
        static void edges(GameTestHelper helper) {
            var spell=heal(); var source=io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK;
            for(String change:new String[]{"same_settings","mode","reset","throw","reenter"}) {
                settings("iss_primary",1); ServerPlayer player=player(helper,"hotfix_event_"+change);
                int[] calls={0};
                Consumer<io.redspace.ironsspellbooks.api.events.SpellOnCastEvent> listener=event->{
                    if(event.getEntity()!=player || ++calls[0]>1)return;
                    switch(change) {
                        case "same_settings" -> BridgeManager.refreshMode();
                        case "mode" -> settings("ars_primary",1);
                        case "reset" -> data(player).resetCastingState();
                        case "throw" -> throw new IllegalStateException("Expected injected cost listener failure");
                        case "reenter" -> spell.castSpell(player.level(),1,player,source,true);
                    }
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,false,io.redspace.ironsspellbooks.api.events.SpellOnCastEvent.class,listener);
                try {
                    helper.assertTrue(IronsProxyCastDriver.initiateViaSpellSelection(player,nativeBook(spell),0),"edge fixture admission"); drainWindup(player);
                    boolean success=change.equals("same_settings")||change.equals("reenter");
                    helper.assertTrue((player.getHealth()>10)==success,"unexpected effect for "+change);
                    equal(helper,success?9000-spell.getManaCost(1):9000,balance(player,ResourceUnit.IRONS_MANA),"event-boundary accounting "+change);
                    equal(helper,9000,balance(player,ResourceUnit.ARS_MANA),"event must not silently switch payer");
                    helper.assertTrue(calls[0]==1,"reentrant invocation must not redispatch cost event");
                    helper.assertTrue(!CastValidationScope.isActive(data(player)),"event exception leaked validation");
                } finally {NeoForge.EVENT_BUS.unregister(listener);}
            }
            settings("iss_primary",1); ServerPlayer player=player(helper,"hotfix_partial_world_failure");
            var partial=new io.redspace.ironsspellbooks.spells.holy.HealSpell() {
                @Override public void onCast(net.minecraft.world.level.Level world,int level,net.minecraft.world.entity.LivingEntity caster,
                        io.redspace.ironsspellbooks.api.spells.CastSource castSource,io.redspace.ironsspellbooks.api.magic.MagicData magic) {
                    super.onCast(world,level,caster,castSource,magic);
                    throw new IllegalStateException("Expected failure after native healing");
                }
            };
            partial.castSpell(player.level(),1,player,source,true);
            helper.assertTrue(player.getHealth()>10,"native partial effect did not occur");
            equal(helper,9000-partial.getManaCost(1),balance(player,ResourceUnit.IRONS_MANA),"world effects must remain paid after exception");
            helper.assertTrue(com.otectus.arsnspells.casting.CastLedger.ledger().openFor(player.getUUID()).isEmpty(),"failed effect leaked a payment reservation");
        }
        static void prepare(ServerPlayer player) {
            var data = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player);
            data.setServerPlayer(player);
            data.resetCastingState();
            player.getAttribute(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA).setBaseValue(10000);
            BridgeManager.getNativeIronsBridge().setMana(player, 9000);
        }
        static void irons(GameTestHelper helper) {
            var spell = io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell("irons_spellbooks:heal");
            var source = io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK;
            for (String mode : MODES) for (double rate : new double[]{.01, 1, 3}) for (boolean cross : new boolean[]{false, true}) {
                settings(mode, rate);
                ServerPlayer player = player(helper, "iron_payment_" + mode + rate + cross);
                float ars = balance(player, ResourceUnit.ARS_MANA), irons = balance(player, ResourceUnit.IRONS_MANA);
                if (cross) CrossCastContext.begin(player, CrossSpellType.IRONS_SPELLBOOKS, player.level().getGameTime(), 0, 0, spell.getSpellId());
                try {
                    helper.assertTrue(spell.attemptInitiateCast(ItemStack.EMPTY, 1, player.level(), player, source, false, "mainhand"), "native Iron initiation refused " + mode);
                } finally { CrossCastContext.clear(player); }
                equal(helper, ars, balance(player, ResourceUnit.ARS_MANA), "Iron initiation Ars debit");
                equal(helper, irons, balance(player, ResourceUnit.IRONS_MANA), "Iron initiation Iron debit");
                spell.castSpell(player.level(), 1, player, source, false);
                helper.assertTrue(player.getHealth() > 10, "funded native Iron cast did not heal " + mode);
                equal(helper, ars - owed(mode, ResourceUnit.IRONS_MANA, ResourceUnit.ARS_MANA, spell.getManaCost(1), rate, cross), balance(player, ResourceUnit.ARS_MANA), "Iron cast Ars delta " + mode);
                equal(helper, irons - owed(mode, ResourceUnit.IRONS_MANA, ResourceUnit.IRONS_MANA, spell.getManaCost(1), rate, cross), balance(player, ResourceUnit.IRONS_MANA), "Iron cast Iron delta " + mode);
            }
            settings("separate", 3);
            ServerPlayer player = player(helper, "iron_payment_lost_balance");
            helper.assertTrue(spell.attemptInitiateCast(ItemStack.EMPTY, 1, player.level(), player, source, false, "mainhand"), "funded initiation refused");
            BridgeManager.getNativeIronsBridge().setMana(player, 0);
            float ars = balance(player, ResourceUnit.ARS_MANA);
            spell.castSpell(player.level(), 1, player, source, false);
            equal(helper, 10, player.getHealth(), "lost payment must prevent healing");
            equal(helper, ars, balance(player, ResourceUnit.ARS_MANA), "lost payment must not debit Ars");
            equal(helper, 0, balance(player, ResourceUnit.IRONS_MANA), "lost payment must not go negative");
        }
    }
}
