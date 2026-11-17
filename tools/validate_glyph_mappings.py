#!/usr/bin/env python3
"""Validate Ars 'n' Spells imported-glyph mapping files offline: the dry run a pack author can do
before shipping a data pack, with the same rules the game applies on /reload.

Shared, unchanged, by the Forge 1.20.1 and NeoForge 1.21.1 builds (tools/verify_loader_parity.py
checks that). The rules are the canonical schema in docs/3.4.1/mapping-schema.md; the game's own
implementations (GlyphMappingSnapshot on Forge, GlyphMappings on NeoForge) and this one are all
tested against the same fixtures, src/test/resources/ans_shared_fixtures/mappings. In game, the
same check is /ans glyph validate.

Each PACK is a data pack directory (with data/<namespace>/ans_iron_glyphs/...) or a .zip of one.
Packs are given lowest priority first, as /datapack list orders them: a later pack overrides an
earlier one, and two different declarations of one spell in the same pack conflict and fail closed.

Usage:
  validate_glyph_mappings.py [--catalog CATALOG.json ...] [--json] PACK [PACK ...]

  --catalog   the imported-glyph catalog(s) to check against, required catalog first (default: this
              checkout's bundled catalog). Add an addon's catalog to validate files for its spells.
  --json      print a machine-readable report instead of text.

Exit status: 0 valid, 1 invalid (every problem is listed with its pack, file and field), 2 unusable input.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass, field
from decimal import Decimal, InvalidOperation
import json
from pathlib import Path
import re
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BUNDLED_CATALOGS = (ROOT / 'src/main/resources/ars_n_spells/iron_glyph_catalog.json',
                    ROOT / 'src/main/resources/ans_glyph_catalog/irons_spellbooks.json')
DIRECTORY = 'ans_iron_glyphs'

SCHEMA_VERSION = 2
LEGACY_SCHEMA_VERSION = 1
MAX_FILES = 2048
MAX_FILE_BYTES = 262_144
MAX_SOURCE_COST = 100_000
MAX_DURATION_TICKS = 20 * 60 * 60
MAX_CATALYSTS = 64
MAX_LEVEL = 99
INT_MAX = 2 ** 31 - 1
DEFAULT_CATALYST_TAG = 'ars_n_spells:glyph_press_catalysts'
SELF_ADAPTER = 'ars_n_spells:self'

SPELL_KEYS = {'schema_version', 'type', 'spell', 'enabled', 'adapter', 'defaults', 'levels'}
LEVEL_KEYS = {'enabled', 'ars_tier', 'source_cost', 'duration_ticks', 'catalyst'}
CATALYST_KEYS = {'tag', 'count'}
TIERS_KEYS = {'schema_version', 'type', 'tiers'}
TIER_TERMS_KEYS = {'source_cost', 'duration_ticks', 'catalyst'}
POLICY_KEYS = {'schema_version', 'type', 'allow_spells', 'deny_spells', 'allow_schools', 'deny_schools'}

NAMESPACE = re.compile(r'[a-z0-9_.-]+\Z')
PATH = re.compile(r'[a-z0-9/._-]*\Z')


class Invalid(ValueError):
    """One problem in one file, already prefixed with its field path."""


@dataclass(frozen=True)
class Terms:
    source_cost: int
    duration_ticks: int
    catalyst_tag: str
    catalyst_count: int


DEFAULT_TIERS = {1: Terms(500, 100, DEFAULT_CATALYST_TAG, 1),
                 2: Terms(1500, 140, DEFAULT_CATALYST_TAG, 2),
                 3: Terms(3000, 200, DEFAULT_CATALYST_TAG, 4)}


@dataclass(frozen=True)
class Override:
    enabled: bool | None = None
    tier: int | None = None
    source_cost: int | None = None
    duration_ticks: int | None = None
    catalyst_tag: str | None = None
    catalyst_count: int | None = None

    def empty(self) -> bool:
        return self == Override()


NONE = Override()


@dataclass(frozen=True)
class SpellMapping:
    pack: str
    file: str
    enabled: bool | None
    self_only: bool
    defaults: Override
    levels: tuple

    def level(self, level: int) -> Override:
        return dict(self.levels).get(level, NONE)


@dataclass(frozen=True)
class Source:
    """One mapping file as a loader finds it: its id, its pack and the pack's rank (higher wins)."""
    id: str
    pack: str
    rank: int
    json: object


