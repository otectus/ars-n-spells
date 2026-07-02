package com.otectus.arsnspells.spell.irons;

import com.otectus.arsnspells.spell.CrossCastNbt;
import com.otectus.arsnspells.spell.CrossCastingHandler;
import com.otectus.arsnspells.util.CrossCastTrace;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * A registered Iron's Spellbooks proxy spell that carries no effect of its own:
 * its {@link #onCast} reads the Ars spell payload from the casting book's
 * {@code arsnspells:cross_spells} sidecar (matched by this proxy's pool id) and
 * delegates to {@link CrossCastingHandler#castArsSpell}, so the real Ars spell
 * runs through Ars 'n Spells' existing cross-cast cost / multiplier / scaling /
 * cooldown pipeline.
 *
 * <p><b>Cost is charged exactly once.</b> {@link #getManaCost} returns 0, so
 * Iron's deducts nothing for the proxy itself, and at {@code SpellOnCastEvent}
 * time there is no {@code CrossCastContext} yet (it is opened inside the
 * delegated cast), so {@code CrossCastIronsHandler} adds nothing either. The true
 * cost is taken by the delegated Ars cast via {@code onArsSpellCost}.
 *
 * <p><b>Iron's-gated.</b> Only loaded when {@code irons_spellbooks} is present
 * (constructed by {@link ArsCrossProxyRegistry}).
 */
public class ArsCrossProxySpell extends AbstractSpell {
    private final int poolId;
    private final ResourceLocation spellResource;
    private final DefaultConfig defaultConfig;

    public ArsCrossProxySpell(int poolId) {
        this.poolId = poolId;
        this.spellResource = ArsCrossProxyRegistry.spellId(poolId);
        // No intrinsic cost/power: the delegated Ars cast owns all of that.
        this.baseManaCost = 0;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        // ENDER school => requiresLearning == false, so a programmatically-added
        // proxy slot is never blocked by Iron's "not learned" cast gate.
        this.defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.COMMON)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(0.0)
            .build();
    }

    public int getPoolId() {
        return poolId;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return spellResource;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.INSTANT;
    }

    @Override
    public int getManaCost(int spellLevel) {
        // Real cost is charged by the delegated Ars cast (onArsSpellCost). The
        // proxy must be free to Iron's so the player is never double-charged.
        return 0;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource,
                       MagicData playerMagicData) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer player)) {
            return;
        }
        ItemStack book = playerMagicData.getPlayerCastingItem();
        if (book == null || book.isEmpty() || !book.hasTag()) {
            return;
        }
        CompoundTag entry = CrossCastNbt.findEntryByProxyPoolId(book.getTag(), poolId);
        if (entry == null || !entry.contains(CrossCastNbt.TAG_ARS_SPELL, Tag.TAG_COMPOUND)) {
            return;
        }
        InteractionHand hand = player.getOffhandItem() == book
            ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        UUID attemptId = UUID.randomUUID();
        CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
            CrossCastTrace.Stage.UPSTREAM_CAST_ENTER, "runtime", "ARS_PROXY", "pool", poolId);
        CrossCastingHandler.castArsSpell(player, book, hand, entry, attemptId);
    }
}
