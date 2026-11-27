#!/usr/bin/env python3
"""Compare shared contracts, config defaults/bounds, assets, recipes, translations, command
coverage and GameTest coverage between the Forge 1.20.1 and NeoForge 1.21.1 builds, in both
directions.

Every difference either fails the check or is an entry in one of the explicit exception tables
below, each with its reason: integrations whose dependency has no build for the other Minecraft
version (Covenant of the Seven and Too Many Glyphs are 1.20.1 only; Ars Affinity and Ars
Elemancy are 1.21.1 only), and loader-native storage (capabilities and NBT on Forge, data
attachments and components on NeoForge).

Since 3.4.1 the report has three parts, kept apart so intentional platform differences are
reviewed separately from source-level ones:
  behaviour    the shared behavioural fixtures (catalogs, mapping schema, effective levels,
               policies, Press terms) are byte-identical and run by both loaders' unit tests, the
               shared tool modules are identical, and both bundled catalogs publish the same
               imported-glyph identities and the same ledger. --section behaviour checks only this.
  exceptions   intentional platform differences, each counted and verified where it can be (the two
               item-id schemes must still pair one model and one name per identity).
  errors       every other difference.

Native Java behavior is verified separately by unit tests and loaded/absent GameTests.
This check deliberately does not claim that matching source inventories proves gameplay.
"""
from __future__ import annotations
import argparse
import collections
import hashlib
import json
from pathlib import Path
import re
import sys
import contract_parity

ROOT = Path(__file__).resolve().parents[1]
JAVA = Path('src/main/java/com/otectus/arsnspells')
OPTIONAL_CONFIG = {
    'ARS_LP_BASE_MULTIPLIER', 'ARS_LP_MINIMUM_COST', 'ARS_LP_TIER1_MULTIPLIER',
    'ARS_LP_TIER2_MULTIPLIER', 'ARS_LP_TIER3_MULTIPLIER', 'ARS_VIRTUE_AURA_MULTIPLIER',
    'AURA_FAILURE_MODE', 'BLASPHEMY_DISCOUNT', 'BLASPHEMY_LP_DISCOUNT',
    'BLASPHEMY_MATCHING_SCHOOL_BONUS', 'DEATH_ON_INSUFFICIENT_LP', 'ENABLE_LP_SYSTEM',
    'ENABLE_VIRTUE_AURA_SYSTEM', 'HIDE_MANA_BAR_WITH_RING', 'IRONS_LP_BASE_MULTIPLIER',
    'IRONS_LP_COMMON_MULTIPLIER', 'IRONS_LP_EPIC_MULTIPLIER', 'IRONS_LP_LEGENDARY_MULTIPLIER',
    'IRONS_LP_MINIMUM_COST', 'IRONS_LP_PER_LEVEL_MULTIPLIER', 'IRONS_LP_RARE_MULTIPLIER',
    'IRONS_LP_UNCOMMON_MULTIPLIER', 'LP_SOURCE_MODE', 'PAYMENT_OPEN_FAILURE_POLICY',
    'SHOW_LP_COST_MESSAGES',
}
OPTIONAL_LANG = ('message.ars_n_spells.lp.', 'message.ars_n_spells.aura.',
                 'message.ars_n_spells.ring_', 'message.ars_n_spells.covenant_',
                 'commands.ans.info.aura', 'commands.ans.info.cursed_ring', 'commands.ans.info.virtue_ring')
NATIVE_METADATA = {'META-INF/mods.toml', 'pack.mcmeta', 'ars_n_spells.mixins.json', 'ars_n_spells.compat.mixins.json'}
OPTIONAL_TAGS = ('data/ars_n_spells/tags/items/blasphemy', 'data/ars_n_spells/tags/items/cursed_rings.json', 'data/ars_n_spells/tags/items/virtue_rings.json')
# This table was removed in Iron's 1.21.1-3.16.3; the port also injects dead_king_vault.
REMOVED_LOOT = {'irons_spellbooks:chests/catacombs/crypt_loot'}
# Forge-only translation keys outside OPTIONAL_LANG: Covenant diagnostics.
OPTIONAL_LANG_KEYS = {'commands.ans.info.ring_bypass'}
NEO_NATIVE_METADATA = {'META-INF/neoforge.mods.toml'}
# Forge-only config that is not an unavailable integration: none.
NEO_ONLY_CONFIG = set()