@dataclass
class CatalogSpell:
    spell: str
    school: str
    adapter: str
    min_level: int
    registered_max: int


@dataclass
class Snapshot:
    tiers: dict
    spells: dict
    allow_spells: set
    deny_spells: set
    allow_schools: set
    deny_schools: set
    failed_closed: set
    errors: list
    winners: list
    catalog: dict = field(repr=False, default_factory=dict)

    @property
    def valid(self) -> bool:
        return not self.errors

    def refusal(self, spell: str, level: int, school: str | None = None) -> str | None:
        """None when the data-pack layer allows the glyph, otherwise which rule refuses it. Deny wins."""
        if school is None and spell in self.catalog:
            school = self.catalog[spell].school
        if spell in self.failed_closed:
            return 'a mapping file for this spell is invalid (fails closed)'
        if spell in self.deny_spells:
            return 'spell denied by policy'
        if school is not None and school in self.deny_schools:
            return f'school {school} denied by policy'
        if (self.allow_spells or self.allow_schools) and spell not in self.allow_spells \
                and (school is None or school not in self.allow_schools):
            return 'not on the policy allow list'
        mapping = self.spells.get(spell)
        if mapping is None:
            return None
        if mapping.enabled is False:
            return 'disabled by its mapping'
        specific = mapping.level(level)
        if specific.enabled is not None:
            return None if specific.enabled else f'level {level} disabled by its mapping'
        if mapping.defaults.enabled is False:
            return 'disabled by its mapping defaults'
        return None

    def allows(self, spell: str, level: int, school: str | None = None) -> bool:
        return self.refusal(spell, level, school) is None

    def self_only(self, spell: str) -> bool:
        mapping = self.spells.get(spell)
        return mapping is not None and mapping.self_only

    def tier_override(self, spell: str, level: int) -> int | None:
        mapping = self.spells.get(spell)
        if mapping is None:
            return None
        specific = mapping.level(level).tier
        return specific if specific is not None else mapping.defaults.tier

    def press_terms(self, spell: str, level: int, tier: int) -> Terms:
        base = self.tiers.get(tier, DEFAULT_TIERS[3])
        mapping = self.spells.get(spell)
        if mapping is None:
            return base
        defaults, specific = mapping.defaults, mapping.level(level)
        if defaults.empty() and specific.empty():
            return base
        pick = lambda name: next(v for v in (getattr(specific, name), getattr(defaults, name), getattr(base, name)) if v is not None)
        return Terms(pick('source_cost'), pick('duration_ticks'), pick('catalyst_tag'), pick('catalyst_count'))

    def catalyst_tags(self) -> set:
        tags = {DEFAULT_CATALYST_TAG} | {terms.catalyst_tag for terms in self.tiers.values()}
        for mapping in self.spells.values():
            if mapping.defaults.catalyst_tag is not None:
                tags.add(mapping.defaults.catalyst_tag)
            for _, override in mapping.levels:
                if override.catalyst_tag is not None:
                    tags.add(override.catalyst_tag)
        return tags


# ---- JSON, strictly ---------------------------------------------------------------------------

def loads(text: str):
    """Parse JSON keeping every number exact, so 10.5 is never truncated and 1e12 is never rounded."""
    def constant(name):
        raise ValueError(f'{name} is not a JSON number')
    return json.loads(text, parse_float=Decimal, parse_int=Decimal, parse_constant=constant)


def describe(value) -> str:
    if value is None:
        return 'nothing'
    if isinstance(value, dict):
        return 'an object'
    if isinstance(value, list):
        return 'a list'
    if isinstance(value, bool):
        return 'true' if value else 'false'
    if isinstance(value, str):
        return 'the string ' + json.dumps(value)
    return str(value)


