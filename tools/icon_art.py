"""Original ANS pixel-grid artwork. SPDX-License-Identifier: GPL-3.0-only.

Authored for ANS 3.3.0 with OpenAI Codex assistance. No upstream art inputs.
Coordinates are deliberate 16px design geometry, not hashes or generated noise.
The renderer outlines these masks and adds an upper-left engraving highlight.
"""
from PIL import Image, ImageDraw

VERSION = '3.3.0-art-1'
PALETTES = {
    'fire': ('#F47738', '#FFD08A'), 'ice': ('#61CBEA', '#DBF8FF'),
    'lightning': ('#F3C94A', '#FFF0B2'), 'nature': ('#72B85B', '#CEE7A4'),
    'holy': ('#E4D69C', '#FFF6D7'), 'blood': ('#C64B68', '#F3A4B6'),
    'ender': ('#B285E8', '#E8D2FF'), 'evocation': ('#548FE0', '#BCD5FF'),
    'eldritch': ('#58B6A4', '#B5EADD'), 'generic': ('#A3A9B7', '#E4E8F0'),
    'unknown': ('#A3A9B7', '#FFFFFF'), 'arcane': ('#B285E8', '#E8D2FF'),
}

# A small vocabulary of independently drawn silhouettes. Commands are polygon,
# line, rectangle, ellipse, hollow rectangle and hollow ellipse, respectively.
GLYPHS = {
 'diamond':'p 8,2 13,8 8,13 3,8',
 'rune':'l 8,2 13,8 8,13 3,8 8,2|l 6,7 9,7 9,10',
 'flame':'p 8,2 9,6 12,4 13,9 11,12 6,13 3,10 3,7 5,9 6,5|l 7,10 8,8 9,11',
 'ice':'l 8,2 8,13|l 3,5 13,11|l 3,11 13,5|l 6,3 8,5 10,3|l 6,12 8,10 10,12',
 'bolt':'p 8,2 12,2 9,6 12,6 5,13 7,9 4,9',
 'leaf':'p 3,3 8,4 8,8 4,7|p 12,5 12,9 8,10 8,7|l 8,5 8,13 5,12',
 'sun':'p 7,3 9,3 9,6 12,6 12,9 9,9 9,12 6,12 6,9 3,9 3,6 6,6|l 3,3 4,4|l 12,3 11,4|l 3,12 4,11|l 12,12 11,11',
 'drop':'p 8,2 12,8 12,11 10,13 6,13 4,11 4,8|c 7,8 8,11',
 'portal':'l 6,2 4,4 3,8 4,12 6,13|l 10,2 12,4 13,8 12,12 10,13|l 7,5 9,5 10,8 9,10 7,10 6,8 7,5',
 'conjure':'l 8,2 13,8 8,13 3,8 8,2|p 7,5 10,8 7,11|l 2,2 3,3|l 12,12 13,13',
 'eye':'l 2,8 5,4 9,3 13,7 12,10 8,11 4,10 2,8|e 6,5 9,9|l 4,11 3,13|l 10,11 11,13',
 'question':'l 5,4 6,2 10,2 12,4 12,6 8,9 8,10|r 8,12 8,13',
 'heart':'p 3,4 6,3 8,5 10,3 13,4 13,8 8,13 3,8',
 'shield':'p 3,3 8,2 13,3 12,9 8,13 4,9|c 7,5 8,9',
 'plus':'r 7,3 9,12|r 3,7 13,9',
 'cross':'l 4,4 12,12|l 4,12 12,4',
 'check':'l 3,8 6,11 13,4',
 'arrow':'l 3,8 13,8|l 9,4 13,8 9,12',
 'back':'l 3,8 13,8|l 7,4 3,8 7,12',
 'up':'l 8,3 8,13|l 4,7 8,3 12,7',
 'down':'l 8,3 8,13|l 4,9 8,13 12,9',
 'burst':'l 8,2 8,13|l 2,8 13,8|l 4,4 11,11|l 4,11 11,4',
 'star':'p 8,2 10,6 13,6 11,9 12,13 8,11 4,13 5,9 2,6 6,6',
 'moon':'p 9,2 5,3 3,7 4,11 8,13 12,11 8,10 6,7 7,4',
 'clock':'o 3,3 13,13|l 8,5 8,8 11,8|l 7,1 9,1',
 'hourglass':'l 4,2 12,2 11,5 6,10 4,13 12,13 11,10 6,5 4,2|r 7,4 9,4',
 'chain':'o 2,3 9,8|o 7,7 13,12|l 6,7 9,8',
 'unlink':'l 2,4 3,3 7,3 8,4|l 2,6 3,8 5,8|l 8,10 9,12 12,12 13,10 13,8|l 5,12 10,2',
 'lock':'b 4,7 12,13|l 5,6 5,4 7,2 10,2 12,4 12,6|r 8,9 8,11',
 'unlock':'b 4,7 12,13|l 6,6 6,4 8,2 11,2 13,4|r 8,9 8,11',
 'book':'l 2,3 5,2 8,4 11,2 13,3 13,12 10,11 8,13 5,11 2,12 2,3|l 8,4 8,13',
 'scroll':'l 4,2 11,2 13,4 11,5 11,11 13,12 12,13 3,13 2,11 4,10 4,2|l 5,4 11,4',
 'page':'l 4,2 10,2 13,5 13,13 4,13 4,2|l 10,2 10,5 13,5',
 'cube':'l 8,2 13,5 13,11 8,14 3,11 3,5 8,2|l 3,5 8,8 13,5|l 8,8 8,14',
 'jar':'l 5,2 11,2 11,4 13,6 13,12 11,13 5,13 3,12 3,6 5,4 5,2|l 4,8 12,8',
 'skull':'p 5,3 11,3 13,5 13,9 11,11 11,13 5,13 5,11 3,9 3,5|c 5,6 6,8|c 10,6 11,8|c 8,10 8,11',
 'sword':'p 11,2 13,2 13,4 7,10 5,8|l 3,7 9,13|l 3,13 6,10',
 'wand':'l 3,13 10,6|l 10,2 10,5|l 12,4 13,4|l 6,4 8,4|l 12,7 13,8',
 'wing':'p 2,3 7,5 8,10 10,5 13,3 13,8 10,12 8,13 5,11 3,8|l 3,5 5,8',
 'hand':'l 3,8 3,5 5,5 5,8 5,2 7,2 7,7 8,3 10,3 10,7 11,5 13,5 12,11 9,13 5,12 3,8',
 'flask':'l 6,2 10,2 9,6 13,11 12,13 4,13 3,11 7,6 6,2|l 5,10 11,10',
 'cloud':'l 3,11 2,8 4,6 6,6 7,3 10,3 12,6 13,7 13,10 11,11 3,11',
 'target':'o 3,3 13,13|o 6,6 10,10|r 8,8 8,8',
 'warning':'l 8,2 14,13 2,13 8,2|l 8,6 8,9|r 8,11 8,11',
 'info':'o 2,2 13,13|r 8,4 8,4|l 7,7 8,7 8,11 10,11',
 'gear':'o 4,4 11,11|o 6,6 9,9|r 6,2 9,3|r 6,12 9,13|r 2,6 3,9|r 12,6 13,9',
 'person':'e 6,2 10,5|l 3,8 13,8|l 8,6 8,10|l 5,13 8,10 11,13',
 'spiral':'l 3,12 2,6 5,2 10,2 13,6 12,11 8,13 5,10 5,6 8,5 10,7 9,9 7,8',
 'flower':'o 6,2 10,6|o 2,6 6,10|o 10,6 14,10|o 6,10 10,14|e 7,7 9,9',
 'sound':'p 3,6 5,6 8,3 8,12 5,9 3,9|l 10,5 12,7 12,9 10,11',
 'mountain':'p 2,12 6,3 8,7 10,4 14,12|c 6,6 6,8',
 'root':'l 8,2 8,8 3,12|l 8,8 12,12|l 6,10 6,13|l 8,8 10,5 13,4',
 'familiar':'p 3,2 7,5 10,5 13,2 12,8 10,10 11,13 5,13 6,10 4,8|c 6,6 6,7|c 10,6 10,7',
 'flag':'l 4,13 4,2 12,2 10,5 12,8 5,8',
 'balance':'l 8,2 8,13|l 3,5 13,5|l 3,5 2,9 5,9 3,5|l 12,5 10,9 14,9 12,5|l 5,13 11,13',
 'hammer':'p 3,3 6,2 11,6 8,9|l 9,8 4,13',
 'scissors':'o 2,8 6,13|o 8,8 12,13|l 5,9 12,2|l 9,9 3,2',
 'magnet':'l 3,3 3,9 5,12 10,12 13,9 13,3 10,3 10,9 6,9 6,3 3,3',
 'anvil':'p 2,4 13,4 11,7 9,7 9,10 13,12 13,13 3,13 3,12 6,10 6,7 3,7',
 'focus':'p 7,2 10,4 10,7 8,9 5,7 5,4|l 8,9 8,13|l 6,12 10,12',
 'grid':'b 3,3 6,6|b 10,3 13,6|b 3,10 6,13|b 10,10 13,13',
 'list':'l 6,3 13,3|l 6,8 13,8|l 6,13 13,13|r 3,3 3,3|r 3,8 3,8|r 3,13 3,13',
 'filter':'l 2,3 13,3 9,7 9,12 6,13 6,7 2,3',
 'search':'o 2,2 10,10|l 10,10 14,14',
 'server':'b 3,2 13,6|b 3,9 13,13|r 5,4 5,4|r 5,11 5,11',
 'monitor':'b 2,2 13,10|l 8,10 8,13|l 5,13 11,13',
 'mouse':'l 5,2 10,2 12,5 12,10 10,13 5,13 3,10 3,5 5,2|l 8,3 8,7 12,7',
 'keyboard':'b 2,4 14,12|l 4,6 4,7|l 7,6 7,7|l 10,6 10,7|l 5,10 11,10',
 'gamepad':'l 4,4 11,4 14,11 11,13 9,10 6,10 4,13 2,11 4,4|l 4,7 7,7|l 5,6 5,8|r 11,6 11,6|r 12,8 12,8',
 'chest':'b 2,4 13,13|l 2,7 13,7|r 7,7 8,9|l 4,2 11,2',
}

