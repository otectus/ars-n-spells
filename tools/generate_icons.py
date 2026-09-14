#!/usr/bin/env python3
"""Build original ANS icons, state variants and HD32 pack deterministically.

SPDX-License-Identifier: GPL-3.0-only. Run: python tools/generate_icons.py
Requires Pillow. --check compares all generated assets without writing them.
No downloaded art, dependency textures, random seeds or lossy resizes are used.
"""
from pathlib import Path
import argparse, csv, hashlib, io, json, zipfile
from PIL import Image, ImageChops, ImageColor, ImageDraw, ImageFilter
from icon_art import VERSION, PALETTES, commands, draw_commands, palette_for

ROOT = Path(__file__).resolve().parents[1]
PREFIX = 'assets/ars_n_spells/'
STATES = ('normal', 'selected', 'disabled', 'high_contrast', 'monochrome')
BACKGROUNDS = ('none','arcane','fire','ice','lightning','nature','holy','blood','ender','evocation','eldritch')
AUTHOR = 'Ars n Spells contributors, with OpenAI Codex assistance'
SOURCE_URL = 'https://github.com/otectus/ars-n-spells/blob/release/3.3.0/tools/icon_art.py'
ALIASES = {'spark':'spell/burst','flame':'spell/ignite','leaf':'school/nature',
           'bolt':'school/lightning','star':'spell/light','eye':'school/eldritch',
           'drop':'school/blood','moon':'spell/phase'}

def png(im):
    out = io.BytesIO(); im.save(out, format='PNG', optimize=False, compress_level=9)
    return out.getvalue()

def rgba(value): return ImageColor.getrgb(value) + (255,)

def add_pack_provenance(contents, theme):
    record={'schema_version':1,'author':AUTHOR,'license':'GPL-3.0-only',
            'source_files':['tools/icon_art.py','tools/generate_icons.py'],'theme':theme,
            'allowed_transformations':['scale','recolor','compose','modify under GPL-3.0-only'],
            'textures':{path:hashlib.sha256(data).hexdigest() for path,data in sorted(contents.items()) if path.endswith('.png')}}
    contents['ans-art-provenance.json']=(json.dumps(record,indent=2)+'\n').encode()

def render(family, name, size, state='normal', theme=None):
    mask = draw_commands(commands(family, name), size)
    color, light = PALETTES[palette_for(family,name)]
    if state == 'disabled': color, light = '#707381','#B7BBC5'
    if state in ('monochrome','high_contrast') or theme in ('monochrome','high_contrast'): color, light = '#F1E9D8','#FFFFFF'
    edge = mask.filter(ImageFilter.MaxFilter(3))
    out = Image.new('RGBA',(size,size)); out.paste(rgba('#181A24'), mask=edge)
    out.paste(rgba(color), mask=mask)
    # HD32 is separately rasterized: contour and lit edge stay one physical
    # pixel, with a second inset engraving. It is not nearest-neighbour output.
    offset = Image.new('L',(size,size)); offset.paste(mask,(1,1))
    highlight = ImageChops.subtract(mask, offset)
    out.paste(rgba(light), mask=highlight)
    if size == 32:
        inset = ImageChops.subtract(mask, mask.filter(ImageFilter.MinFilter(3)))
        shadow = Image.new('L',(size,size)); shadow.paste(inset,(-1,-1))
        out.paste(rgba(color), mask=ImageChops.multiply(shadow, mask))
        out.paste(rgba(light), mask=highlight)
    d = ImageDraw.Draw(out)
    if state == 'selected':
        for x,y,dx,dy in ((1,1,1,1),(size-2,1,-1,1),(1,size-2,1,-1),(size-2,size-2,-1,-1)):
            d.line((x+3*dx,y,x,y,x,y+3*dy), fill='#FFF6D7',width=1)
    elif state == 'disabled':
        d.line((2,size-3,size-3,2),fill='#181A24',width=3)
        d.line((2,size-3,size-3,2),fill='#F1E9D8',width=1)
    # Native icons always retain their transparent outer pixel.
    d.rectangle((0,0,size-1,0),fill=0); d.rectangle((0,size-1,size-1,size-1),fill=0)
    d.rectangle((0,0,0,size-1),fill=0); d.rectangle((size-1,0,size-1,size-1),fill=0)
    return out

