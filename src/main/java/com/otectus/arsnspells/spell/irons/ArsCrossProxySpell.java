package com.otectus.arsnspells.spell.irons;

import com.otectus.arsnspells.spell.CrossCastingHandler;
import com.otectus.arsnspells.spell.CrossModSpell;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import com.otectus.arsnspells.util.AdvancementUtil;
import com.otectus.arsnspells.util.CrossCastTrace;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A registered Iron's Spellbooks proxy spell that carries no effect of its own:
 * its {@link #onCast} reads the Ars spell payload from the casting book's
 * cross-cast sidecar component (matched by this proxy's pool id) and delegates
 * to {@link CrossCastingHandler#castArsSpell}, so the real Ars spell runs
 * through Ars 'n Spells' existing cross-cast cost / multiplier / scaling /
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
    private static final Logger LOGGER = LoggerFactory.getLogger(ArsCrossProxySpell.class);

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
            // A proxy is meaningless outside a book that carries the matching sidecar entry,
            // so it must never be craftable as a standalone scroll. Iron's gates the Scroll
            // Forge (and the Scroll Forge recipes it feeds to JEI) on this flag and nothing
            // else - casting, the spell wheel and the inscription table are unaffected - so
            // this removes a nonsense craft without disabling the spell itself.
            .setAllowCrafting(false)
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
        Carrier carrier = resolveCastingBook(player, playerMagicData);
        if (carrier == null) {
            // Previously a silent return: the wheel slot selected and nothing happened, with
            // no way for a player or a log reader to tell why.
            LOGGER.warn("Ars cross proxy pool {} cast by {} (source={}) but no carried spellbook "
                    + "holds a sidecar entry for it - the native wheel slot is desynced from the "
                    + "cross-cast component, or the book was unequipped mid-cast",
                poolId, player.getGameProfile().getName(), castSource);
            player.displayClientMessage(
                Component.translatable("message.ars_n_spells.crosscast.proxy.book_missing", poolId), true);
            return;
        }
        if (carrier.entry().arsSpellTag().isEmpty()) {
            LOGGER.warn("Ars cross proxy pool {} cast by {} resolved a book but that entry carries "
                    + "no Ars payload", poolId, player.getGameProfile().getName());
            player.displayClientMessage(
                Component.translatable("message.ars_n_spells.crosscast.proxy.entry_missing", poolId), true);
            return;
        }
        UUID attemptId = UUID.randomUUID();
        CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
            CrossCastTrace.Stage.UPSTREAM_CAST_ENTER, "runtime", "ARS_PROXY", "pool", poolId);
        if (CrossCastingHandler.castArsSpell(player, carrier.book(), carrier.entry(), attemptId)) {
            // Audit H4: the native-wheel path bypasses serverHandleCast, so the advancement is
            // granted here too (grant() is idempotent).
            AdvancementUtil.grant(player, "first_cross_cast");
        }
    }

    /**
     * Find the stack that actually carries this proxy's sidecar entry.
     *
     * <p>{@code MagicData.getPlayerCastingItem()} is the authoritative answer only when Iron's
     * set it for this cast; it comes back empty for a book held in the Curios spellbook slot,
     * which made every such cast a silent no-op (3.0.3). The equipped spellbook and both hands
     * are checked as fallbacks, and each candidate must actually hold an entry for this pool
     * id - so a player carrying two bound books can never resolve to the wrong one.
     */
    private Carrier resolveCastingBook(ServerPlayer player, MagicData magicData) {
        Carrier carrier = carrierOf(magicData.getPlayerCastingItem());
        if (carrier != null) {
            return carrier;
        }
        carrier = carrierOf(Utils.getPlayerSpellbookStack(player));
        if (carrier != null) {
            return carrier;
        }
        carrier = carrierOf(player.getMainHandItem());
        return carrier != null ? carrier : carrierOf(player.getOffhandItem());
    }

    /** The carrier view of {@code stack}, or null when it holds no entry for this pool id. */
    private Carrier carrierOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Optional<CrossModSpell> entry = CrossModSpellComponents.findEntryByProxyPoolId(stack, poolId);
        return entry.map(e -> new Carrier(stack, e)).orElse(null);
    }

    /** A resolved book and the sidecar entry on it that belongs to this proxy. */
    private record Carrier(ItemStack book, CrossModSpell entry) {}
}