# Every spell has an authored action silhouette. Related actions deliberately
# share a root motif with a meaningful stroke: e.g. projectile, rain or channel.
SPELLS = {
 'projectile':'p 3,5 10,5 13,8 10,11 3,11|l 2,3 5,3',
 'beam':'l 2,4 13,4|l 2,8 13,8|l 2,12 13,12',
 'ray':'l 3,12 13,2|l 2,7 2,11|l 5,13 9,13',
 'burst':'@burst', 'cone':'l 3,8 13,2 13,13 3,8|l 6,8 11,8',
 'nova':'o 5,5 11,11|l 8,1 8,3|l 8,13 8,14|l 1,8 3,8|l 13,8 14,8|l 3,3 4,4|l 12,12 13,13',
 'wall':'b 2,3 13,13|l 2,7 13,7|l 2,10 13,10|l 6,3 6,7|l 10,7 10,10|l 5,10 5,13',
 'ring':'o 2,5 13,11|l 3,4 5,3|l 10,13 12,12',
 'orbit':'o 2,5 13,11|e 6,6 9,9|e 10,3 12,5',
 'chain':'@chain', 'trap':'l 2,12 5,7 8,12 11,7 14,12|l 3,5 4,2|l 12,5 11,2',
 'rune':'@rune', 'missile':'p 11,2 13,2 13,5 7,11 5,9|l 3,6 3,10 7,13|l 2,13 3,12',
 'volley':'l 3,5 9,2 7,5|l 4,10 13,3 11,7|l 8,13 13,9 12,12',
 'rain':'l 4,3 2,7|l 9,2 7,6|l 13,4 11,8|l 6,9 4,13|l 11,10 10,13',
 'meteor':'e 3,7 10,13|l 7,7 12,2|l 10,9 13,5|l 4,7 7,2',
 'shard':'p 8,2 12,5 9,13 4,10|l 8,3 7,10',
 'lance':'p 11,2 13,2 13,4 8,9 6,7|l 7,8 2,13',
 'wave':'l 2,10 4,6 7,6 9,10 12,10 14,6|l 2,13 5,10 7,13 10,13',
 'pulse':'l 2,8 5,8 7,3 9,13 11,8 14,8',
 'fireball':'e 4,6 11,13|p 5,6 7,2 8,6 12,3 12,8|l 6,10 8,8',
 'flame_jet':'p 2,10 7,7 6,4 10,5 13,2 13,7 9,11 4,12',
 'ignite':'@flame|l 2,3 3,4|l 12,12 13,13',
 'combustion':'p 8,2 10,5 13,4 12,8 14,11 10,11 8,14 6,11 2,12 4,8 2,4 6,5|c 7,7 9,9',
 'frost_bolt':'@bolt|l 2,3 4,3|l 3,2 3,4|l 12,10 12,13|l 11,12 13,12',
 'freeze':'b 3,4 12,13|l 3,7 12,7|l 6,2 6,5|l 10,2 10,5|l 7,9 9,11|l 9,9 7,11',
 'blizzard':'l 2,5 11,5 13,3 11,2|l 2,8 13,8|l 5,11 11,11 13,13|l 3,10 3,13|l 2,12 4,12',
 'hail':'l 3,2 2,5|l 8,2 7,5|l 13,2 12,5|p 3,7 5,9 3,11 1,9|p 9,9 11,11 9,13 7,11|p 13,6 14,8 12,9 11,7',
 'lightning_bolt':'@bolt', 'thunderstorm':'l 2,6 3,3 6,3 8,2 11,3 13,5 13,7 10,7|p 7,6 10,6 8,9 10,9 5,14 6,10 4,10',
 'shock_chain':'l 2,5 5,3 4,7 7,6 6,10 10,8 9,12 13,10|l 2,11 4,13|l 11,2 13,4',
 'static_field':'b 2,2 13,13|l 7,4 5,8 9,7 7,11|r 4,4 4,4|r 11,11 11,11',
 'poison_cloud':'@cloud|l 4,12 5,14|l 8,12 8,14|l 12,12 11,14',
 'toxic_spore':'e 5,5 11,11|l 8,2 8,4|l 2,6 4,7|l 12,7 14,5|l 4,13 6,11|l 11,11 13,13|c 7,7 8,8',
 'vine_grasp':'l 3,13 6,9 5,5 8,2 11,3 11,6 9,7|l 6,9 10,10 13,7|p 2,7 5,8 3,10',
 'thorn_burst':'l 8,3 8,13|l 3,6 13,10|l 3,11 12,3|p 8,3 6,5 8,6|p 11,4 13,4 12,7|p 3,11 3,8 6,10',
 'root_snare':'@root|l 3,4 3,7 5,8|l 11,2 13,2',
 'earthquake':'l 2,5 7,5 5,8 9,8 7,11 13,11|l 2,12 4,10|l 11,4 13,6',
 'stone_spike':'p 3,13 6,3 8,7 11,2 13,13|l 8,8 7,12',
 'sand_blast':'l 2,6 6,4 8,5|l 2,10 7,8 9,9|r 10,3 10,3|r 13,6 13,6|r 11,11 11,11|r 6,13 6,13',
 'heal':'@plus', 'cleanse':'@drop|l 2,2 4,4|l 3,1 3,5|l 1,3 5,3',
 'regenerate':'l 4,5 3,9 6,13 11,12 13,8|l 4,5 8,5 8,2|l 6,8 10,8|l 8,6 8,10',
 'sanctuary':'l 2,7 8,2 14,7|l 4,7 4,13 12,13 12,7|l 6,9 10,9|l 8,7 8,11',
 'ward':'@shield', 'barrier':'l 3,13 3,6 6,2 10,2 13,6 13,13|l 5,13 5,7 8,4 11,7 11,13',
 'reflect':'l 3,3 3,8 8,8 8,3|l 5,5 8,2 11,5|l 3,13 12,4',
 'absorb':'o 5,5 11,11|l 2,2 5,5 3,5|l 14,2 11,5 11,3|l 2,14 5,11 5,13|l 14,14 11,11 13,11',
 'blood_lance':'p 10,2 12,5 10,7 8,5|l 9,6 3,13|l 3,9 6,12',
 'drain_life':'@heart|c 4,7 10,7|l 7,5 10,7 7,9',
 'sacrifice':'@drop|l 2,12 13,3', 'blood_pact':'@scroll|r 6,7 9,8|l 8,9 8,11',
 'wither':'l 8,3 8,13|l 8,6 4,5 3,3|l 8,9 12,8 13,5|l 6,13 10,13',
 'curse':'@skull|l 2,2 3,3|l 13,12 14,13',
 'hex':'l 5,2 11,2 14,8 11,13 5,13 2,8 5,2|l 6,6 10,10|l 6,10 10,6',
 'terror':'p 3,3 6,5 10,5 13,3 12,11 8,14 4,11|c 5,6 6,7|c 10,6 11,7|c 7,10 9,12',
 'blink':'l 2,4 4,4|l 1,8 3,8|l 2,12 4,12|p 9,3 13,8 9,13 5,8',
 'teleport':'l 3,3 6,3 6,6|l 3,3 8,8|l 9,10 13,10 13,13|l 9,10 12,7|l 3,12 6,9|l 10,3 12,5',
 'portal':'@portal', 'recall':'l 12,11 12,5 9,2 5,2 3,5|l 2,2 3,6 7,5|l 6,11 8,9 10,11 10,13 6,13 6,11',
 'phase':'l 6,2 6,5|l 6,7 6,10|l 6,12 6,14|l 2,8 13,8|l 10,5 13,8 10,11',
 'gravity_pull':'l 2,3 5,6 3,6|l 13,3 10,6 10,4|l 2,13 5,10 3,10|l 13,13 10,10 10,12|e 7,7 8,8',
 'gravity_push':'l 2,3 5,6|l 2,3 2,6|l 13,3 10,6|l 13,3 10,3|l 2,13 5,10|l 2,13 5,13|l 13,13 10,10|l 13,13 13,10|e 7,7 8,8',
 'levitate':'l 3,12 13,12|l 8,3 8,10|l 5,6 8,3 11,6|l 3,14 5,14|l 11,14 13,14',
 'flight':'@wing', 'dash':'l 2,4 7,4|l 2,8 6,8|l 2,12 7,12|l 9,3 13,8 9,13',
 'leap':'l 2,12 4,5 7,2 11,4 13,9|l 10,8 13,10 14,6',
 'slow':'l 3,10 3,7 6,4 10,4 12,7 12,10 3,10|l 3,12 13,12 14,10|o 6,6 9,9',
 'haste':'l 3,3 7,8 3,13|l 8,3 12,8 8,13',
 'summon_familiar':'@familiar', 'summon_guardian':'@shield|l 2,12 3,14 13,14 14,12',
 'summon_undead':'@skull|l 2,13 2,14 14,14 14,13',
 'summon_weapon':'@sword|l 9,2 8,3|l 12,10 14,10|l 13,9 13,11',
 'command_minion':'@flag|e 9,10 12,13', 'banish':'@portal|l 2,13 14,2',
 'light':'@sun', 'reveal':'@eye|l 8,1 8,2|l 2,3 3,4|l 12,2 13,3',
 'detect':'@target|l 8,8 12,4', 'invisibility':'@eye|l 2,13 13,2',
 'silence':'@sound|l 3,13 13,2', 'dispel':'@rune|l 2,13 13,2',
 'counterspell':'l 2,4 5,7 2,10|l 13,4 10,7 13,10|l 7,2 8,5|l 7,9 8,13',
 'break_block':'@cube|l 9,3 7,7 10,9 7,13',
 'place_block':'@cube|l 1,2 1,5|l 0,3 2,3',
 'harvest':'l 3,13 7,3 11,2 13,5 10,7 7,7|l 8,4 11,4',
 'grow':'@leaf|l 11,2 11,4|l 10,3 12,3',
 'smelt':'@flame|l 2,12 2,14 14,14 14,12', 'cut':'@scissors',
 'extract':'l 3,9 3,13 13,13 13,9|l 8,10 8,2|l 5,5 8,2 11,5',
 'exchange':'l 2,5 13,5 10,2|l 13,10 2,10 5,13',
 'transmute':'l 3,3 6,3 6,6 3,6 3,3|l 9,10 12,7 14,10 12,13 9,10|l 4,12 9,5',
 'collect':'@magnet', 'interact':'@hand', 'carry':'@hand|l 2,14 13,14',
 'craft':'@anvil', 'mana_transfer':'@arrow|l 3,3 5,1 7,3 5,5 3,3|l 9,13 11,11 13,13',
 'mana_drain':'@down|l 3,2 3,5 13,5 13,2',
 'mana_restore':'@up|l 3,11 3,14 13,14 13,11',
 'source_transfer':'@jar|l 5,10 11,10 9,8', 'lifelink':'@chain|l 3,11 5,13 7,11',
 'phantom_grasp':'@hand|l 2,2 3,3|l 13,1 14,2', 'charm':'@heart|l 1,2 3,2|l 2,1 2,3',
 'time_delay':'@hourglass', 'contingency':'@clock|l 1,2 3,2 3,4',
 'split':'l 8,13 8,8 3,3|l 8,8 13,3|l 3,6 3,3 6,3|l 10,3 13,3 13,6',
 'amplify':'l 3,12 3,9|l 7,12 7,6|l 11,12 11,3|l 9,5 11,3 13,5',
 'dampen':'l 3,12 3,3|l 7,12 7,6|l 11,12 11,9|l 9,7 11,9 13,7',
 'extend':'l 2,8 13,8|l 5,5 2,8 5,11|l 10,5 13,8 10,11',
 'area_expand':'l 2,6 2,2 6,2|l 10,2 13,2 13,6|l 2,10 2,13 6,13|l 10,13 13,13 13,10|b 6,6 9,9',
}