# GameTests that exist on one loader only. Each maps to the other loader's equivalent test (or
# reason) so a new one-sided test fails the check until it is ported or classified.
FORGE_ONLY_GAMETESTS = {
    'covenantLoaded_hotfixRingPaymentOwnership': 'Covenant of the Seven has no 1.21.1 build',
    'tooManyGlyphs_everyGlyphResolvesWithoutThrowing': 'Too Many Glyphs has no 1.21.1 build',
    'tooManyGlyphs_glyphsRoundTrip': 'Too Many Glyphs has no 1.21.1 build',
    'mixedAddonRecipe_survivesSerialization': 'mixes Too Many Glyphs, which has no 1.21.1 build',
    'snapshotRemovesMissingAndDisabledSchools': 'Forge capability; NeoForge covers it in the AffinitySnapshotTest unit test',
    'ironsLoaded_tenCostReadsChangeNoBalance': 'tests the Forge-only quoteArsCostForEvent helper; arsNative_successAndLateVetoHaveExactPoolDeltas covers the live path on both',
    'ironsLoaded_undecodableContainer_isRepaired': 'Forge NBT container repair; components cannot hold an undecodable container',
    'ironsLoaded_delayedHitIsScaledByItsOwnCasterSchool': 'NeoForge: ironsLoaded_interleavedSchools_doNotContaminateEachOther (also run on Forge)',
    'ironsLoaded_unclassifiedDamageIsNeverScaled': 'NeoForge: ironsLoaded_environmentalDamage_isNeverTouched (also run on Forge)',
    'bindRoutes_produceIdenticalBookNbt': 'NeoForge: bindRoutes_produceIdenticalBookComponents',
    'crossCastCycle_advancesSelectedIndex': 'NeoForge: cycle_advancesSelectedIndexAndWraps',
    'ironsLoaded_bindAllocatesProxyPoolId': 'NeoForge: bindAllocatesTheSmallestFreePoolId',
    'ironsLoaded_nativeScroll_isAllowedThroughGuard': 'NeoForge: plainItem_isAllowedThroughTheInscriptionGuard',
    'ironsLoaded_nativeScroll_isNotAGhost': 'NeoForge: ironsLoaded_plainItems_areNotProxyOnlyStacks',
    'ironsLoaded_proxies_declareUnlootable': 'NeoForge: ironsLoaded_proxies_areNotLootable',
    'ironsLoaded_proxyCastWithoutSidecarEntry_isNoopWithoutCrash': 'NeoForge: proxyCastWithoutSidecarEntry_isANoopWithoutCrash',
    'ironsLoaded_realBookWithBoundEntry_isNotAGhost': 'NeoForge: ironsLoaded_aBoundBook_isNotAProxyOnlyStack',
    'ironsLoaded_unbind_removesNativeProxySlotAndSidecar': 'NeoForge: bindThenUnbind_leavesNoTrace',
    'ironsLoaded_exportBindCoexist_roundTrip': 'Forge NBT: sidecar beside the ISB_Spells container; NeoForge keeps them in separate components (bindRoutes_produceIdenticalBookComponents)',
    'ironsLoaded_legacyContainerlessCarrier_isRejectedNotCrashed': 'pre-3.0 Forge NBT carriers; no such item ever existed on NeoForge',
    'ironsLoaded_reconciler_repairsLegacyCarrierContainer': 'pre-3.0 Forge NBT carriers; no such item ever existed on NeoForge',
    'scaffoldIsWired': 'Forge GameTest harness smoke check; the NeoForge harness is proven by every other test',
    # 3.4.1
    'ironsLoaded_payment_discountSettlesExactlyButCannotRescueAdmission': 'NeoForge: ironsLoaded_nativeCostHookAppliesToTheGlyphLegOnce (the cast-event price is the one charged)',
    'ironsLoaded_press_customCatalystTagIsAdmittedByClickShiftAndHoppers': 'a Forge defect (its catalyst slot admitted only the default tag); the NeoForge slot already filters on every configured tag, and the shared fixture custom_catalyst_tag_joins_the_union covers the union on both',
    'ironsLoaded_press_unrelatedReloadRevalidatesOnceAndKeepsTheJob': 'Forge revalidates a running job once per behaviour generation; NeoForge re-checks the terms when the job completes (ironsLoaded_press_recipeChangeCancelsTheJob)',
    'ironsLoaded_press_restoredJobWaitsForIronsConfigurationThenCompletes': 'NeoForge: ironsLoaded_press_waitsForIronsConfigInsteadOfCancelling',
    'ironsLoaded_press_refillThenCancelReturnsTheReservationInFull': 'NeoForge: ironsLoaded_press_cancelReturnsEverything',
    'ironsLoaded_sessions_instantCastsReturnToBaselineAtOnce': "Forge's session indexes (owner, carriers, delays); NeoForge's token sessions: ironsLoaded_expiredSession_closesAndReturnsItsHold",
    # 3.4.1 review (2026-11-09)
    'covenantLoaded_route_cursedRingChargedGlyphPaysLpOnce': 'Covenant of the Seven has no 1.21.1 build',
    'covenantLoaded_route_cursedRingImportedGlyphPaysLpLikeANativeCast': 'Covenant of the Seven has no 1.21.1 build',
    'covenantLoaded_route_cursedRingInsufficientLpRefusesAndTakesNothing': 'Covenant of the Seven has no 1.21.1 build',
    'covenantLoaded_route_nativeFallbackPaysManaWhenLpIsShort': 'Covenant of the Seven has no 1.21.1 build',
    'covenantLoaded_route_virtueRingImportedGlyphPaysCovenantAuraNotMana': 'Covenant of the Seven has no 1.21.1 build',
    'ironsLoaded_charged_chargedTailStartsItsOwnChild': "Forge runs a charged glyph as a child in Iron's own cast slot, so a charged tail waits for that slot; NeoForge's detached charges have no shared slot (ironsLoaded_followingArsEffectRunsOnceAfterTheCharge)",
    'ironsLoaded_charged_lastInRecipeReturnsToBaselineImmediately': "a Forge defect (review CAST-02: a completed last child skipped its session close); NeoForge's sessions close with their continuation (the ironsLoaded_lifetime_* tests check the session baseline)",
    'ironsLoaded_charged_laterNativeCastOfTheSameSpellIsNotTheChild': "Forge's child shares Iron's cast slot with the player's native casts; NeoForge's detached slot never touches it (ironsLoaded_glyphDoesNotCancelAnUnrelatedNativeCast)",
    'ironsLoaded_charged_queuedTailNeverRunsAfterDeath': 'a Forge defect (review CAST-02: a queued resume could not be cancelled); NeoForge resumes through an Ars delay that re-authorizes before it runs (ironsLoaded_policy_chargeAboveALoweredMaximumRefusesAtCompletion, ironsLoaded_policy_deadEntityTargetRefusesAtCompletion, ironsLoaded_cancel_logoutAfterAPaidPulseKeepsTheCooldown)',
    'ironsLoaded_charged_queuedTailNeverRunsAfterDimensionChange': 'a Forge defect (review CAST-02: a queued resume could not be cancelled); NeoForge resumes through an Ars delay that re-authorizes before it runs (ironsLoaded_policy_chargeAboveALoweredMaximumRefusesAtCompletion, ironsLoaded_policy_deadEntityTargetRefusesAtCompletion, ironsLoaded_cancel_logoutAfterAPaidPulseKeepsTheCooldown)',
    'ironsLoaded_charged_queuedTailNeverRunsAfterLogout': 'a Forge defect (review CAST-02: a queued resume could not be cancelled); NeoForge resumes through an Ars delay that re-authorizes before it runs (ironsLoaded_policy_chargeAboveALoweredMaximumRefusesAtCompletion, ironsLoaded_policy_deadEntityTargetRefusesAtCompletion, ironsLoaded_cancel_logoutAfterAPaidPulseKeepsTheCooldown)',
    'ironsLoaded_charged_queuedTailNeverRunsWhenGlyphsAreDisabled': 'a Forge defect (review CAST-02: a queued resume could not be cancelled); NeoForge resumes through an Ars delay that re-authorizes before it runs (ironsLoaded_policy_chargeAboveALoweredMaximumRefusesAtCompletion, ironsLoaded_policy_deadEntityTargetRefusesAtCompletion, ironsLoaded_cancel_logoutAfterAPaidPulseKeepsTheCooldown)',
    'ironsLoaded_charged_queuedTailNeverRunsWhenTheTargetIsRemoved': 'a Forge defect (review CAST-02: a queued resume could not be cancelled); NeoForge resumes through an Ars delay that re-authorizes before it runs (ironsLoaded_policy_chargeAboveALoweredMaximumRefusesAtCompletion, ironsLoaded_policy_deadEntityTargetRefusesAtCompletion, ironsLoaded_cancel_logoutAfterAPaidPulseKeepsTheCooldown)',
    'ironsLoaded_charged_tailResumesAfterCompletion': 'NeoForge: ironsLoaded_followingArsEffectRunsOnceAfterTheCharge',
    'ironsLoaded_extract_nativeSpellsAreStillExtractedNatively': "Forge's table mixin replaces the native result only for an ANS entry; NeoForge's extraction guard is checked by plainItem_isAllowedThroughTheInscriptionGuard",
    'ironsLoaded_lifetime_creativeMagmaBombIsFreeForItsWholeLife': 'NeoForge: ironsLoaded_lifetime_creativeMagmaBombIsFree',
    'ironsLoaded_lifetime_magmaBombSelfIsBilledOnceInArsPrimary': 'NeoForge: ironsLoaded_lifetime_magmaBombIsBilledOnceInArsPrimary',
    'ironsLoaded_lifetime_magmaBombSelfIsBilledOnceInDisabled': 'NeoForge: ironsLoaded_lifetime_magmaBombIsBilledOnceInDisabled',
    'ironsLoaded_lifetime_magmaBombSelfIsBilledOnceInHybrid': 'NeoForge: ironsLoaded_lifetime_magmaBombIsBilledOnceInHybrid',
    'ironsLoaded_lifetime_magmaBombSelfIsBilledOnceInIssPrimary': 'NeoForge: ironsLoaded_lifetime_magmaBombIsBilledOnceInIssPrimary',
    'ironsLoaded_lifetime_magmaBombSelfIsBilledOnceInSeparate': 'NeoForge: ironsLoaded_lifetime_magmaBombIsBilledOnceInSeparate',
    'ironsLoaded_policy_chargedReleaseAboveALoweredMaximumRefuses': 'NeoForge: ironsLoaded_policy_chargeAboveALoweredMaximumRefusesAtCompletion',
    'ironsLoaded_policy_deadTargetRefusesTheDelayedEffect': 'NeoForge: ironsLoaded_policy_deadEntityTargetRefusesAtCompletion',
    'ironsLoaded_policy_disablingGlyphsStopsACharge': 'NeoForge: ironsLoaded_cancel_disablingGlyphsAfterAPaidPulseKeepsTheCooldown',
    'ironsLoaded_settings_modeChangeMidChargeNeitherDoubleBillsNorLosesTheHold': 'NeoForge: ironsLoaded_settings_modeChangeMidChargeKeepsTheAdmittedPrice',
    # 2026-11-18 audit remediation
    'covenantLoaded_route_delayedVirtueGlyphKeptRingPaysAura': 'Covenant of the Seven has no 1.21.1 build',
    'covenantLoaded_route_delayedVirtueGlyphRefusedAfterRingRemoved': 'Covenant of the Seven has no 1.21.1 build',
    'covenantLoaded_route_delayedVirtueGlyphRefusedAfterAuraDisabled': 'Covenant of the Seven has no 1.21.1 build',
    'covenantLoaded_route_delayedVirtueGlyphRefusedAfterCursedRingAdded': 'Covenant of the Seven has no 1.21.1 build',
    'covenantLoaded_route_virtueEquippedAfterManaAdmissionIsRefusedNotFree': 'Covenant of the Seven has no 1.21.1 build',
    'covenantLoaded_route_projectileVirtueGlyphRefusedAfterRingRemoved': 'Covenant of the Seven has no 1.21.1 build',
    'covenantLoaded_route_chargedVirtueGlyphRefusedWhenRingRemovedBeforeRelease': 'Covenant of the Seven has no 1.21.1 build',
}
NEO_ONLY_GAMETESTS = {
    'arsElemancy_everyGlyphResolvesWithoutThrowing': 'Ars Elemancy has no 1.20.1 build',
    'arsElemancy_profileIsComplete': 'Ars Elemancy has no 1.20.1 build',
    'arsElemancy_registersItems': 'Ars Elemancy has no 1.20.1 build',
    'affinityLoaded_manaTapRestoresTheRoutedPoolInEveryMode': 'Ars Affinity has no 1.20.1 build',
    # 3.4.1
    'ironsLoaded_channel_pulsesUseTheirEventLevel': "NeoForge's own pulse loop posts each pulse's cast event; a Forge channel pulses as Iron's native invocations in its own cast slot (ironsLoaded_lifetime_fireBreathPaysEachPulseAndNothingAfter)",
    'ironsLoaded_recast_isAdmittedWithoutItsOriginalPrice': 'a NeoForge defect; Forge admission has treated a held native recast as free since 3.4.0 (GlyphAdmission)',
    'affinityLoaded_nativeArsCastProgressesAffinity': 'Ars Affinity has no 1.20.1 build',
    'affinityLoaded_nativeIronsSpellAddsNoGlyphProgress': 'Ars Affinity has no 1.20.1 build',
    'affinityLoaded_proxyArsCastProgressesExactlyOnce': 'Ars Affinity has no 1.20.1 build',
    'bindRoutes_produceIdenticalBookComponents': 'Forge: bindRoutes_produceIdenticalBookNbt',
    'cycle_advancesSelectedIndexAndWraps': 'Forge: crossCastCycle_advancesSelectedIndex',
    'bindAllocatesTheSmallestFreePoolId': 'Forge: ironsLoaded_bindAllocatesProxyPoolId',
    'plainItem_isAllowedThroughTheInscriptionGuard': 'Forge: ironsLoaded_nativeScroll_isAllowedThroughGuard',
    'ironsLoaded_plainItems_areNotProxyOnlyStacks': 'Forge: ironsLoaded_nativeScroll_isNotAGhost',
    'ironsLoaded_proxies_areNotLootable': 'Forge: ironsLoaded_proxies_declareUnlootable',
    'proxyCastWithoutSidecarEntry_isANoopWithoutCrash': 'Forge: ironsLoaded_proxyCastWithoutSidecarEntry_isNoopWithoutCrash',
    'ironsLoaded_aBoundBook_isNotAProxyOnlyStack': 'Forge: ironsLoaded_realBookWithBoundEntry_isNotAGhost',
    'bindThenUnbind_leavesNoTrace': 'Forge: ironsLoaded_unbind_removesNativeProxySlotAndSidecar',
    # 3.4.1 review (2026-11-09)
    'ironsLoaded_budget_burstFanOutAdmitsOnePendingChargeInCreative': "a NeoForge defect (review CAST-05: pending charges took no emission permit); Forge runs one child per player in Iron's slot (child_busy)",
    'ironsLoaded_budget_burstFanOutAdmitsOnePendingChargeInSurvival': "a NeoForge defect (review CAST-05: pending charges took no emission permit); Forge runs one child per player in Iron's slot (child_busy)",
    'ironsLoaded_cancel_disablingGlyphsAfterAPaidPulseKeepsTheCooldown': "Forge: ironsLoaded_policy_disablingGlyphsStopsACharge; a cancelled Forge channel takes its cooldown from Iron's own cancellation",
    'ironsLoaded_cancel_logoutAfterAPaidPulseKeepsTheCooldown': "a NeoForge defect (review CAST-04: cancelling a channel suppressed its cooldown); a Forge channel is cancelled by Iron's own cast slot, which applies it (ironsLoaded_charged_queuedTailNeverRunsAfterLogout)",
    'ironsLoaded_failure_afterTheEffectBeganStaysPaidWithItsCooldown': "a NeoForge defect (review CAST-04: a post-effect exception was an unpaid refusal); Forge's one post-effect handler is exercised by ironsLoaded_failure_aLinkageErrorAfterTheEffectBeganIsContained",
    'ironsLoaded_failure_beforePaymentIsAnUnpaidRefusal': "the NeoForge half of review CAST-04's execution stages; Forge refuses before payment inside IronsGlyphInvoker.runInstant (ironsLoaded_shortage_refusesTheWholeCastAtomically, ironsLoaded_casterOnlyGlyph_withTouch_isRefused)",
    'ironsLoaded_failure_laterChannelPulseKeepsThePaidPulseAndTheCooldown': "NeoForge's own pulse loop; Forge's channel pulses are Iron's own native invocations (ironsLoaded_lifetime_fireBreathPaysEachPulseAndNothingAfter)",
    'ironsLoaded_lifetime_creativeMagmaBombIsFree': 'Forge: ironsLoaded_lifetime_creativeMagmaBombIsFreeForItsWholeLife',
    'ironsLoaded_lifetime_magmaBombIsBilledOnceInArsPrimary': 'Forge: ironsLoaded_lifetime_magmaBombSelfIsBilledOnceInArsPrimary',
    'ironsLoaded_lifetime_magmaBombIsBilledOnceInDisabled': 'Forge: ironsLoaded_lifetime_magmaBombSelfIsBilledOnceInDisabled',
    'ironsLoaded_lifetime_magmaBombIsBilledOnceInHybrid': 'Forge: ironsLoaded_lifetime_magmaBombSelfIsBilledOnceInHybrid',
    'ironsLoaded_lifetime_magmaBombIsBilledOnceInIssPrimary': 'Forge: ironsLoaded_lifetime_magmaBombSelfIsBilledOnceInIssPrimary',
    'ironsLoaded_lifetime_magmaBombIsBilledOnceInSeparate': 'Forge: ironsLoaded_lifetime_magmaBombSelfIsBilledOnceInSeparate',
    'ironsLoaded_policy_chargeAboveALoweredMaximumRefusesAtCompletion': 'Forge: ironsLoaded_policy_chargedReleaseAboveALoweredMaximumRefuses',
    'ironsLoaded_policy_deadEntityTargetRefusesAtCompletion': 'Forge: ironsLoaded_policy_deadTargetRefusesTheDelayedEffect',
    'ironsLoaded_policy_positionGlyphOutlivesTheEntityItHit': "NeoForge's position target kind; Forge's adapters have no separate place target",
    'ironsLoaded_settings_modeChangeMidChargeKeepsTheAdmittedPrice': 'Forge: ironsLoaded_settings_modeChangeMidChargeNeitherDoubleBillsNorLosesTheHold',
}


