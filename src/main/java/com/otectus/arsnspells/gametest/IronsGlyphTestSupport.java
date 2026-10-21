package com.otectus.arsnspells.gametest;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainerMutable;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** Iron's-referencing helpers for the imported-glyph GameTests; call only behind the Iron's gate. */
final class IronsGlyphTestSupport {
    private IronsGlyphTestSupport() {}

    static boolean slowed(LivingEntity entity) {
        return entity.hasEffect(MobEffectRegistry.SLOWED.get());
    }

    static boolean onCooldown(ServerPlayer player, String spellId) {
        AbstractSpell spell = SpellRegistry.getSpell(new ResourceLocation(spellId));
        return MagicData.getPlayerMagicData(player).getPlayerCooldowns().isOnCooldown(spell);
    }

    static void clearCooldowns(ServerPlayer player) {
        MagicData.getPlayerMagicData(player).getPlayerCooldowns().clearCooldowns();
    }

    static int magicProjectilesNear(ServerPlayer player, AABB box) {
        return player.level().getEntitiesOfClass(AbstractMagicProjectile.class, box).size();
    }

    static boolean anyRisingProjectileOwnedBy(ServerPlayer player, AABB box) {
        return magicProjectiles(player, box).stream().anyMatch(p -> p.getDeltaMovement().y > 0.1 && p.getOwner() == player);
    }

    static java.util.List<AbstractMagicProjectile> magicProjectiles(ServerPlayer player, AABB box) {
        return player.level().getEntitiesOfClass(AbstractMagicProjectile.class, box);
    }

    static boolean casting(ServerPlayer player) {
        return MagicData.getPlayerMagicData(player).isCasting();
    }

    static String castingSpell(ServerPlayer player) {
        return MagicData.getPlayerMagicData(player).getCastingSpellId();
    }

    static int manaCost(String spellId, int level) {
        return SpellRegistry.getSpell(new ResourceLocation(spellId)).getManaCost(level);
    }

    static boolean learned(ServerPlayer player, String spellId) {
        AbstractSpell spell = SpellRegistry.getSpell(new ResourceLocation(spellId));
        return spell.isLearned(player);
    }

    static void learn(ServerPlayer player, String spellId) {
        AbstractSpell spell = SpellRegistry.getSpell(new ResourceLocation(spellId));
        MagicData.getPlayerMagicData(player).getSyncedData().learnSpell(spell);
    }

    // ---- Scrolls the Glyph Press must refuse, built the way Iron's builds containers --------

    static ItemStack bareScroll() {
        return new ItemStack(io.redspace.ironsspellbooks.registries.ItemRegistry.SCROLL.get());
    }

    /** A scroll whose single slot holds {@code spell} at {@code level}, with no bounds applied by us. */
    static ItemStack scrollHolding(String spellId, int level) {
        return scrollWith(1, new String[]{spellId}, new int[]{level});
    }

    static ItemStack noneScroll() {
        ItemStack scroll = bareScroll();
        ISpellContainerMutable container = ISpellContainer.create(1, false, false).mutableCopy();
        container.addSpellAtIndex(SpellRegistry.none(), 1, 0, false);
        ISpellContainer.set(scroll, container.toImmutable());
        return scroll;
    }

    static ItemStack emptyContainerScroll() {
        ItemStack scroll = bareScroll();
        ISpellContainer.set(scroll, ISpellContainer.create(1, false, false));
        return scroll;
    }

    static ItemStack scrollWith(int slots, String[] spells, int[] levels) {
        ItemStack scroll = bareScroll();
        ISpellContainerMutable container = ISpellContainer.create(slots, false, false).mutableCopy();
        for (int i = 0; i < spells.length; i++)
            container.addSpellAtIndex(SpellRegistry.getSpell(new ResourceLocation(spells[i])), levels[i], i, false);
        ISpellContainer.set(scroll, container.toImmutable());
        return scroll;
    }