def background(name, size=16):
    out = Image.new('RGBA',(size,size))
    if name == 'none': return out
    color = rgba(PALETTES[name][0]); light = rgba(PALETTES[name][1])
    d = ImageDraw.Draw(out); last=size-2
    # Engraved school frame on dark slate, separately composable behind art.
    # Glyph at center remains undisturbed; edge patterns encode school shape.
    d.polygon([(3,1),(size-4,1),(last,3),(last,size-4),(size-4,last),(3,last),(1,size-4),(1,3)],fill='#181A24')
    dark=tuple(int(c*.26) for c in color[:3])+(255,)
    d.rectangle((3,3,size-4,size-4),fill=dark)
    d.line((3,1,size-4,1,last,3),fill=light)
    d.line((1,3,1,size-4,3,last,size-4,last,last,size-4,last,4),fill=color)
    # Authored edge notches; exact motifs remain readable at 16 and 32.
    mid=size//2
    if name in ('arcane','evocation'): d.point([(mid,1),(1,mid),(last,mid),(mid,last)],fill='#FFFFFF')
    elif name == 'fire': d.line((mid-2,last,mid,last-2,mid+2,last), fill=light)
    elif name == 'ice': d.line((1,mid-2,3,mid,1,mid+2),fill=light); d.line((last,mid-2,last-2,mid,last,mid+2),fill=light)
    elif name == 'lightning': d.line((last,mid-3,last-2,mid,last,mid,last-2,mid+3),fill=light)
    elif name == 'nature': d.rectangle((1,mid-1,2,mid+1),fill=light); d.rectangle((last-1,mid-1,last,mid+1),fill=light)
    elif name == 'holy': d.line((mid-2,1,mid+2,1),fill='#FFFFFF'); d.line((mid,1,mid,3),fill=light)
    elif name == 'blood': d.polygon([(mid,last-2),(mid+1,last),(mid-1,last)],fill=light)
    elif name == 'ender': d.rectangle((1,mid-1,1,mid+1),fill=0); d.rectangle((last,mid-1,last,mid+1),fill=0)
    elif name == 'eldritch': d.line((1,mid-2,2,mid,1,mid+2),fill=light); d.point((last,mid),fill='#FFFFFF')
    return out