SCHOOLS = dict(zip(PALETTES, ['flame','ice','bolt','leaf','sun','drop','portal','conjure','eye','rune','question','rune']))
ELEMENTS = dict(zip('air earth water fire manipulation conjuration abjuration necromancy arcane light shadow void time force poison frost'.split(),
                    'wing mountain drop flame hand conjure shield skull rune sun moon portal hourglass hammer flask ice'.split()))

UI = dict(zip('inscribe bind unbind transcribe erase copy paste preview confirm cancel close back forward up down expand collapse search filter sort grid list favorite unfavorite lock unlock info help warning error success settings reset refresh reload import export inspect compare link unlink swap next_spell previous_spell hand_main hand_off mouse_right keyboard gamepad inventory output'.split(),
              'wand chain unlink scroll cross page scroll eye check cross cross back arrow up down burst diamond search filter balance grid list star star lock unlock info question warning cross check gear back spiral spiral down up target balance chain unlink balance arrow back hand hand mouse keyboard gamepad chest arrow'.split()))

RESOURCES = dict(zip('mana_ars mana_irons mana_shared mana_split lp aura source mana_cost mana_refund mana_reserve mana_capacity mana_regeneration conversion efficiency power resistance health cooldown duration range area tier_one tier_two tier_three'.split(),
                     'rune flask chain balance drop leaf jar down up lock jar spiral balance gear sword shield heart clock hourglass arrow grid diamond diamond diamond'.split()))