def at(path: str, key: str) -> str:
    return key if not path else f'{path}.{key}'


def parse_id(raw: str) -> str | None:
    """Minecraft's ResourceLocation.tryParse: a missing namespace is minecraft."""
    index = raw.find(':')
    namespace, path = ('minecraft', raw) if index < 0 else (raw[:index] or 'minecraft', raw[index + 1:])
    if not NAMESPACE.match(namespace) or not PATH.match(path):
        return None
    return f'{namespace}:{path}'


def obj(value, path: str, allowed: set | None) -> dict:
    if not isinstance(value, dict):
        raise Invalid(f'{path}: expected an object, got {describe(value)}')
    if allowed is not None:
        for key in value:
            if not key.startswith('_') and key not in allowed:
                raise Invalid(f'{at(path, key)}: unknown field (allowed: {", ".join(sorted(allowed))})')
    return value


def whole(value, where: str, low: int, high: int) -> int:
    if not isinstance(value, Decimal):
        raise Invalid(f'{where}: expected a whole number in {low}..{high}, got {describe(value)}')
    try:
        fractional = value != 0 and value != value.to_integral_value()
    except InvalidOperation:
        raise Invalid(f'{where}: not a finite number: {value}')
    if fractional:
        raise Invalid(f'{where}: expected a whole number, got {value}')
    if value < low or value > high:
        raise Invalid(f'{where}: {value} is outside {low}..{high}')
    return int(value)


def boolean(value, where: str) -> bool:
    if not isinstance(value, bool):
        raise Invalid(f'{where}: expected true or false, got {describe(value)}')
    return value


def string(value, where: str) -> str:
    if not isinstance(value, str):
        raise Invalid(f'{where}: expected a string, got {describe(value)}')
    return value


def ident(value, where: str) -> str:
    raw = string(value, where)
    parsed = parse_id(raw)
    if parsed is None:
        raise Invalid(f'{where}: not a valid id: "{raw}"')
    return parsed


def ids(value, where: str) -> list:
    if not isinstance(value, list):
        raise Invalid(f'{where}: expected a list of ids, got {describe(value)}')
    out = []
    for index, item in enumerate(value):
        here = f'{where}[{index}]'
        if not isinstance(item, str):
            raise Invalid(f'{here}: expected an id, got {describe(item)}')
        parsed = parse_id(item)
        if parsed is None:
            raise Invalid(f'{here}: not a valid id: "{item}"')
        out.append(parsed)
    return out


def level_key(key: str, where: str, low: int, high: int) -> int:
    if not key or len(key) > 2 or not key.isdigit() or not key.isascii() or (len(key) > 1 and key[0] == '0'):
        raise Invalid(f'{where}: level key "{key}" is not a level number')
    level = int(key)
    if level < low or level > high:
        raise Invalid(f'{where}: level {level} is outside {low}..{high}')
    return level


# ---- The canonical rules ----------------------------------------------------------------------

def override(value: dict, path: str) -> Override:
    enabled = boolean(value['enabled'], at(path, 'enabled')) if 'enabled' in value else None
    tier = whole(value['ars_tier'], at(path, 'ars_tier'), 1, 3) if 'ars_tier' in value else None
    cost = whole(value['source_cost'], at(path, 'source_cost'), 0, MAX_SOURCE_COST) if 'source_cost' in value else None
    ticks = whole(value['duration_ticks'], at(path, 'duration_ticks'), 1, MAX_DURATION_TICKS) if 'duration_ticks' in value else None
    tag = count = None
    if 'catalyst' in value:
        where = at(path, 'catalyst')
        catalyst = obj(value['catalyst'], where, CATALYST_KEYS)
        if 'tag' in catalyst:
            tag = ident(catalyst['tag'], at(where, 'tag'))
        if 'count' in catalyst:
            count = whole(catalyst['count'], at(where, 'count'), 0, MAX_CATALYSTS)
    return Override(enabled, tier, cost, ticks, tag, count)