RESOURCES = Path('src/main/resources')
FIXTURES = Path('src/test/resources/ans_shared_fixtures')
# Each shared fixture family and the unit-test class that runs it on each loader.
FIXTURE_RUNNERS = {'mappings': 'SharedMappingFixturesTest', 'catalogs': 'SharedCatalogFixturesTest', 'levels': 'SharedLevelFixturesTest',
                   'browser': 'BrowserModelTest'}
# Tool modules both checkouts ship unchanged.
SHARED_TOOLS = ('build_catalog_packages.py', 'contract_parity.py', 'glyph_catalog_pack.py', 'test_addon_adapter.py', 'test_glyph_catalog_pack.py',
                'test_pack_presets.py', 'test_validate_glyph_mappings.py', 'test_verify_gametest_log.py',
                'test_verify_loader_parity.py', 'validate_glyph_mappings.py', 'verify_gametest_log.py', 'verify_icons.py',
                'verify_loader_parity.py')
LEDGER = 'ars_n_spells/glyph_identity_ledger.txt'
# The bundled imported-glyph catalog of each loader (compared by the identities they publish).
CATALOGS = ('ars_n_spells/iron_glyph_catalog.json', 'ans_glyph_catalog/irons_spellbooks.json')
# Each loader's imported-glyph item ids (3.4.1 A: preserved, never renamed for parity).
GLYPH_MODELS = ('assets/ars_n_spells/models/item/iron/', 'assets/ars_n_spells/models/item/glyph_iron/')
GLYPH_NAME_KEY = 'ars_n_spells.glyph_name.'


