"""Resource/provenance/graph checks on shipped art; no Minecraft needed."""
import hashlib, io, json, zipfile
from pathlib import Path
from PIL import Image
root=Path(__file__).resolve().parents[1]
assets=root/'src/main/resources/assets/ars_n_spells'
manifest=json.loads((assets/'icon_manifest.json').read_text())
entries={r['id']:r for r in manifest['icons']}
assert len(entries)==274
labels=json.loads((assets/'lang/en_us.json').read_text(encoding='utf-8'))
for key,row in entries.items():
    assert row['localization_key'] in labels, key
    for field in ('author','license','source_file','provenance_url','sha256','review_status','allowed_transformations'):
        assert row[field], (key,field)
    assert (root/row['source_file']).is_file()
    seen=set(); current=key
    while current:
        assert current not in seen, ('cyclic fallback',key)
        seen.add(current); current=entries[current]['fallback']
    family,name=key.split(':')[1].split('/')
    for state in manifest['states']:
        path=assets/('textures/gui/icons/v2/'+('' if state=='normal' else 'variants/'+state+'/')+family+'/'+name+'.png')
        with Image.open(path) as im:
            assert im.size==(16,16) and im.mode=='RGBA',path
            assert im.getbbox() is not None,path
            assert im.getchannel('A').getextrema()[0]==0,path
            assert all(im.getpixel((i,0))[3]==im.getpixel((0,i))[3]==im.getpixel((15,i))[3]==im.getpixel((i,15))[3]==0 for i in range(16)),path
        assert hashlib.sha256(path.read_bytes()).hexdigest()==row['variant_sha256']['16/'+state]
    for legacy in row['legacy_resources']:
        assert hashlib.sha256((assets/legacy['resource']).read_bytes()).hexdigest()==legacy['sha256']
for background in manifest['backgrounds']:
    assert hashlib.sha256((assets/background['resource']).read_bytes()).hexdigest()==background['sha256']
with zipfile.ZipFile(root/'resourcepacks/ars-n-spells-icons-hd32.zip') as pack:
    assert len([n for n in pack.namelist() if n.endswith('.png')])==274*5+11
    for row in entries.values():
        data=pack.read('assets/ars_n_spells/'+row['resource'])
        assert hashlib.sha256(data).hexdigest()==row['variant_sha256']['32/normal']
for filename in ('hd32','high-contrast','monochrome'):
    with zipfile.ZipFile(root/f'resourcepacks/ars-n-spells-icons-{filename}.zip') as pack:
        provenance=json.loads(pack.read('ans-art-provenance.json'))
        files={name for name in pack.namelist() if name.endswith('.png')}
        assert files==set(provenance['textures']), filename
        assert len(files)>=274*5+11, filename
        for name in files:
            raw=pack.read(name)
            assert hashlib.sha256(raw).hexdigest()==provenance['textures'][name], name
            with Image.open(io.BytesIO(raw)) as im:
                assert im.mode=='RGBA' and im.size==((32,32) if filename=='hd32' else (16,16)), name
print('PASS: 274 logical icons; 1,370 native state rasters; 11 backgrounds; HD32; provenance, aliases, labels and acyclic fallbacks')