def tier_terms(value: dict, path: str) -> Terms:
    if 'source_cost' not in value or 'duration_ticks' not in value:
        raise Invalid(f'{path}: a press tier needs source_cost and duration_ticks')
    source = whole(value['source_cost'], at(path, 'source_cost'), 0, MAX_SOURCE_COST)
    duration = whole(value['duration_ticks'], at(path, 'duration_ticks'), 1, MAX_DURATION_TICKS)
    if 'catalyst' not in value:
        raise Invalid(f'{path}: a press tier needs a catalyst (use "count": 0 for none)')
    where = at(path, 'catalyst')
    catalyst = obj(value['catalyst'], where, CATALYST_KEYS)
    tag = ident(catalyst['tag'], at(where, 'tag')) if 'tag' in catalyst else DEFAULT_CATALYST_TAG
    count = whole(catalyst['count'], at(where, 'count'), 0, MAX_CATALYSTS) if 'count' in catalyst else 0
    return Terms(source, duration, tag, count)


def spell_mapping(source: Source, spell: str, document: dict, catalog: dict) -> SpellMapping:
    entry = catalog.get(spell)
    if entry is None:
        raise Invalid(f'spell: {spell} is not in the imported-glyph catalog')
    self_only = False
    if 'adapter' in document:
        adapter = ident(document['adapter'], 'adapter')
        if adapter == SELF_ADAPTER:
            self_only = True
        elif adapter != entry.adapter:
            raise Invalid(f'adapter: {spell} is catalogued with adapter {entry.adapter}; a datapack may confirm it or restrict '
                          f'the spell to {SELF_ADAPTER}, never change it')
    enabled = boolean(document['enabled'], 'enabled') if 'enabled' in document else None
    defaults = override(obj(document['defaults'], 'defaults', LEVEL_KEYS), 'defaults') if 'defaults' in document else NONE
    levels = {}
    if 'levels' in document:
        by_level = obj(document['levels'], 'levels', None)
        for key, value in by_level.items():
            if key.startswith('_'):
                continue
            where = at('levels', key)
            level = level_key(key, where, 1, MAX_LEVEL)
            if level < entry.min_level or level > entry.registered_max:
                raise Invalid(f'{where}: {spell} level {level} is not a registered glyph (catalog registers {entry.min_level}-'
                              f'{entry.registered_max}); a reload cannot add glyph identities')
            levels[level] = override(obj(value, where, LEVEL_KEYS), where)
    return SpellMapping(source.pack, source.id, enabled, self_only, defaults, tuple(sorted(levels.items())))


def named_spell(document) -> str | None:
    if isinstance(document, dict) and isinstance(document.get('spell'), str):
        return parse_id(document['spell'])
    return None