def identities(catalog: Path) -> tuple:
    """The part ids a catalog publishes, its identity-relevant facts per spell, and its adapters."""
    root = json.loads(catalog.read_text(encoding='utf-8'))
    ids, facts, adapters = set(), {}, {}
    for spell in root['spells']:
        levels = spell['levels']
        namespace, path = spell['spell'].split(':', 1)
        for level in range(levels.get('min', 1), levels['registered_max'] + 1):
            ids.add(f'ars_n_spells:iron/{namespace}/{path}/level_{level}')
        facts[spell['spell']] = {'status': spell.get('status', 'supported'), 'levels': levels, 'school': spell['school'],
                                 'cast_type': spell['cast_type'], 'adapter_version': spell.get('adapter_version', 1)}
        adapters[spell['spell']] = spell['adapter']
    return ids, facts, adapters


def ledger(path: Path) -> list:
    return [line.strip() for line in path.read_text(encoding='utf-8').splitlines() if line.strip() and not line.startswith('#')]


def tree(root: Path) -> dict:
    return {p.relative_to(root).as_posix(): p.read_bytes() for p in sorted(root.rglob('*')) if p.is_file()} if root.is_dir() else {}


def behaviour(forge: Path, neo: Path) -> dict:
    """Shared behaviour that must agree exactly; see the module comment."""
    errors = []
    # The loader-specific editor adapters share one localization vocabulary with resource packs.
    browser_labels = []
    for checkout in (forge, neo):
        locale = json.loads((checkout / 'src/main/resources/assets/ars_n_spells/lang/en_us.json').read_text(encoding='utf-8'))
        browser_labels.append({key: value for key, value in locale.items() if key.startswith('ars_n_spells.browser.')})
    if not browser_labels[0] or browser_labels[0] != browser_labels[1]:
        errors.append('The shared glyph browser labels differ between loaders')
    fa, na = tree(forge / FIXTURES), tree(neo / FIXTURES)
    for name in sorted(fa.keys() | na.keys()):
        if name not in na: errors.append('Shared fixture missing on NeoForge: ' + name)
        elif name not in fa: errors.append('Shared fixture missing on Forge: ' + name)
        elif fa[name] != na[name]: errors.append('Shared fixture differs: ' + name)
    cases = 0
    for family, runner in FIXTURE_RUNNERS.items():
        index = forge / FIXTURES / family / 'index.json'
        if not index.is_file():
            errors.append(f'Shared fixture family {family} has no index'); continue
        names = json.loads(index.read_text(encoding='utf-8'))['cases']
        listed = {f'{family}/{case}.json' for case in names} | {f'{family}/index.json'}
        for name in sorted(listed - fa.keys()): errors.append('Shared fixture listed but missing: ' + name)
        for name in sorted(n for n in fa if n.startswith(family + '/') and n not in listed):
            errors.append('Shared fixture not in its index: ' + name)
        cases += len(names)
        for root, loader in ((forge, 'Forge'), (neo, 'NeoForge')):
            if not any(f'/ans_shared_fixtures/{family}/' in source.read_text(encoding='utf-8')
                       for source in (root / 'src/test/java').rglob(runner + '.java')):
                errors.append(f'{loader} has no {runner} running ans_shared_fixtures/{family}')
    for name in SHARED_TOOLS:
        a, b = forge / 'tools' / name, neo / 'tools' / name
        if not a.is_file() or not b.is_file(): errors.append('Shared tool missing: ' + name)
        elif a.read_bytes() != b.read_bytes(): errors.append('Shared tool differs: ' + name)
    fi, ff, fadapters = identities(forge / RESOURCES / CATALOGS[0])
    ni, nf, nadapters = identities(neo / RESOURCES / CATALOGS[1])
    if fi != ni:
        errors.append(f'Catalog identities differ: {len(fi - ni)} Forge-only, {len(ni - fi)} NeoForge-only, first {sorted(fi ^ ni)[0]}')
    for spell in sorted(ff.keys() & nf.keys()):
        if ff[spell] != nf[spell]: errors.append(f'Catalog facts differ for {spell}: {ff[spell]} / {nf[spell]}')
    fl, nl = ledger(forge / RESOURCES / LEDGER), ledger(neo / RESOURCES / LEDGER)
    if fl != nl: errors.append('Identity ledgers differ')
    for root, ids, loader in ((forge, fi, 'Forge'), (neo, ni, 'NeoForge')):
        lost = sorted(set(ledger(root / RESOURCES / LEDGER)) - ids)
        if lost: errors.append(f'{loader} catalog lost {len(lost)} published identities, first {lost[0]}')
    adapter_pairs = collections.Counter((fadapters[s], nadapters[s]) for s in fadapters.keys() & nadapters.keys())
    return {'browser_labels': len(browser_labels[0]), 'shared_fixture_cases': cases, 'shared_fixture_files': len(fa), 'shared_tools': len(SHARED_TOOLS),
            'catalog_identities': len(fi), 'ledger_identities': len(fl), 'identities': fi,
            'adapter_pairs': adapter_pairs, 'errors': errors}


