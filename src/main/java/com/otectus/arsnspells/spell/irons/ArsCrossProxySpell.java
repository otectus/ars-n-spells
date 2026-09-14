package com.otectus.arsnspells.spell.irons;

import com.otectus.arsnspells.spell.CrossCastNbt;
import com.otectus.arsnspells.spell.CrossCastingHandler;
import com.otectus.arsnspells.util.CrossCastTrace;
import com.otectus.arsnspells.util.LogThrottle;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(ArsCrossProxySpell.class);

    /** At most one failed-cast WARN per player per this many ms. */
    private static final long STRAY_LOG_WINDOW_MS = 5_000L;

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
            // else — casting, the spell wheel and the inscription table are unaffected — so
            // this removes a nonsense craft without disabling the spell itself.
            //
            // Crafting and looting are two independent gates in Iron's, and this one does not
            // cover loot. See allowLooting() below for the other half.
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

    /**
     * Never a candidate for random loot, wandering trades or ring imbuing.
     *
     * <p>Iron's has two independent opt-out gates and this class used to set only one.
     * {@code allowCrafting=false} covers the Scroll Forge; loot goes through
     * {@code SpellFilter#isSpellAllowed}, whose entire body is
     * {@code isEnabled() && (force || allowLooting())} — it never looks at crafting. The base
     * implementation returns {@code getSchoolType().allowLooting}, and the ENDER school the
     * constructor picks for its {@code requiresLearning == false} is built with the seven-arg
     * {@code SchoolType} constructor, which hardcodes {@code allowLooting = true}.
     *
     * <p>So without this override every proxy was a live candidate in each
     * {@code irons_spellbooks:randomize_spell} roll, at {@code COMMON} — the heaviest rarity
     * weight Iron's assigns — and turned up as an uncastable scroll in vanilla chests, scroll
     * pouches, mob drops and wandering-trader stock, which is exactly what was reported
     * against 3.2.1.
     *
     * <p>Overriding the method is the only mechanism available: {@code DefaultConfig} has no
     * {@code setAllowLooting}. Switching school is not an alternative — the one school with
     * {@code allowLooting == false} is Eldritch, which also sets {@code requiresLearning =
     * true}, precisely the cast gate the ENDER choice exists to avoid. Disabling the spell is
     * not an alternative either: already-bound books resolve their wheel slots by id.
     */
    @Override
    public boolean allowLooting() {
        return false;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource,
                       MagicData playerMagicData) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer player)) {
            return;
        }
        Carrier carrier = resolveCastingBook(player, playerMagicData);
        if (carrier == null) {
            // Previously a silent return: the wheel slot selected and nothing happened,
            // with no way for a player or a log reader to tell why.
            //
            // Throttled because this branch is reachable from a stray loot scroll, not only
            // from a genuine book desync: a player clicking a dud once per tick would
            // otherwise write a WARN line per click for as long as they kept clicking.
            if (LogThrottle.allow(player.getUUID(), STRAY_LOG_WINDOW_MS)) {
                LOGGER.warn("Ars cross proxy {} cast by {} (source={}, equipmentSlot={}) but no carried "
                        + "spellbook holds a sidecar entry for pool {} — the native wheel slot is desynced "
                        + "from the {} sidecar, the book was unequipped mid-cast, or this is a stray "
                        + "scroll from before the proxies were made unlootable",
                    spellResource, player.getGameProfile().getName(), castSource,
                    playerMagicData.getCastingEquipmentSlot(), poolId, CrossCastNbt.TAG_CROSS_MOD_SPELLS);
            }
            if (neutralizeStrayCarrier(player, playerMagicData)) {
                player.displayClientMessage(
                    Component.translatable("arsnspells.crosscast.proxy.stray_scroll"), true);
            } else {
                player.displayClientMessage(
                    Component.translatable("arsnspells.crosscast.proxy.book_missing", poolId), true);
            }
            return;
        }
        ItemStack book = carrier.book();
        CompoundTag entry = carrier.entry();
        if (!entry.contains(CrossCastNbt.TAG_ARS_SPELL, Tag.TAG_COMPOUND)) {
            if (LogThrottle.allow(player.getUUID(), STRAY_LOG_WINDOW_MS)) {
                LOGGER.warn("Ars cross proxy {} cast by {} resolved book {} for pool {} but that entry "
                        + "carries no {} payload ({} Ars entries present)",
                    spellResource, player.getGameProfile().getName(),
                    ForgeRegistries.ITEMS.getKey(book.getItem()), poolId,
                    CrossCastNbt.TAG_ARS_SPELL, CrossCastNbt.countArsEntries(book.getTag()));
            }
            player.displayClientMessage(
                Component.translatable("arsnspells.crosscast.proxy.entry_missing", poolId), true);
            return;
        }
        InteractionHand hand = player.getOffhandItem() == book
            ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        UUID attemptId = UUID.randomUUID();
        CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
            CrossCastTrace.Stage.UPSTREAM_CAST_ENTER, "runtime", "ARS_PROXY", "pool", poolId);
        if (CrossCastingHandler.castArsSpell(player, book, hand, entry, attemptId)) {
            // Audit H4: the native-wheel path bypasses serverHandleCast, so the
            // advancement is granted here too (grant() is idempotent).
            com.otectus.arsnspells.util.AdvancementUtil.grant(player, "first_cross_cast");
        }
    }

    /**
     * Find the stack that actually carries this proxy's sidecar entry.
     *
     * <p>{@code MagicData.getPlayerCastingItem()} is the authoritative answer only when
     * Iron's set it for this cast; it comes back empty for a book held in the Curios
     * spellbook slot, which made every such cast a silent no-op. The equipped spellbook
     * and both hands are checked as fallbacks, and each candidate must actually hold an
     * entry for this pool id — so a player carrying two bound books can never resolve to
     * the wrong one.
     */
    private Carrier resolveCastingBook(ServerPlayer player, MagicData magicData) {
        ProxyCarrierResolver.Carrier carrier = ProxyCarrierResolver.casting(player, magicData, poolId);
        return carrier == null ? null : new Carrier(carrier.stack(), carrier.entry());
    }

    /**
     * Blank whichever carried stack fired this proxy, when it turns out to be a stray scroll
     * rather than a bound book. Returns true when something was rewritten.
     *
     * <p>Reaching {@code onCast} with no book that owns the pool id is the signature of loot
     * debris from before {@link #allowLooting()} existed. The scroll can never do anything but
     * fail this way, so the honest outcome is to blank it back to a plain Iron's scroll the
     * first time the player tries it, rather than leave a permanent dud in their inventory.
     *
     * <p>A proxy imbued into a spellbook or an enhancement ring is deliberately left alone —
     * {@link ArsCrossProxyHiding#neutralizeStrayProxyScroll} refuses anything that is not a
     * scroll. Rewriting a player's book is a gameplay-visible change that should not happen
     * silently, the same reasoning {@code CarrierReconciler} documents for its inverse case,
     * and {@link #allowLooting()} stops any of these being generated in the first place.
     */
    private boolean neutralizeStrayCarrier(ServerPlayer player, MagicData magicData) {
        return ArsCrossProxyHiding.neutralizeStrayProxyScroll(magicData.getPlayerCastingItem())
            || ArsCrossProxyHiding.neutralizeStrayProxyScroll(player.getMainHandItem())
            || ArsCrossProxyHiding.neutralizeStrayProxyScroll(player.getOffhandItem());
    }

    /** The carrier view of {@code stack}, or null when it holds no entry for this pool id. */
    private Carrier carrierOf(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return null;
        }
        CompoundTag entry = CrossCastNbt.findEntryByProxyPoolId(stack.getTag(), poolId);
        return entry == null ? null : new Carrier(stack, entry);
    }

    /** A resolved book and the sidecar entry on it that belongs to this proxy. */
    private record Carrier(ItemStack book, CompoundTag entry) {}
}