    /** A scroll holding one of ANS's own proxy spells, as binding leaves on an Iron's book. */
    static ItemStack proxyScroll() {
        ItemStack scroll = bareScroll();
        AbstractSpell proxy = SpellRegistry.getSpell(new ResourceLocation("ars_n_spells", "ars_cross_1"));
        ISpellContainer.set(scroll, ISpellContainer.createScrollContainer(proxy, 1, scroll));
        return scroll;
    }

    static int maxLevel(String spellId) {
        return SpellRegistry.getSpell(new ResourceLocation(spellId)).getMaxLevel();
    }

    /**
     * Run {@code action} and return the level Iron's final cast event carried for {@code spellId}
     * cast by {@code player}, or -1 when none was posted.
     */
    static int castLevelOf(ServerPlayer player, String spellId, Runnable action) {
        int[] level = {-1};
        java.util.function.Consumer<io.redspace.ironsspellbooks.api.events.SpellOnCastEvent> listener = event -> {
            if (event.getEntity() == player && spellId.equals(event.getSpellId())) level[0] = event.getSpellLevel();
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, false,
            io.redspace.ironsspellbooks.api.events.SpellOnCastEvent.class, listener);
        try {
            action.run();
        } finally {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);
        }
        return level[0];
    }

    /** Raise the player's Iron's max mana to {@code amount} (Iron's clamps the pool to it) and fill the pool. */
    static void fundIronsPool(ServerPlayer player, float amount) {
        var attribute = player.getAttribute(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get());
        java.util.UUID id = java.util.UUID.fromString("0a000000-0000-0000-0000-0000000067eb");
        if (attribute != null && attribute.getModifier(id) == null) {
            attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, "ANS glyph test mana",
                amount, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
        }
        MagicData.getPlayerMagicData(player).setMana(amount);
    }

    /** Cancel whatever the player is casting, as Iron's does when a cast is interrupted on the server. */
    static void cancelNativeCast(ServerPlayer player) {
        io.redspace.ironsspellbooks.api.util.Utils.serverSideCancelCast(player);
    }

    /**
     * Iron's summons inside {@code box} that Iron's records as {@code owner}'s. Read from Iron's
     * summon registry by UUID, which also works for a fake player the level cannot look up.
     */
    static int summonsOwnedBy(ServerPlayer owner, AABB box) {
        java.util.Set<java.util.UUID> owned = io.redspace.ironsspellbooks.capabilities.magic.SummonManager.getSummons(owner);
        int count = 0;
        for (var entity : owner.level().getEntities((net.minecraft.world.entity.Entity) null, box,
                e -> e instanceof io.redspace.ironsspellbooks.entity.mobs.IMagicSummon)) {
            if (owned != null && owned.contains(entity.getUUID())) count++;
        }
        return count;
    }

    /**
     * Record where Iron's sends {@code player} by a spell teleport into {@code sink[0]}, from
     * Iron's own {@code SpellTeleportEvent}, until {@link #stopWatching}. A fake player's
     * connection ignores the move itself, so the event is the observable.
     */
    static Object watchTeleports(ServerPlayer player, net.minecraft.world.phys.Vec3[] sink) {
        java.util.function.Consumer<io.redspace.ironsspellbooks.api.events.SpellTeleportEvent> listener = event -> {
            if (event.getEntity() == player) sink[0] = event.getTarget();
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, false,
            io.redspace.ironsspellbooks.api.events.SpellTeleportEvent.class, listener);
        return listener;
    }

    static void stopWatching(Object listener) {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);
    }

    /** Every enabled-or-not, non-placeholder spell in the loaded Iron's registry with its live max level. */
    static java.util.Map<ResourceLocation, Integer> playerSpells() {
        java.util.Map<ResourceLocation, Integer> spells = new java.util.TreeMap<>();
        for (AbstractSpell spell : SpellRegistry.REGISTRY.get().getValues()) {
            if (spell == SpellRegistry.none() || spell instanceof com.otectus.arsnspells.spell.irons.ArsCrossProxySpell) continue;
            spells.put(spell.getSpellResource(), spell.getMaxLevel());
        }
        return spells;
    }
}
