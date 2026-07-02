package com.otectus.arsnspells.spell.irons;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.spell.CrossCastNbt;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers the finite pool of proxy spells ({@code ars_n_spells:ars_cross_1..N})
 * that let Ars Nouveau spells appear as their own entries in Iron's Spellbooks'
 * native spell-selection wheel. Each registered id is a real, legitimately
 * registered {@link AbstractSpell} — not a fake/unresolvable id — whose
 * {@code onCast} delegates to the Ars cast pipeline (see {@link ArsCrossProxySpell}).
 *
 * <p><b>Iron's-gated.</b> This class imports Iron's API types and references
 * {@link SpellRegistry#SPELL_REGISTRY_KEY}; it must only be touched when
 * {@code irons_spellbooks} is loaded. {@link ArsNSpells} calls {@link #register}
 * behind that check, and no unconditionally-loaded class references this type.
 *
 * <p>The wheel de-duplicates entries by spell id, so each Ars spell bound to one
 * book must claim a distinct pool id {@code k} in {@code [1, POOL_SIZE]}. This is
 * the hard ceiling on Ars entries visible in the native wheel per book.
 */
public final class ArsCrossProxyRegistry {
    /** Distinct proxy wheel slots per book; the native-wheel ceiling for Ars entries. */
    public static final int POOL_SIZE = CrossCastNbt.PROXY_POOL_SIZE;

    private static final String PREFIX = "ars_cross_";

    public static final DeferredRegister<AbstractSpell> SPELLS =
        DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, ArsNSpells.MODID);

    private static final List<RegistryObject<AbstractSpell>> PROXIES = new ArrayList<>(POOL_SIZE);

    static {
        for (int k = 1; k <= POOL_SIZE; k++) {
            final int poolId = k;
            PROXIES.add(SPELLS.register(spellName(poolId), () -> new ArsCrossProxySpell(poolId)));
        }
    }

    private ArsCrossProxyRegistry() {}

    public static String spellName(int poolId) {
        return PREFIX + poolId;
    }

    public static ResourceLocation spellId(int poolId) {
        return new ResourceLocation(ArsNSpells.MODID, spellName(poolId));
    }

    /** Pool id {@code k} parsed from an {@code ars_cross_k} id, or {@code -1} if not a proxy id. */
    public static int poolIdOf(ResourceLocation id) {
        if (id == null || !ArsNSpells.MODID.equals(id.getNamespace())) {
            return -1;
        }
        String path = id.getPath();
        if (!path.startsWith(PREFIX)) {
            return -1;
        }
        try {
            int k = Integer.parseInt(path.substring(PREFIX.length()));
            return (k >= 1 && k <= POOL_SIZE) ? k : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** The registered proxy spell for pool id {@code k}, or {@code null} if out of range. */
    public static AbstractSpell get(int poolId) {
        if (poolId < 1 || poolId > POOL_SIZE) {
            return null;
        }
        return PROXIES.get(poolId - 1).get();
    }

    public static void register(IEventBus modBus) {
        SPELLS.register(modBus);
    }
}