CARRIERS = dict(zip('scroll_blank scroll_inscribed book_ars book_irons book_mixed focus parchment slot_empty slot_native slot_cross slot_conflict slot_locked'.split(),
                    'scroll scroll book book book focus page grid sword rune warning lock'.split()))
STATUSES = dict(zip('resonance_ready resonance_lingering resonance_depleted affinity_gain affinity_decay progression_gain mastery mana_boost mana_regen source_proximity source_empty cooldown_shared cooldown_personal silenced interrupted cast_pending cast_failed spell_bound spell_unbound spell_missing spell_blacklisted insufficient_mana insufficient_lp insufficient_aura overdrawn'.split(),
                    'sun sun moon leaf leaf up star plus spiral jar jar chain clock sound cross hourglass cross chain unlink question lock flask drop leaf balance'.split()))
COMPAT = dict(zip('available absent degraded disabled unsupported untested mismatch synchronized desynchronized optional required server_only client_only api_verified runtime_verified resource_missing data_migrated migration_needed conflict fallback addon_unknown'.split(),
                  'check cross warning lock hand question unlink chain unlink diamond star server monitor page target page scroll hourglass warning back question'.split()))
RITUALS = dict(zip('mana_infusion mana_well spell_transcription spellbook_binding spell_uninscription'.split(), 'flask jar scroll book unlink'.split()))