def build(sources: list, catalog: dict) -> Snapshot:
    """Every source file into one candidate snapshot, with every problem found (empty only when valid)."""
    errors, winners = [], []
    allow_spells, deny_spells, allow_schools, deny_schools, failed_closed = set(), set(), set(), set(), set()
    if len(sources) > MAX_FILES:
        errors.append(f'too many {DIRECTORY} files: {len(sources)} (at most {MAX_FILES} are read)')
        sources = sources[:MAX_FILES]
    spell_candidates, tier_candidates = {}, {}
    for source in sorted(sources, key=lambda s: (s.rank, s.id)):
        where = f'{source.id} [{source.pack}]'
        document = source.json
        try:
            if not isinstance(document, dict):
                raise Invalid('not a JSON object')
            schema = whole(document.get('schema_version'), 'schema_version', 0, INT_MAX)
            if schema not in (SCHEMA_VERSION, LEGACY_SCHEMA_VERSION):
                raise Invalid(f'schema_version: {schema} is not supported (expected {SCHEMA_VERSION}, or {LEGACY_SCHEMA_VERSION} for 3.4.0 files)')
            kind = string(document['type'], 'type') if 'type' in document else 'ars_n_spells:spell'
            if kind == 'ars_n_spells:press_tiers':
                obj(document, '', TIERS_KEYS)
                tiers = obj(document.get('tiers'), 'tiers', {'1', '2', '3'})
                for key, value in tiers.items():
                    if key.startswith('_'):
                        continue
                    here = at('tiers', key)
                    tier = level_key(key, here, 1, 3)
                    terms = tier_terms(obj(value, here, TIER_TERMS_KEYS), here)
                    tier_candidates.setdefault(tier, []).append((source, terms))
            elif kind in ('ars_n_spells:policy', 'policy'):
                if kind == 'policy' and schema != LEGACY_SCHEMA_VERSION:
                    raise Invalid(f'type: "policy" is the 3.4.0 alias; schema {SCHEMA_VERSION} uses "ars_n_spells:policy"')
                obj(document, '', POLICY_KEYS)
                for key, target in (('allow_spells', allow_spells), ('deny_spells', deny_spells),
                                    ('allow_schools', allow_schools), ('deny_schools', deny_schools)):
                    if key in document:
                        target.update(ids(document[key], key))
            elif kind == 'ars_n_spells:spell':
                obj(document, '', SPELL_KEYS)
                spell = ident(document.get('spell'), 'spell')
                mapping = spell_mapping(source, spell, document, catalog)
                spell_candidates.setdefault(spell, []).append((source, mapping))
            else:
                raise Invalid(f'type: unknown document type "{kind}"')
        except Invalid as invalid:
            errors.append(f'{where}: {invalid}')
            spell = named_spell(document)
            if spell is not None:
                failed_closed.add(spell)
    spells = {}
    for spell in sorted(spell_candidates):
        candidates = spell_candidates[spell]
        top = max(source.rank for source, _ in candidates)
        best = [(source, mapping) for source, mapping in candidates if source.rank == top]
        if len(best) > 1:
            names = ', '.join(f'{source.id} [{source.pack}]' for source, _ in best)
            errors.append(f'{spell} is declared by [{names}] at the same pack priority; the declarations conflict, so the spell fails closed')
            failed_closed.add(spell)
            continue
        source, mapping = best[0]
        spells[spell] = mapping
        overridden = f' (overrides {len(candidates) - 1} lower-priority file(s))' if len(candidates) > 1 else ''
        winners.append(f'{spell} <- {source.id} [{source.pack}]{overridden}')
    tiers = dict(DEFAULT_TIERS)
    for tier in sorted(tier_candidates):
        candidates = tier_candidates[tier]
        top = max(source.rank for source, _ in candidates)
        best = [(source, terms) for source, terms in candidates if source.rank == top]
        if len({terms for _, terms in best}) > 1:
            names = ', '.join(f'{source.id} [{source.pack}]' for source, _ in best)
            errors.append(f'press tier {tier} is declared differently by [{names}] at the same pack priority; the shipped terms stay in force')
            continue
        tiers[tier] = best[0][1]
        winners.append(f'press tier {tier} <- {best[0][0].id} [{best[0][0].pack}]')
    for spell in sorted(allow_spells):
        if spell not in catalog:
            errors.append(f'policy allows unknown spell {spell}')
    for spell in sorted(deny_spells):
        if spell not in catalog:
            errors.append(f'policy denies unknown spell {spell}')
    for spell in failed_closed:
        spells.pop(spell, None)
    return Snapshot(tiers, spells, allow_spells, deny_spells, allow_schools, deny_schools, failed_closed, errors, winners, catalog)


# ---- Catalogs and packs -----------------------------------------------------------------------