def glyph_identity_exceptions(forge: Path, neo: Path, ids: set) -> tuple:
    """Pair each loader's imported-glyph models and names with the catalog identities: (exceptions, errors)."""
    expected = {i[len('ars_n_spells:iron/'):] for i in ids}
    errors, exceptions = [], []
    for root, prefix, loader in ((forge, GLYPH_MODELS[0], 'Forge'), (neo, GLYPH_MODELS[1], 'NeoForge')):
        models = {p.relative_to(root / RESOURCES / prefix).as_posix()[:-len('.json')]
                  for p in (root / RESOURCES / prefix).rglob('*.json')}
        if models != expected:
            errors.append(f'{loader} imported-glyph models do not pair with the identities: {len(models - expected)} extra, '
                          f'{len(expected - models)} missing')
        else:
            exceptions.append(f'Imported-glyph item models ({loader}, {prefix}): {len(models)}, one per identity')
    names = {k[len(GLYPH_NAME_KEY) + len('iron/'):] for k in json.loads(
        (neo / RESOURCES / 'assets/ars_n_spells/lang/en_us.json').read_text(encoding='utf-8')) if k.startswith(GLYPH_NAME_KEY)}
    if names != expected:
        errors.append(f'NeoForge glyph name keys do not pair with the identities: {len(names - expected)} extra, {len(expected - names)} missing')
    else:
        exceptions.append(f'Imported-glyph names: NeoForge names its {len(names)} glyph items through lang keys, Forge from the catalog')
    return exceptions, errors