def commands(family, name):
    if family == 'spell':
        return SPELLS[name]
    vocab = {'school':SCHOOLS, 'element':ELEMENTS, 'ui':UI, 'resource':RESOURCES,
             'carrier':CARRIERS, 'status':STATUSES, 'compat':COMPAT, 'ritual':RITUALS}[family]
    base = '@' + vocab[name]
    # Meaningful visual additions to otherwise related concepts. These are
    # authored separately from state badges (not arbitrary per-ID fingerprints).
    details = {
        'school/unknown':'l 2,10 2,13 5,13',
        'ui/inscribe':'l 2,14 11,14', 'ui/transcribe':'l 6,7 9,7|l 6,10 9,10',
        'ui/bind':'l 2,14 13,14', 'ui/unbind':'l 2,14 13,14',
        'ui/success':'o 1,1 14,14', 'ui/export':'l 3,11 3,14 13,14 13,11',
        'ui/import':'l 3,2 3,5 13,5 13,2',
        'ui/erase':'l 2,13 13,13', 'ui/cancel':'o 2,2 13,13',
        'ui/error':'l 5,1 10,1 14,5 14,10 10,14 5,14 1,10 1,5 5,1',
        'ui/copy':'l 2,5 2,14 10,14', 'ui/paste':'b 6,6 9,9',
        'ui/unfavorite':'l 2,13 13,2', 'ui/reset':'l 2,3 2,8',
        'ui/reload':'l 1,10 1,14 5,14', 'ui/compare':'l 1,2 1,13',
        'ui/swap':'l 2,2 13,2', 'ui/next_spell':'l 14,3 14,13',
        'ui/previous_spell':'l 1,3 1,13', 'ui/hand_off':'l 1,3 1,12',
        'ui/output':'l 2,3 2,13',
        'carrier/scroll_inscribed':'l 6,7 9,7|l 6,10 9,10',
        'carrier/book_ars':'l 4,5 6,7 4,9', 'carrier/book_irons':'l 10,5 10,9',
        'carrier/book_mixed':'l 4,5 6,7 4,9|l 10,5 10,9',
        'resource/mana_ars':'l 1,5 1,11', 'resource/mana_irons':'l 5,10 7,8 9,10',
        'resource/mana_split':'l 1,1 1,13|l 15,1 15,13',
        'resource/lp':'l 6,6 9,6|l 8,4 8,8', 'resource/aura':'o 2,2 13,13',
        'resource/mana_cost':'l 3,2 13,2', 'resource/mana_refund':'l 3,13 13,13',
        'resource/mana_capacity':'l 6,6 10,6', 'resource/mana_regeneration':'l 1,2 3,2',
        'resource/health':'l 6,7 10,7|l 8,5 8,9', 'resource/range':'l 3,3 3,5|l 13,3 13,5',
        'resource/area':'l 7,7 9,9', 'resource/tier_one':'c 6,6 10,10|r 8,8 8,8',
        'resource/tier_two':'c 6,6 10,10|r 6,8 6,8|r 10,8 10,8',
        'resource/tier_three':'c 6,6 10,10|r 6,9 6,9|r 10,9 10,9|r 8,6 8,6',
        'status/resonance_lingering':'l 1,2 1,5', 'status/resonance_depleted':'l 2,13 13,2',
        'status/affinity_gain':'l 11,1 11,3|l 10,2 12,2', 'status/affinity_decay':'l 10,2 13,2',
        'status/progression_gain':'l 2,14 13,14', 'status/mastery':'o 1,1 14,14',
        'status/source_proximity':'l 1,3 1,11|l 15,3 15,11',
        'status/source_empty':'l 2,13 13,2', 'status/cooldown_shared':'l 1,13 4,13',
        'status/cooldown_personal':'e 1,1 3,3', 'status/silenced':'l 2,13 13,2',
        'status/interrupted':'l 8,1 8,3', 'status/cast_failed':'b 1,1 14,14',
        'status/spell_bound':'l 2,14 13,14', 'status/spell_unbound':'l 2,14 13,14',
        'status/spell_missing':'l 2,11 2,14 5,14', 'status/spell_blacklisted':'l 1,13 14,2',
        'status/insufficient_mana':'l 1,1 14,14', 'status/insufficient_lp':'l 1,1 14,14',
        'status/insufficient_aura':'l 1,1 14,14', 'status/overdrawn':'l 2,2 13,13',
        'compat/absent':'l 2,14 13,14', 'compat/degraded':'l 2,1 5,1',
        'compat/unsupported':'l 2,13 13,2', 'compat/mismatch':'l 1,1 4,1',
        'compat/desynchronized':'l 1,13 4,13', 'compat/api_verified':'l 6,9 8,11 11,7',
        'compat/runtime_verified':'l 2,13 4,14 7,11',
        'compat/resource_missing':'l 6,7 11,12|l 6,12 11,7',
        'compat/data_migrated':'l 6,9 8,11 10,8',
        'compat/conflict':'l 3,1 12,1', 'compat/fallback':'l 13,2 13,5',
        'compat/addon_unknown':'l 2,12 2,14 5,14',
    }
    if family == 'element':
        base += '|l 1,4 1,1 4,1|l 11,14 14,14 14,11'
    if family == 'ritual':
        base += '|l 1,4 1,12 3,14 12,14 14,12 14,4 12,1 3,1 1,4'
    return base + ('|' + details[family+'/'+name] if family+'/'+name in details else '')

