/**
 * The loader-neutral Ars 'n' Spells contract.
 *
 * <p>Closes the audit's cross-cutting "two loaders, two dialects" finding: every rule that
 * prices, routes, tracks, or plans a cast used to live twice - once in the Forge 1.20.1
 * source and once in the NeoForge 1.21.1 port - and the two copies drifted. This package is
 * the single copy. It is duplicated byte-for-byte into both repositories and guarded by
 * {@code contract-manifest.txt} plus {@code contract_parity.py}.
 *
 * <p>Two hard rules hold for every type here:
 *
 * <ul>
 *   <li><b>Java 17 syntax only.</b> Records and sealed types are fine. No switch pattern
 *       matching, no record deconstruction, no {@code Object} patterns, no Java 21 feature -
 *       even though the NeoForge repository compiles at 21.</li>
 *   <li><b>Zero platform imports.</b> Nothing from {@code net.minecraft},
 *       {@code net.minecraftforge}, {@code net.neoforged}, Ars Nouveau, Iron's Spellbooks,
 *       Curios, or any other mod. Where a rule needs a player or an item stack it takes an
 *       opaque handle (a {@link java.util.UUID}, a {@code String} identity) or goes through a
 *       port interface that each loader implements outside this package.</li>
 * </ul>
 *
 * <p>Everything here is pure and deterministic except {@link com.otectus.arsnspells.contract.AttemptLedger},
 * which is the one deliberately stateful type and says so in its own javadoc.
 */
package com.otectus.arsnspells.contract;