def configs(root: Path) -> dict:
    source = (root / JAVA / 'config/AnsConfig.java').read_text()
    result = {}
    pattern = r'(\w+)\s*=\s*BUILDER[\s\S]*?\.(define(?:InRange|ListAllowEmpty|List)?)\(\s*"([^"\n]+)"\s*,'
    for match in re.finditer(pattern, source):
        pos = start = match.end()
        depth, quoted, escaped, args = 1, False, False, []
        while pos < len(source) and depth:
            ch = source[pos]
            if quoted:
                if escaped: escaped = False
                elif ch == '\\': escaped = True
                elif ch == '"': quoted = False
            elif ch == '"': quoted = True
            elif ch in '([{': depth += 1
            elif ch in ')]}':
                depth -= 1
                if not depth: args.append(source[start:pos].strip())
            elif ch == ',' and depth == 1:
                args.append(source[start:pos].strip()); start = pos + 1
            pos += 1
        section = []
        for token in re.finditer(r'BUILDER\.(push|pop)\((?:"([^"\n]+)")?\)', source[:match.start()]):
            if token[1] == 'push': section.append(token[2])
            elif section: section.pop()
        # NeoForge adds input validators to several String values; defaults and bounds agree.
        count = 3 if match[2] == 'defineInRange' else 1
        result[match[1]] = {'key': '.'.join(section + [match[3]]), 'values': [re.sub(r'\s+', '', x) for x in args[:count]]}
    if not result: raise ValueError('No configuration declarations found')
    return result


def resource_path(name: str) -> str:
    parts = name.split('/')
    if len(parts) >= 3 and parts[0] == 'data':
        parts[2] = {'advancements': 'advancement', 'recipes': 'recipe', 'loot_tables': 'loot_table'}.get(parts[2], parts[2])
        if parts[1] == 'forge': parts[1] = 'neoforge'
        if len(parts) >= 4 and parts[2] == 'tags':
            parts[3] = {'items': 'item', 'blocks': 'block'}.get(parts[3], parts[3])
    return '/'.join(parts)


