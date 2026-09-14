"""Merge maintained client labels without replacing unrelated translations."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
path=root/'src/main/resources/assets/ars_n_spells/lang/en_us.json'
labels={
 'transaction.ars_n_spells.native':'Spell',
 'transaction.ars_n_spells.cross':'Cross-cast',
 'transaction.ars_n_spells.paid':'Paid %s %s',
 'transaction.ars_n_spells.required':'Requires %s %s',
 'transaction.ars_n_spells.reserved':'Reserved %s %s',
 'transaction.ars_n_spells.refunded':'Refunded %s %s',
 'transaction.ars_n_spells.stage.reserved':'Reserved',
 'transaction.ars_n_spells.stage.committed':'Paid',
 'transaction.ars_n_spells.stage.refunded':'Refunded',
 'transaction.ars_n_spells.stage.refused':'Unable to cast',
 'transaction.ars_n_spells.resource.ars_mana':'Ars mana',
 'transaction.ars_n_spells.resource.irons_mana':'Iron’s mana',
 'transaction.ars_n_spells.resource.lp':'LP',
 'transaction.ars_n_spells.resource.aura':'aura',
 'transaction.ars_n_spells.reason.none':'',
 'transaction.ars_n_spells.reason.insufficient_resource':'Insufficient resources',
 'transaction.ars_n_spells.reason.cancelled':'Cast cancelled',
 'transaction.ars_n_spells.reason.native_failure':'Spell could not resolve',
 'transaction.ars_n_spells.reason.adapter_unavailable':'Required resource unavailable',
 'transaction.ars_n_spells.reason.resource_changed':'Resources changed before casting',
 'ars_n_spells.icon_picker.library':'Icon library',
 'ars_n_spells.compatibility.title':'Compatibility',
 'ars_n_spells.compatibility.evidence':'Installation status. Presence alone does not verify gameplay; see the release test matrix.',
 'ars_n_spells.compatibility.present':'Installed · runtime verification separate',
 'ars_n_spells.compatibility.absent':'Absent · optional features unavailable',
 'ars_n_spells.icon_picker.title':'Spell icon library',
 'ars_n_spells.icon_picker.open':'Choose icon…',
 'ars_n_spells.icon_picker.search':'Search symbols or names',
 'ars_n_spells.icon_picker.next':'Next',
 'ars_n_spells.icon_picker.no_results':'No matching icons',
 'ars_n_spells.icon_picker.page':'Page %s / %s · %s icons',
 'ars_n_spells.icon_picker.cosmetic':'Backgrounds are cosmetic. They do not change the spell’s school or cost.',
 'ars_n_spells.icon_picker.state_preview':'Preview accessibility artwork. Use the included high contrast or monochrome resource pack to apply it throughout the game.',
 'ars_n_spells.icon_picker.state.normal':'Normal preview',
 'ars_n_spells.icon_picker.state.high_contrast':'High contrast',
 'ars_n_spells.icon_picker.state.monochrome':'Monochrome',
 'ars_n_spells.spell_loom.tooltip.nature':'Choose a cosmetic school background frame. The same frame appears in the native spell wheel.',
 'ars_n_spells.spell_loom.tooltip.icon':'Search all 274 original symbols. Select a symbol and background, then choose Done.',
 'ars_n_spells.spell_loom.tooltip.preview':'Preview: %s · %s',
 'ars_n_spells.spell_loom.tooltip.inscribe':'Create one scroll. Reusable books and foci are retained; disposable spell scrolls are consumed.',
 'ars_n_spells.spell_loom.error.invalid_target':'Use an Iron’s scroll as the target.',
}
for family in ('all','spell','school','element','resource','status','compat','ui','ritual','carrier'):
    labels['ars_n_spells.icon_picker.family.'+family]={'all':'All families','ui':'Controls','compat':'Compatibility'}.get(family,family.capitalize())
for nature in ('none','evocation','eldritch'):
    labels['ars_n_spells.nature.'+nature]=nature.capitalize()
content=json.loads(path.read_text(encoding='utf-8'));content.update(labels)
path.write_text(json.dumps(content,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
