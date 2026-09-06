package com.otectus.arsnspells.contract;

/**
 * Turns a loader-neutral modifier key from {@link AnsModifierIds} into the loader's own
 * identity type (audit V14).
 *
 * <p>Closes the finding that the modifier identities were duplicated as literals in both repos
 * because the two loaders disagree on the type: Forge 1.20.1 identifies an
 * {@code AttributeModifier} by {@code UUID}, NeoForge 1.21.1 by {@code ResourceLocation}. The
 * contract owns the keys as strings and each loader owns exactly one implementation of this
 * interface, so a key added here reaches both repos without a second literal list.
 *
 * @param <K> the loader's modifier identity type
 */
public interface ModifierKeyMapper<K> {

    /**
     * Map a contract key to the loader's identity.
     *
     * @param key a key from {@link AnsModifierIds}, current or legacy
     * @return the loader identity, or {@code null} if this key has no representation on this
     *         loader (a legacy {@code UUID} key means nothing to a {@code ResourceLocation}
     *         loader, and vice versa)
     */
    K map(String key);
}