def canonical(value):
    if isinstance(value, str):
        return {'forge:logs/archwood': 'c:logs/archwood', 'forge:mod_loaded': 'neoforge:mod_loaded', 'forge:loot_table_id': 'neoforge:loot_table_id'}.get(value, value)
    if isinstance(value, list): return [canonical(x) for x in value]
    if not isinstance(value, dict): return value
    if value.get('type') == 'forge:conditional':
        if len(value['recipes']) != 1: raise ValueError('Unexpected conditional recipe alternatives')
        row = value['recipes'][0]
        value = dict(row['recipe'], **{'neoforge:conditions': row['conditions']})
    result = {}
    for key, item in value.items():
        if key == 'output': key = 'result'
        if key == 'reagent' and isinstance(item, list) and len(item) == 1: item = item[0]
        if key in ('result', 'icon') and isinstance(item, dict):
            item = dict(item)
            if 'item' in item: item['id'] = item.pop('item')
            if key == 'result': item.setdefault('count', 1)
        result[key] = canonical(item)
    return result


def gametests(root: Path) -> set:
    names = set()
    for source in (root / JAVA / 'gametest').glob('*.java'):
        names.update(re.findall(r'@GameTest[^\n]*\n(?:\s*@[^\n]*\n)*\s*public\s+static\s+void\s+(\w+)',
                                source.read_text()))
    return names


def audit(forge: Path, neo: Path) -> dict:
    errors, exceptions = [], []
    shared = behaviour(forge, neo)
    identity_exceptions, identity_errors = glyph_identity_exceptions(forge, neo, shared['identities'])
    exceptions.extend(identity_exceptions)
    errors.extend(identity_errors)
    exceptions.append(f'Imported-glyph catalogs ({CATALOGS[0]}, {CATALOGS[1]}): compared by the {shared["catalog_identities"]} identities they publish')
    for (forge_adapter, neo_adapter), count in sorted(shared['adapter_pairs'].items()):
        if forge_adapter != neo_adapter:
            exceptions.append(f'Loader-native imported-glyph adapter: {count} spell(s) {forge_adapter} on Forge, {neo_adapter} on NeoForge')
    a, b = contract_parity.inventory(forge), contract_parity.inventory(neo)
    for name in sorted(a.keys() | b.keys()):
        if a.get(name) != b.get(name): errors.append('Contract mismatch: ' + name)
    fc, nc = configs(forge), configs(neo)
    for field, declaration in fc.items():
        if field in OPTIONAL_CONFIG:
            exceptions.append('Unavailable integration config: ' + field)
        elif field in {'CONFIG_SCHEMA_VERSION', 'CONVERSION_POLICY'}:
            if nc.get(field, {}).get('values') != declaration['values']: errors.append('Config default/bounds mismatch: ' + field)
            exceptions.append('Preserved existing NeoForge config category: ' + field)
        elif nc.get(field) != declaration: errors.append('Config mismatch: ' + field + ' ' + str((declaration, nc.get(field))))
    resources = 0
    for source in sorted((forge / 'src/main/resources').rglob('*')):
        if not source.is_file(): continue
        name = source.relative_to(forge / 'src/main/resources').as_posix()
        if name.startswith(GLYPH_MODELS[0]) or name == CATALOGS[0]: continue
        if name in NATIVE_METADATA or name.startswith(OPTIONAL_TAGS):
            exceptions.append('Native metadata or unavailable integration: ' + name); continue
        target = neo / 'src/main/resources' / resource_path(name)
        if not target.is_file(): errors.append('Missing resource: ' + name); continue
        if name.endswith('/lang/en_us.json'):
            fl, nl = json.loads(source.read_text()), json.loads(target.read_text())
            for key in fl:
                if key not in nl and not key.startswith(OPTIONAL_LANG) and key not in OPTIONAL_LANG_KEYS:
                    errors.append('Missing translation: ' + key)
        elif name.endswith('.json'):
            fv, nv = canonical(json.loads(source.read_text())), canonical(json.loads(target.read_text()))
            if name.endswith('blank_scroll_irons.json'):
                def tables(value): return next(c for c in value['conditions'] if c['condition'] == 'minecraft:any_of')['terms']
                ft, nt = tables(fv), tables(nv)
                required = {t['loot_table_id'] for t in ft} - REMOVED_LOOT
                if not required.issubset({t['loot_table_id'] for t in nt}): errors.append('Missing supported Iron\'s chest loot injection')
                ft.clear(); nt.clear()
                exceptions.append('Removed native loot table: ' + ', '.join(sorted(REMOVED_LOOT)))
            if fv != nv: errors.append('Resource semantics mismatch: ' + name)
        elif source.read_bytes() != target.read_bytes(): errors.append('Asset mismatch: ' + name)
        resources += 1
    for name in ('ArsNSpellsCommands.java', 'AuditDiagnosticsCommands.java'):
        def literals(root):
            text = (root / JAVA / 'commands' / name).read_text()
            return set(re.findall(r'(?:Commands\.)?literal\("([a-z_]+)"\)', text))
        missing = literals(forge) - literals(neo) - {'aura'}
        if missing: errors.append('Missing command literals: ' + ', '.join(sorted(missing)))
    # ---- NeoForge -> Forge direction ----
    for field in sorted(nc.keys() - fc.keys() - NEO_ONLY_CONFIG):
        errors.append('NeoForge-only config field: ' + field)
    forge_resources = {resource_path(p.relative_to(forge / 'src/main/resources').as_posix())
                       for p in (forge / 'src/main/resources').rglob('*') if p.is_file()}
    for target in sorted((neo / 'src/main/resources').rglob('*')):
        if not target.is_file(): continue
        name = target.relative_to(neo / 'src/main/resources').as_posix()
        if name in NEO_NATIVE_METADATA:
            exceptions.append('Native metadata: ' + name); continue
        if name.startswith(GLYPH_MODELS[1]) or name == CATALOGS[1]: continue
        if name not in forge_resources: errors.append('NeoForge-only resource: ' + name)
    fl = json.loads((forge / 'src/main/resources/assets/ars_n_spells/lang/en_us.json').read_text())
    nl = json.loads((neo / 'src/main/resources/assets/ars_n_spells/lang/en_us.json').read_text())
    for key in nl:
        if key.startswith(GLYPH_NAME_KEY): continue
        if key not in fl: errors.append('NeoForge-only translation: ' + key)
        elif fl[key] != nl[key]: errors.append('Translation text differs: ' + key)
    for key in fl:
        if key not in nl and (key.startswith(OPTIONAL_LANG) or key in OPTIONAL_LANG_KEYS):
            exceptions.append('Unavailable integration translation: ' + key)
    for name in ('ArsNSpellsCommands.java', 'AuditDiagnosticsCommands.java'):
        text = lambda root: (root / JAVA / 'commands' / name).read_text()
        extra = (set(re.findall(r'(?:Commands\.)?literal\("([a-z_]+)"\)', text(neo)))
                 - set(re.findall(r'(?:Commands\.)?literal\("([a-z_]+)"\)', text(forge))))
        if extra: errors.append('NeoForge-only command literals: ' + ', '.join(sorted(extra)))
    ft, nt = gametests(forge), gametests(neo)
    for name in sorted(ft - nt):
        if name in FORGE_ONLY_GAMETESTS: exceptions.append('Forge-only GameTest ' + name + ': ' + FORGE_ONLY_GAMETESTS[name])
        else: errors.append('Forge-only GameTest: ' + name)
    for name in sorted(nt - ft):
        if name in NEO_ONLY_GAMETESTS: exceptions.append('NeoForge-only GameTest ' + name + ': ' + NEO_ONLY_GAMETESTS[name])
        else: errors.append('NeoForge-only GameTest: ' + name)
    for table, present in ((FORGE_ONLY_GAMETESTS, ft - nt), (NEO_ONLY_GAMETESTS, nt - ft)):
        for stale in sorted(table.keys() - present):
            errors.append('Stale GameTest exception (now on both loaders or gone): ' + stale)
    versions = []
    for root in (forge, neo):
        versions.append(re.search(r'^mod_version=(.*)$', (root / 'gradle.properties').read_text(), re.M)[1])
    if versions[0] != versions[1]: errors.append('Mod version mismatch')
    return {'mod_version': versions[1], 'shared_contract_files': len(a), 'shared_config_fields': len(fc.keys() - OPTIONAL_CONFIG),
            'resources_compared': resources, 'neo_extensions': sorted(nc.keys() - fc.keys()),
            'shared_gametests': len(ft & nt),
            'behaviour': summary(shared), 'exceptions': exceptions, 'errors': errors}


