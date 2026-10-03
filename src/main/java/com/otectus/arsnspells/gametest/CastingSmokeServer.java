package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Explicit disposable-world developer smoke test, inert without the Gradle profile. The
 * NeoForge counterpart of the Forge 1.20.1 class of the same name: {@code -PwithCastingServerSmoke}
 * on {@code runServer}, or the client smoke on an integrated server, casts Iron's Heal from a
 * native book in ISS_PRIMARY and requires the exact final balance, the heal and the native
 * cooldown ({@code ANS_CAST_SERVER_PASS}).
 */
@EventBusSubscriber(modid = "ars_n_spells")
public final class CastingSmokeServer {
    private static final java.util.Map<java.util.UUID, Integer> PENDING = new java.util.HashMap<>();
    private CastingSmokeServer() {}

    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Integer age = PENDING.get(player.getUUID());
        if (age == null) return;
        if (age > 200) throw new IllegalStateException("Server casting smoke timed out");
        PENDING.put(player.getUUID(), age + 1);
        if (age < 0) {
            if (age == -1) Loaded.prepare(player);
            return;
        }
        Loaded.check(player);
    }

    @SubscribeEvent public static void joined(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (Boolean.getBoolean("ans.castingSmoke.server") && event.getEntity() instanceof ServerPlayer player) {
            PENDING.put(player.getUUID(), -80);
        }
    }

    public static void prepare(ServerPlayer player) {
        if (!com.otectus.arsnspells.compat.IronsCompat.isLoaded()) throw new IllegalStateException("Casting smoke requires Iron's");
        Loaded.prepare(player);
        PENDING.put(player.getUUID(), 0);
    }

    private static final class Loaded {
        static void check(ServerPlayer player) {
            var data = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player);
            if (data.isCasting()) return;
            var spell = io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell("irons_spellbooks:heal");
            double mana = BridgeManager.getNativeIronsBridge().transactionMana(player);
            if (player.getHealth() <= 10 || mana != 1000 - spell.getManaCost(1) || !data.getPlayerCooldowns().isOnCooldown(spell))
                throw new IllegalStateException("Server casting smoke failed: health=" + player.getHealth() + " mana=" + mana);
            PENDING.remove(player.getUUID());
            org.slf4j.LoggerFactory.getLogger(CastingSmokeServer.class).info(
                "ANS_CAST_SERVER_PASS player={} health={} mana={} nativeCooldown=true", player.getUUID(), player.getHealth(), mana);
        }

        static void prepare(ServerPlayer player) {
            AnsConfig.MANA_UNIFICATION_MODE.set("iss_primary");
            BridgeManager.refreshMode();
            io.redspace.ironsspellbooks.config.ServerConfigs.MANA_REGEN_MULTIPLIER.set(0.0);
            player.setGameMode(GameType.SPECTATOR);
            player.setGameMode(GameType.SURVIVAL);
            player.setHealth(10);
            player.setNoGravity(true);
            player.teleportTo(0, 120, 0);
            player.getAttribute(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA).setBaseValue(1000);
            player.getAttribute(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MANA_REGEN).setBaseValue(0);
            var data = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player);
            data.setServerPlayer(player);
            data.resetCastingState();
            data.getPlayerCooldowns().clearCooldowns();
            BridgeManager.getNativeIronsBridge().setMana(player, 1000);
            var spell = io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell("irons_spellbooks:heal");
            ItemStack book = ItemStack.EMPTY;
            for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                var candidate = new ItemStack(item);
                if (com.otectus.arsnspells.spell.IronsBookBindingUtil.isIronsSpellBook(candidate)) {
                    book = candidate;
                    break;
                }
            }
            if (book.isEmpty()) throw new IllegalStateException("Native book missing");
            var mutable = io.redspace.ironsspellbooks.api.spells.ISpellContainer.getOrCreate(book).mutableCopy();
            for (int i = 0; i < mutable.getMaxSpellCount(); i++) mutable.removeSpellAtIndex(i);
            mutable.setMaxSpellCount(1);
            mutable.addSpellAtIndex(spell, 1, 0, false);
            io.redspace.ironsspellbooks.api.spells.ISpellContainer.set(book, mutable.toImmutable());
            io.redspace.ironsspellbooks.api.util.Utils.setPlayerSpellbookStack(player, book);
            data.getSyncedData().setSpellSelection(new io.redspace.ironsspellbooks.gui.overlays.SpellSelection(
                io.redspace.ironsspellbooks.compat.Curios.SPELLBOOK_SLOT, 0));
            if (!io.redspace.ironsspellbooks.api.util.Utils.serverSideInitiateCast(player))
                throw new IllegalStateException("Native book initiation refused");
            org.slf4j.LoggerFactory.getLogger(CastingSmokeServer.class).info(
                "ANS_CAST_SMOKE_STARTED player={} expectedMana={}", player.getUUID(), 1000 - spell.getManaCost(1));
        }
    }
}