def read_catalogs(paths: list) -> dict:
    """Spell id to its catalogued identity range, school and adapter. Later catalogs may only extend an envelope."""
    spells = {}
    for path in paths:
        root = loads(Path(path).read_text(encoding='utf-8'))
        schema = int(root.get('schema_version', -1))
        if schema not in (1, 2):
            raise ValueError(f'{path}: catalog schema {schema} is not 1 or 2')
        for entry in root.get('spells', []):
            spell = parse_id(entry['spell'])
            if schema == 2:
                levels = entry['levels']
                low = int(levels.get('min', 1))
                registered = int(levels.get('registered_max', levels['default_max']))
            else:
                low = int(entry.get('min_level', 1))
                registered = int(entry['max_level'])
            previous = spells.get(spell)
            if previous is None:
                spells[spell] = CatalogSpell(spell, parse_id(entry['school']), parse_id(entry['adapter']), low, registered)
            elif previous.adapter == parse_id(entry['adapter']):
                # Identities only grow (ImportedGlyphCatalog.merge): the registered range is the union.
                previous.registered_max = max(previous.registered_max, registered)
                previous.min_level = min(previous.min_level, low)
    return spells


def read_pack(path: Path, rank: int) -> tuple:
    """(sources, problems) for one data pack directory or zip."""
    sources, problems = [], []
    pack = path.name
    pattern = re.compile(r'data/([^/]+)/' + DIRECTORY + r'/(.+)\.json\Z')
    if path.is_dir():
        entries = [(p.relative_to(path).as_posix(), p) for p in sorted(path.rglob('*.json'))]
        read = lambda p: p.read_bytes() if p.stat().st_size <= MAX_FILE_BYTES else None
    elif zipfile.is_zipfile(path):
        archive = zipfile.ZipFile(path)
        entries = [(info.filename, info) for info in sorted(archive.infolist(), key=lambda i: i.filename) if not info.is_dir()]
        read = lambda info: archive.read(info) if info.file_size <= MAX_FILE_BYTES else None
    else:
        raise ValueError(f'{path}: not a data pack directory or zip')
    for name, handle in entries:
        match = pattern.match(name)
        if not match:
            continue
        file_id = f'{match.group(1)}:{match.group(2)}'
        data = read(handle)
        if data is None:
            problems.append(f'{file_id} [{pack}]: larger than {MAX_FILE_BYTES} bytes; not read')
            continue
        try:
            document = loads(data.decode('utf-8'))
        except (ValueError, UnicodeDecodeError) as malformed:
            problems.append(f'{file_id} [{pack}]: not valid JSON ({malformed})')
            continue
        sources.append(Source(file_id, pack, rank, document))
    return sources, problems


def main(argv: list | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split('\n\n')[0], formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--catalog', action='append', default=[], help='catalog JSON; the required catalog first')
    parser.add_argument('--json', action='store_true', help='print a JSON report')
    parser.add_argument('packs', nargs='+', type=Path, help='data pack directories or zips, lowest priority first')
    args = parser.parse_args(argv)
    try:
        catalogs = args.catalog or [str(next(p for p in BUNDLED_CATALOGS if p.is_file()))]
        catalog = read_catalogs(catalogs)
        sources, problems = [], []
        for rank, pack in enumerate(args.packs, start=1):
            found, unreadable = read_pack(pack, rank)
            sources.extend(found)
            problems.extend(unreadable)
    except (OSError, ValueError, KeyError, StopIteration) as unusable:
        print(f'error: {unusable}', file=sys.stderr)
        return 2
    snapshot = build(sources, catalog)
    errors = problems + snapshot.errors
    if args.json:
        print(json.dumps({'valid': not errors, 'files': len(sources), 'errors': errors, 'winners': snapshot.winners,
                          'failed_closed': sorted(snapshot.failed_closed), 'catalogs': catalogs}, indent=1))
    else:
        for line in errors:
            print('error: ' + line)
        for line in snapshot.winners:
            print('uses: ' + line)
        verdict = 'valid' if not errors else f'INVALID, {len(errors)} problem(s)'
        print(f'{len(sources)} mapping file(s) in {len(args.packs)} pack(s) against {len(catalog)} catalogued spell(s): {verdict}')
    return 0 if not errors else 1


if __name__ == '__main__':
    sys.exit(main())