def summary(shared: dict) -> dict:
    return {k: v for k, v in shared.items() if k not in ('identities', 'adapter_pairs')}


def is_neoforge(root: Path) -> bool:
    return (root / 'src/main/resources/META-INF/neoforge.mods.toml').is_file()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    other = parser.add_mutually_exclusive_group(required=True)
    other.add_argument('--forge', type=Path, help='the Forge 1.20.1 checkout (run from the NeoForge one)')
    other.add_argument('--neoforge', type=Path, help='the NeoForge 1.21.1 checkout (run from the Forge one)')
    parser.add_argument('--report', type=Path)
    parser.add_argument('--section', choices=('all', 'behaviour'), default='all',
                        help='behaviour: only the shared behavioural fixtures, tools, catalog identities and ledger')
    args = parser.parse_args()
    # The same file ships in both checkouts; ROOT is whichever one it is run from.
    if args.forge is not None:
        forge, neo = args.forge.resolve(), ROOT
    else:
        forge, neo = ROOT, args.neoforge.resolve()
    if is_neoforge(forge) or not is_neoforge(neo):
        parser.error('expected one Forge checkout and one NeoForge checkout')
    if args.section == 'behaviour':
        report = summary(behaviour(forge, neo))
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(json.dumps(report, indent=2) + '\n')
        if report['errors']:
            print('\n'.join(report['errors']), file=sys.stderr); return 1
        print(f"PASS (behaviour): {report['shared_fixture_cases']} shared fixture cases in {report['shared_fixture_files']} identical files, "
              f"{report['shared_tools']} identical tool modules, {report['browser_labels']} browser labels, {report['catalog_identities']} catalog identities and "
              f"{report['ledger_identities']} ledger ids agree")
        return 0
    report = audit(forge, neo)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + '\n')
    behaviour_errors = report['behaviour']['errors']
    report['errors'] = behaviour_errors + report['errors']
    if args.report:
        args.report.write_text(json.dumps(report, indent=2) + '\n')
    if report['errors']:
        print('\n'.join(report['errors']), file=sys.stderr)
        print(f"FAIL: shared behaviour {'PASS' if not behaviour_errors else f'{len(behaviour_errors)} error(s)'}; "
              f"{len(report['exceptions'])} reviewed platform exceptions; {len(report['errors']) - len(behaviour_errors)} "
              "source-level differences", file=sys.stderr)
        return 1
    print(f"PASS: {report['shared_contract_files']} contract files, {report['shared_config_fields']} config fields, {report['resources_compared']} resources, {report['shared_gametests']} shared GameTests, both directions; native gameplay requires separate verification")
    return 0

if __name__ == '__main__': raise SystemExit(main())
