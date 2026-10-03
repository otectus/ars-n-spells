#!/usr/bin/env python3
"""Compare shared contracts, config defaults/bounds, assets, recipes, translations, command
coverage and GameTest coverage between the Forge 1.20.1 and NeoForge 1.21.1 builds, in both
directions.

Every difference either fails the check or is an entry in one of the explicit exception tables
below, each with its reason: integrations whose dependency has no build for the other Minecraft
version (Covenant of the Seven and Too Many Glyphs are 1.20.1 only; Ars Affinity and Ars
Elemancy are 1.21.1 only), and loader-native storage (capabilities and NBT on Forge, data
attachments and components on NeoForge).

Native Java behavior is verified separately by unit tests and loaded/absent GameTests.
This check deliberately does not claim that matching source inventories proves gameplay.
"""
from __future__ import annotations
import argparse
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
}
NEO_ONLY_GAMETESTS = {
    'arsElemancy_everyGlyphResolvesWithoutThrowing': 'Ars Elemancy has no 1.20.1 build',
    'arsElemancy_profileIsComplete': 'Ars Elemancy has no 1.20.1 build',
    'arsElemancy_registersItems': 'Ars Elemancy has no 1.20.1 build',
    'affinityLoaded_manaTapRestoresTheRoutedPoolInEveryMode': 'Ars Affinity has no 1.20.1 build',
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
}


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
        if name not in forge_resources: errors.append('NeoForge-only resource: ' + name)
    fl = json.loads((forge / 'src/main/resources/assets/ars_n_spells/lang/en_us.json').read_text())
    nl = json.loads((neo / 'src/main/resources/assets/ars_n_spells/lang/en_us.json').read_text())
    for key in nl:
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
            'exceptions': exceptions, 'errors': errors}


def is_neoforge(root: Path) -> bool:
    return (root / 'src/main/resources/META-INF/neoforge.mods.toml').is_file()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    other = parser.add_mutually_exclusive_group(required=True)
    other.add_argument('--forge', type=Path, help='the Forge 1.20.1 checkout (run from the NeoForge one)')
    other.add_argument('--neoforge', type=Path, help='the NeoForge 1.21.1 checkout (run from the Forge one)')
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    # The same file ships in both checkouts; ROOT is whichever one it is run from.
    if args.forge is not None:
        forge, neo = args.forge.resolve(), ROOT
    else:
        forge, neo = ROOT, args.neoforge.resolve()
    if is_neoforge(forge) or not is_neoforge(neo):
        parser.error('expected one Forge checkout and one NeoForge checkout')
    report = audit(forge, neo)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + '\n')
    if report['errors']:
        print('\n'.join(report['errors']), file=sys.stderr); return 1
    print(f"PASS: {report['shared_contract_files']} contract files, {report['shared_config_fields']} config fields, {report['resources_compared']} resources, {report['shared_gametests']} shared GameTests, both directions; native gameplay requires separate verification")
    return 0

if __name__ == '__main__': raise SystemExit(main())
