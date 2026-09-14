package com.otectus.arsnspells.spell;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Pure-helper faÃ§ade over the {@link ModDataComponents#CROSS_SPELLS}
 * data component. Replaces the 1.20.1-era {@code CrossCastNbt} class that
 * worked directly on {@link CompoundTag}. Same operational shape (mutate
 * the cross-cast payload in place), now via component reads/writes.
 */
public final class CrossModSpellComponents {
    private CrossModSpellComponents() {}

    /** Sentinel meaning "no proxy pool slot allocated for this entry". */
    public static final int NO_PROXY_POOL_ID = -1;

    /**
     * Number of distinct native-wheel proxy slots per Iron's spellbook. Declared
     * here (Iron's-free) so binding/allocation logic and unit tests can reference
     * it without classloading the Iron's-gated {@code ArsCrossProxyRegistry}. The
     * registry's {@code POOL_SIZE} mirrors this value.
     */
    public static final int PROXY_POOL_SIZE = 8;

    /** Registry-path prefix shared by every {@code ars_cross_k} proxy spell. */
    public static final String PROXY_ID_PATH_PREFIX = "ars_cross_";

    /**
     * Fully-qualified id prefix of the proxy spells. Declared here (Iron's-free) so
     * the Iron's event handlers can recognize a proxy cast from the event's spell-id
     * string without classloading {@code ArsCrossProxyRegistry}.
     */
    public static final String PROXY_SPELL_ID_PREFIX = "ars_n_spells:" + PROXY_ID_PATH_PREFIX;

    /**
     * True when {@code spellId} names one of ANS's own native-wheel proxy spells.
     *
     * <p>Iron's-side accounting (affinity, cooldown, LP, progression, mana) must skip
     * these: the proxy is a zero-cost ENDER-school placeholder whose real cost, school
     * and cooldown are owned by the delegated Ars cast. Charging Iron's rules against
     * the placeholder double-bills the player and attributes the cast to the wrong school.
     */
    /**
     * Current schema for ANS-written item data. Bump when the shape of what this class writes
     * changes in a way a migration has to notice.
     */
    public static final int SCHEMA_VERSION = 1;

    /** The schema an item was last stamped with, or 0 if it has never been stamped. */
    public static int schemaVersion(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        Integer stamped = stack.get(ModDataComponents.SCHEMA_VERSION.get());
        return stamped == null ? 0 : stamped;
    }

    /** Record that this item has been looked over by the current build. */
    public static void stampSchemaVersion(ItemStack stack) {
        if (stack == null || stack.isEmpty() || schemaVersion(stack) > SCHEMA_VERSION) {
            return;
        }
        stack.set(ModDataComponents.SCHEMA_VERSION.get(), SCHEMA_VERSION);
    }

    public static boolean isArsCrossProxyId(String spellId) {
        return spellId != null && spellId.startsWith(PROXY_SPELL_ID_PREFIX);
    }

    /**
     * Canonical symbol keys the Spell Loom can stamp into an entry's icon field.
     * Each maps to a shipped {@code textures/gui/icons/spell/icon_<key>.png}; the
     * wheel-icon mixin only honors keys in this set so unknown component data can
     * never resolve to a missing texture.
     */
    public static final List<String> ICON_SYMBOLS = List.of(
        "spark", "flame", "leaf", "bolt", "star", "eye", "drop", "moon");

    /**
     * Canonical nature keys ({@code textures/gui/icons/spell/nature_<key>.png} +
     * {@code ars_n_spells.nature.<key>} lang entries). The loom screen cycles
     * these; the export payload rejects anything else so hand-crafted packets
     * cannot stamp data that resolves to a missing texture.
     */
    public static final List<String> NATURE_KEYS = com.otectus.arsnspells.icons.IconCatalog.BACKGROUNDS;

    /**
     * Placeholder spell id shared by every Ars-typed entry (the real payload
     * lives in {@code arsSpellTag}). Dedup must never key on it.
     */
    public static final ResourceLocation ARS_PLACEHOLDER_ID =
        ResourceLocation.fromNamespaceAndPath("ars_nouveau", "spell");

    /** True when the stack carries at least one inscribed spell. */
    public static boolean has(ItemStack stack) {
        CrossModSpellList list = stack.get(ModDataComponents.CROSS_SPELLS.get());
        return list != null && !list.isEmpty();
    }

    /** Read the inscribed-spell payload, never null. */
    public static CrossModSpellList get(ItemStack stack) {
        CrossModSpellList list = stack.get(ModDataComponents.CROSS_SPELLS.get());
        return list != null ? list : CrossModSpellList.EMPTY;
    }

    /** Drop every inscription artifact from the stack. */
    public static void clear(ItemStack stack) {
        removeInscription(stack);
    }

    /** Low-level sidecar operation for reconciliation that already removed native slots. */
    public static void clearPayloadOnly(ItemStack stack) {
        stack.remove(ModDataComponents.CROSS_SPELLS.get());
    }

    /** Public player operation: native proxies, schema, selection and export metadata together. */
    public static void removeInscription(ItemStack stack) {
        IronsBookBindingUtil.removeAllArsEntries(stack);
    }

    /**
     * Append an entry. Mirrors the legacy
     * {@code CrossModSpellComponents.addCrossModSpell} contract: spellId may be
     * null for Ars-spell entries that carry data only in {@code arsSpellTag}.
     */
    public static void addCrossModSpell(ItemStack stack,
                                        @Nullable ResourceLocation spellId,
                                        int spellLevel,
                                        @Nullable CrossSpellType type,
                                        @Nullable CompoundTag arsSpellTag) {
        if (schemaVersion(stack) > SCHEMA_VERSION) return;
        CrossModSpellList existing = get(stack);
        ResourceLocation safeId = spellId != null ? spellId : ARS_PLACEHOLDER_ID;
        String typeName = type != null ? type.name() : CrossSpellType.ARS_NOUVEAU.name();
        CrossModSpell entry = new CrossModSpell(
            safeId,
            spellLevel,
            typeName,
            Optional.ofNullable(arsSpellTag),
            Optional.empty()
        );
        List<CrossModSpell> next = new ArrayList<>(existing.spells());
        next.add(entry);
        stack.set(ModDataComponents.CROSS_SPELLS.get(),
            new CrossModSpellList(List.copyOf(next), existing.selectedIndex(), existing.unknownFields()));
        stampSchemaVersion(stack);
    }

    /** Replace just the selected index, preserving the spell list. */
    public static void setSelectedIndex(ItemStack stack, int index) {
        if (schemaVersion(stack) > SCHEMA_VERSION) return;
        CrossModSpellList existing = get(stack);
        if (existing.isEmpty()) return;
        int clamped = Math.max(0, Math.min(index, existing.size() - 1));
        stack.set(ModDataComponents.CROSS_SPELLS.get(),
            new CrossModSpellList(existing.spells(), clamped, existing.unknownFields()));
    }

    // ------------------------------------------------------------------
    // 3.0.0 native-proxy pool helpers. The list-level overloads are pure
    // (no ItemStack/registry bootstrap) so they carry the unit tests; the
    // ItemStack overloads are thin wrappers.
    // ------------------------------------------------------------------

    /**
     * Append an Ars entry carrying the 3.0.0 native-proxy metadata. Fields are
     * only written when meaningful ({@code NO_PROXY_POOL_ID} / null / empty are
     * normalized to absent) so legacy behaviour is preserved for plain entries.
     * Returns the new list.
     */
    public static CrossModSpellList withArsEntry(CrossModSpellList list,
                                                 @Nullable ResourceLocation spellId,
                                                 int spellLevel,
                                                 @Nullable CompoundTag arsSpellTag,
                                                 int proxyPoolId,
                                                 @Nullable String customName,
                                                 @Nullable String nature,
                                                 @Nullable String iconSymbol) {
        CrossModSpell entry = new CrossModSpell(
            spellId != null ? spellId : ARS_PLACEHOLDER_ID,
            spellLevel,
            CrossSpellType.ARS_NOUVEAU.name(),
            Optional.ofNullable(arsSpellTag),
            Optional.empty(),
            proxyPoolId,
            optionalOfNonEmpty(customName),
            optionalOfNonEmpty(nature),
            optionalOfNonEmpty(iconSymbol)
        );
        List<CrossModSpell> next = new ArrayList<>(list.spells());
        next.add(entry);
        return new CrossModSpellList(List.copyOf(next), list.selectedIndex(), list.unknownFields());
    }

    /**
     * Append an Ars entry with proxy metadata to the stack's component.
     * Returns the index of the appended entry.
     */
    public static int addArsEntryWithMeta(ItemStack stack,
                                          @Nullable ResourceLocation spellId,
                                          int spellLevel,
                                          @Nullable CompoundTag arsSpellTag,
                                          int proxyPoolId,
                                          @Nullable String customName,
                                          @Nullable String nature,
                                          @Nullable String iconSymbol) {
        if (schemaVersion(stack) > SCHEMA_VERSION) return -1;
        CrossModSpellList next = withArsEntry(get(stack), spellId, spellLevel, arsSpellTag,
            proxyPoolId, customName, nature, iconSymbol);
        stack.set(ModDataComponents.CROSS_SPELLS.get(), next);
        stampSchemaVersion(stack);
        return next.size() - 1;
    }

    /** The set of proxy pool ids currently in use across the list's entries. */
    public static Set<Integer> usedProxyPoolIds(CrossModSpellList list) {
        Set<Integer> used = new HashSet<>();
        for (CrossModSpell entry : list.spells()) {
            if (entry.hasProxyPool()) {
                used.add(entry.proxyPoolId());
            }
        }
        return used;
    }

    /**
     * Smallest free proxy pool id in {@code [1, maxPool]}, or
     * {@link #NO_PROXY_POOL_ID} when all {@code maxPool} ids are taken. The
     * wheel de-duplicates by spell id, so every entry on one book must own a
     * distinct id.
     */
    public static int allocateProxyPoolId(CrossModSpellList list, int maxPool) {
        Set<Integer> used = usedProxyPoolIds(list);
        for (int k = 1; k <= maxPool; k++) {
            if (!used.contains(k)) {
                return k;
            }
        }
        return NO_PROXY_POOL_ID;
    }

    /**
     * The entry whose proxy pool id equals {@code poolId}. Used by the proxy
     * spell's cast delegation to map a wheel slot back to its Ars payload.
     */
    public static Optional<CrossModSpell> findEntryByProxyPoolId(CrossModSpellList list, int poolId) {
        if (poolId == NO_PROXY_POOL_ID) return Optional.empty();
        for (CrossModSpell entry : list.spells()) {
            if (entry.proxyPoolId() == poolId) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /** Stack overload of {@link #findEntryByProxyPoolId(CrossModSpellList, int)}. */
    public static Optional<CrossModSpell> findEntryByProxyPoolId(ItemStack stack, int poolId) {
        return findEntryByProxyPoolId(get(stack), poolId);
    }

    /**
     * Drop the entry owning {@code poolId} from the list, returning the new list.
     * Returns the input unchanged when no entry owns that id.
     *
     * <p>Used for two things (3.0.3 / 3.1.0): rolling the sidecar back when the
     * native container write fails midway through binding, and tearing an entry
     * down on unbind. Teardown must remove the native slot <em>before</em> calling
     * this â€” clearing the sidecar first leaves an orphan slot in Iron's wheel with
     * nothing to resolve.
     */
    public static CrossModSpellList withoutProxyPoolId(CrossModSpellList list, int poolId) {
        if (poolId == NO_PROXY_POOL_ID || list.isEmpty()) {
            return list;
        }
        List<CrossModSpell> next = new ArrayList<>(list.spells().size());
        boolean removed = false;
        for (CrossModSpell entry : list.spells()) {
            if (!removed && entry.proxyPoolId() == poolId) {
                removed = true;
                continue;
            }
            next.add(entry);
        }
        if (!removed) {
            return list;
        }
        if (next.isEmpty()) {
            return CrossModSpellList.EMPTY;
        }
        // Keep the cycle index in range after the shrink.
        int index = Math.max(0, Math.min(list.selectedIndex(), next.size() - 1));
        return new CrossModSpellList(List.copyOf(next), index, list.unknownFields());
    }

    /**
     * Stack overload of {@link #withoutProxyPoolId(CrossModSpellList, int)}. Removes
     * the component outright once the last entry goes, so an emptied carrier is
     * byte-identical to a never-inscribed one.
     *
     * @return true when an entry was actually removed
     */
    public static boolean removeEntryByProxyPoolId(ItemStack stack, int poolId) {
        CrossModSpellList current = get(stack);
        CrossModSpellList next = withoutProxyPoolId(current, poolId);
        if (next == current) {
            return false;
        }
        if (next.isEmpty()) {
            clear(stack);
        } else {
            stack.set(ModDataComponents.CROSS_SPELLS.get(), next);
        }
        return true;
    }

    /** Count of Ars-type entries (those carrying an {@code arsSpellTag} payload). */
    public static int countArsEntries(CrossModSpellList list) {
        int count = 0;
        for (CrossModSpell entry : list.spells()) {
            if (entry.arsSpellTag().isPresent()) {
                count++;
            }
        }
        return count;
    }

    /**
     * True when the list already carries an Ars entry with the same serialized
     * spell payload. Dedup keys on the {@code arsSpellTag} blob â€” never on the
     * shared {@link #ARS_PLACEHOLDER_ID} â€” so two different Ars spells on one
     * book are never treated as duplicates.
     */
    public static boolean containsEquivalentArsSpell(CrossModSpellList list, @Nullable CompoundTag arsSpellTag) {
        if (arsSpellTag == null) return false;
        for (CrossModSpell entry : list.spells()) {
            if (entry.arsSpellTag().isPresent() && arsSpellTag.equals(entry.arsSpellTag().get())) {
                return true;
            }
        }
        return false;
    }

    private static Optional<String> optionalOfNonEmpty(@Nullable String value) {
        return (value == null || value.isEmpty()) ? Optional.empty() : Optional.of(value);
    }
}