def build(check=False):
    seed=ROOT/'tools/icon_catalog.csv'
    if not seed.exists():
        source=ROOT/'docs/audit-2026-09-05/planned-icons.csv'
        rows=list(csv.DictReader(source.open(encoding='utf-8-sig')))
        seed.parent.mkdir(parents=True,exist_ok=True)
        with seed.open('w',newline='',encoding='utf-8') as f:
            w=csv.DictWriter(f,fieldnames=['logical_id','resource_path','priority','fallback','accessible_label','uses'])
            w.writeheader(); w.writerows({k:r[k] for k in w.fieldnames} for r in rows)
    rows=list(csv.DictReader(seed.open(encoding='utf-8')))
    assert len(rows)==274 and len({r['logical_id'] for r in rows})==274
    artifacts={}; hd={}; entries=[]; labels={}; images=[]
    for row in rows:
        logical=row['logical_id']; family,name=logical.split(':')[1].split('/')
        normal=render(family,name,16); images.append((family+'/'+name,normal))
        hashes={}
        for size, target in ((16,artifacts),(32,hd)):
            for state in STATES:
                path=row['resource_path'] if state=='normal' else PREFIX+f'textures/gui/icons/v2/variants/{state}/{family}/{name}.png'
                data=png(render(family,name,size,state)); target[path]=data
                hashes[f'{size}/{state}']=hashlib.sha256(data).hexdigest()
        aliases=[a for a,v in ALIASES.items() if v==family+'/'+name]
        aliases += ['icon_'+a for a in list(aliases)]
        if family=='school': aliases += ['nature_'+name]
        if family=='school' and name=='evocation': aliases += ['nature_arcane']
        if family=='school' and name=='generic': aliases += ['ars_cross_default']+[f'ars_cross_{i}' for i in range(1,9)]
        fallback=row['fallback'] if family+'/'+name!='school/unknown' else 'ars_n_spells:school/generic'
        if not fallback.startswith('ars_n_spells:'): fallback='ars_n_spells:school/generic'
        if family+'/'+name=='school/generic': fallback=None
        entries.append(dict(id=logical,resource=row['resource_path'].removeprefix('assets/ars_n_spells/'),
            dimensions=[16,16],hd_dimensions=[32,32],aliases=aliases,localization_key=row['accessible_label'],
            fallback=fallback,renderer_hints={'sampling':'nearest','native_size':16,'states':list(STATES),'background':'separate_layer'},
            source_file='tools/icon_art.py',author=AUTHOR,license='GPL-3.0-only',provenance_url=SOURCE_URL,
            allowed_transformations=['scale','recolor','compose','modify under GPL-3.0-only'],
            sha256=hashes['16/normal'],variant_sha256=hashes,priority=row['priority'],
            review_status='16px light/dark contact sheets visually reviewed with Codex; runtime matrix tracked in docs/3.3.0-audit-status.md',uses=row['uses']))
        labels[row['accessible_label']]=name.replace('_',' ').capitalize()
    backgrounds=[]
    for name in BACKGROUNDS:
        path=PREFIX+f'textures/gui/icons/v2/background/{name}.png'
        artifacts[path]=png(background(name)); hd[path]=png(background(name,32))
        backgrounds.append(dict(id=name,resource=path.removeprefix(PREFIX),dimensions=[16,16],
            source_file='tools/generate_icons.py',author=AUTHOR,license='GPL-3.0-only',provenance_url=SOURCE_URL.replace('icon_art.py','generate_icons.py'),
            sha256=hashlib.sha256(artifacts[path]).hexdigest(),hd_sha256=hashlib.sha256(hd[path]).hexdigest(),
            review_status='16px composition sheet visually reviewed with Codex; runtime matrix tracked in docs/3.3.0-audit-status.md'))
        labels['icon.ars_n_spells.background.'+name]= 'No background' if name=='none' else name.capitalize()+' frame'
    # Legacy paths remain valid for saved items and resource-pack compatibility.
    for old,target in ALIASES.items(): artifacts[PREFIX+f'textures/gui/icons/spell/icon_{old}.png']=artifacts[PREFIX+f'textures/gui/icons/v2/{target}.png']
    for school in BACKGROUNDS[1:]:
        target='evocation' if school=='arcane' else school
        artifacts[PREFIX+f'textures/gui/icons/spell/nature_{school}.png']=artifacts[PREFIX+f'textures/gui/icons/v2/school/{target}.png']
    generic=artifacts[PREFIX+'textures/gui/icons/v2/school/generic.png']
    artifacts[PREFIX+'textures/gui/icons/spell/ars_cross_default.png']=generic
    for i in range(1,9): artifacts[PREFIX+f'textures/gui/spell_icons/ars_cross_{i}.png']=generic
    for entry in entries:
        entry['legacy_resources'] = [dict(resource=path.removeprefix(PREFIX),sha256=hashlib.sha256(data).hexdigest())
            for path,data in artifacts.items() if path.endswith('.png') and '/v2/' not in path
            and hashlib.sha256(data).hexdigest() == entry['sha256']]
    manifest=dict(schema_version=2,generator_version=VERSION,default='ars_n_spells:school/generic',
                  icons=entries,backgrounds=backgrounds,states=list(STATES),hd_pack='ars-n-spells-icons-hd32.zip')
    artifacts[PREFIX+'icon_manifest.json']=(json.dumps(manifest,indent=2)+'\n').encode()
    # The catalog class is common-side data only; it cannot load client textures.
    constants=',\n        '.join('"'+e['id'].split(':')[1]+'"' for e in entries)
    aliases=',\n        '.join('Map.entry("'+a+'", "'+v+'")' for a,v in ALIASES.items())
    java='''package com.otectus.arsnspells.icons;

import java.util.List;
import java.util.Map;

/** Generated by tools/generate_icons.py. Original art catalog; safe on a server. */
public final class IconCatalog {
    private IconCatalog() {}
    public static final List<String> IDS = List.of(
        %s);
    public static final List<String> BACKGROUNDS = List.of(%s);
    public static final Map<String, String> LEGACY = Map.ofEntries(
        %s);
    public static final String DEFAULT = "school/generic";
    public static String canonical(String key) {
        if (key == null || key.length() > 96) return null;
        String id = key.startsWith("ars_n_spells:") ? key.substring(13) : key;
        if (id.startsWith("icon_")) id = id.substring(5);
        if (LEGACY.containsKey(id)) return LEGACY.get(id);
        if (id.startsWith("nature_")) id = "school/" + id.substring(7);
        if (id.equals("school/arcane")) id = "school/evocation";
        if (id.equals("ars_cross_default") || id.matches("ars_cross_[1-8]")) return DEFAULT;
        return IDS.contains(id) ? id : null;
    }
    public static String background(String key) {
        return BACKGROUNDS.contains(key == null ? "" : key) ? key : "none";
    }
    public static String label(String key) {
        String id = canonical(key);
        return "icon.ars_n_spells." + (id == null ? DEFAULT : id).replace('/', '.');
    }
}
'''%(constants,', '.join('"'+b+'"' for b in BACKGROUNDS),aliases)
    outputs={ROOT/'src/main/resources'/p:data for p,data in artifacts.items()}
    outputs[ROOT/'src/main/java/com/otectus/arsnspells/icons/IconCatalog.java']=java.encode()
    outputs[ROOT/'tools/icon_labels.json']=(json.dumps(labels,indent=2)+'\n').encode()
    # Resource pack supports Forge 1.20.1 and NeoForge 1.21.1. Native-size-aware
    # compositing samples the whole source texture regardless of this size.
    hd['pack.mcmeta']=json.dumps({'pack':{'pack_format':15,'supported_formats':{'min_inclusive':15,'max_inclusive':34},'description':'ANS 3.3.0 — original HD32 icon engravings'}}).encode()
    add_pack_provenance(hd, 'hd32')
    data=io.BytesIO()
    with zipfile.ZipFile(data,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for path,content in sorted(hd.items()):
            info=zipfile.ZipInfo(path,(2026,9,6,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
            z.writestr(info,content)
    outputs[ROOT/'resourcepacks/ars-n-spells-icons-hd32.zip']=data.getvalue()
    # Accessibility packs apply to native wheel/bar rendering as well as custom UI.
    # Runtime variants remain available for individual explicitly selected states.
    for state in ('high_contrast','monochrome'):
        pack=io.BytesIO()
        with zipfile.ZipFile(pack,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            contents={'pack.mcmeta':json.dumps({'pack':{'pack_format':15,'supported_formats':{'min_inclusive':15,'max_inclusive':34},'description':'ANS 3.3.0 — '+state.replace('_',' ')+' icons'}}).encode()}
            for row, entry in zip(rows, entries):
                family,name=row['logical_id'].split(':')[1].split('/')
                normal=png(render(family,name,16,theme=state))
                contents[row['resource_path']]=normal
                for variant in STATES[1:]:
                    contents[PREFIX+f'textures/gui/icons/v2/variants/{variant}/{family}/{name}.png']=png(render(family,name,16,variant,theme=state))
                for legacy in entry['legacy_resources']: contents[PREFIX+legacy['resource']]=normal
            for name in BACKGROUNDS:
                original=background(name)
                gray=original.convert('L').convert('RGBA'); gray.putalpha(original.getchannel('A'))
                contents[PREFIX+f'textures/gui/icons/v2/background/{name}.png']=png(gray)
            add_pack_provenance(contents, state)
            for path,content in sorted(contents.items()):
                info=zipfile.ZipInfo(path,(2026,9,6,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,content)
        outputs[ROOT/f'resourcepacks/ars-n-spells-icons-{state.replace("_","-")}.zip']=pack.getvalue()
    mismatches=[]
    for path,content in outputs.items():
        if check:
            if not path.exists() or path.read_bytes()!=content: mismatches.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(content)
    if mismatches: raise SystemExit('Generated files differ: '+', '.join(mismatches[:20]))
    if not check:
        lang=ROOT/'src/main/resources/assets/ars_n_spells/lang/en_us.json'
        translations=json.loads(lang.read_text(encoding='utf-8')); translations.update(labels)
        lang.write_text(json.dumps(translations,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
        make_sheets(images)
    print(f'{len(entries)} base icons, {len(backgrounds)} backgrounds, 5 states, native16 + HD32; '+('verified' if check else 'generated'))

def make_sheets(images):
    folder=ROOT/'docs/3.3.0-icons';folder.mkdir(parents=True,exist_ok=True)
    for family in sorted({n.split('/')[0] for n,_ in images}):
        family_images=[(n,im) for n,im in images if n.startswith(family+'/')]
        cols=5; w=cols*176; h=((len(family_images)+cols-1)//cols)*82+32
        sheet=Image.new('RGB',(w,h),'#181A24');d=ImageDraw.Draw(sheet)
        d.text((8,8),family.upper()+' / ACTUAL 16px + 3x DETAIL',fill='#F1E9D8')
        for idx,(name,im) in enumerate(family_images):
            x=idx%cols*176+8;y=idx//cols*82+32
            d.rectangle((x,y,x+17,y+17),fill='#8B8B8B');sheet.paste(im,(x+1,y+1),im)
            sheet.paste(im,(x+24,y+1),im)
            big=im.resize((48,48),Image.Resampling.NEAREST);sheet.paste(big,(x+48,y),big)
            d.text((x,y+54),name.split('/')[1],fill='#F1E9D8')
        sheet.save(folder/f'{family}.png')
    sheet=Image.new('RGBA',(660,150),'#181A24');d=ImageDraw.Draw(sheet)
    for i,name in enumerate(BACKGROUNDS):
        x=i*60; bg=background(name);icon=render('spell','fireball',16)
        bg.alpha_composite(icon)
        sheet.alpha_composite(bg,(x+10,30));sheet.alpha_composite(bg.resize((48,48),Image.Resampling.NEAREST),(x+5,60))
        d.text((x+2,120),name,fill='#F1E9D8')
    sheet.save(folder/'backgrounds.png')

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true')
    build(parser.parse_args().check)