def draw_commands(cmd, size=16):
    out = Image.new('L', (size, size), 0)
    d = ImageDraw.Draw(out)
    scale = size / 16
    def pt(s): return tuple(round(float(c)*scale) for c in s.split(','))
    for part in cmd.split('|'):
        if part.startswith('@'):
            # Expand primitives inline, maintaining explicit clear strokes.
            sub = draw_commands(GLYPHS[part[1:]], size)
            from PIL import ImageChops
            out = ImageChops.lighter(out, sub); d = ImageDraw.Draw(out)
            continue
        op, *raw = part.split(); points = [pt(p) for p in raw]
        width = 1 if size == 16 else 2
        if op == 'p': d.polygon(points, fill=255)
        elif op == 'l': d.line(points, fill=255, width=width)
        elif op == 'r': d.rectangle(points, fill=255)
        elif op == 'b': d.rectangle(points, outline=255, width=width)
        elif op == 'e': d.ellipse(points, fill=255)
        elif op == 'o': d.ellipse(points, outline=255, width=width)
        elif op == 'c': d.rectangle(points, fill=0)
        else: raise ValueError(part)
    # Transparent safe area is invariant, including composed motifs.
    d = ImageDraw.Draw(out)
    d.rectangle((0,0,size-1,0), fill=0); d.rectangle((0,size-1,size-1,size-1),fill=0)
    d.rectangle((0,0,0,size-1),fill=0); d.rectangle((size-1,0,size-1,size-1),fill=0)
    return out

def palette_for(family, name):
    if family == 'school': return name
    if name == 'lp' or name.endswith('_lp') or any(w in name for w in ('blood','life','sacrifice','health')): return 'blood'
    if any(w in name for w in ('fire','flame','ignite','combust','smelt','meteor')): return 'fire'
    if any(w in name for w in ('ice','frost','freeze','blizzard','hail','water')): return 'ice'
    if any(w in name for w in ('bolt','lightning','shock','thunder','static','haste')): return 'lightning'
    if any(w in name for w in ('leaf','nature','source','aura','poison','toxic','vine','thorn','root','grow','harvest','familiar')): return 'nature'
    if any(w in name for w in ('heal','cleanse','regen','sanctuary','ward','holy','light','sun')): return 'holy'
    if any(w in name for w in ('curse','hex','terror','wither','phantom','eldritch','necromancy')): return 'eldritch'
    if any(w in name for w in ('portal','teleport','phase','blink','void','ender','mana_ars')): return 'ender'
    return 'evocation' if family in ('spell','resource','ritual') else 'generic'
