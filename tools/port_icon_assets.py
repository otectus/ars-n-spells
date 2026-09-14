"""Publish shared original assets plus explicit version-specific client adapters.

Only this script's owned icon files are copied. Gameplay/serialization files are
never copied or rewritten here; those need the actual loader API adapters.
"""
from pathlib import Path
import json, shutil
root=Path(__file__).resolve().parents[1]
neo=root/'.worktrees/neoforge-3.3.0'
assert (neo/'gradle.properties').is_file()
for relative in ('tools/icon_art.py','tools/generate_icons.py','tools/icon_catalog.csv','tools/icon_labels.json',
                 'tools/add_icon_translations.py','tools/verify_icons.py',
                 'src/main/java/com/otectus/arsnspells/icons/IconCatalog.java',
                 'src/test/java/com/otectus/arsnspells/icons/IconCatalogTest.java'):
    target=neo/relative; target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(root/relative,target)
for relative in ('src/main/resources/assets/ars_n_spells/textures/gui','resourcepacks','docs/3.3.0-icons'):
    shutil.copytree(root/relative,neo/relative,dirs_exist_ok=True)
relative='src/main/resources/assets/ars_n_spells/icon_manifest.json'
shutil.copy2(root/relative,neo/relative)
langpath=Path('src/main/resources/assets/ars_n_spells/lang/en_us.json')
current=json.loads((neo/langpath).read_text(encoding='utf-8')); source=json.loads((root/langpath).read_text(encoding='utf-8'))
for k,v in source.items():
    if k.startswith(('icon.ars_n_spells.','transaction.ars_n_spells.','ars_n_spells.icon_picker.','ars_n_spells.spell_loom.tooltip.','ars_n_spells.compatibility.')):
        current[k]=v
(neo/langpath).write_text(json.dumps(current,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
for relative in ('client/icons/SpellIconRegistry.java','client/icons/CarrierRenderContext.java',
                 'client/icons/IconClientSmoke.java','client/icons/IconReviewScreen.java','client/screen/CompatibilityScreen.java',
                 'client/screen/SpellIconPickerScreen.java',
                 'mixin/irons/MixinItemStackIconContext.java'):
    p=Path('src/main/java/com/otectus/arsnspells')/relative
    text=(root/p).read_text(encoding='utf-8')
    text=text.replace('new ResourceLocation(', 'ResourceLocation.fromNamespaceAndPath(')
    text=text.replace('SpellLoomExportPacket','SpellLoomExportPayload').replace('SpellLoomResultPacket','SpellLoomResultPayload')
    text=text.replace('com.otectus.arsnspells.spell.CrossCastNbt.NATURE_KEYS','com.otectus.arsnspells.spell.CrossModSpellComponents.NATURE_KEYS')
    text=text.replace('renderBackground(graphics);','renderBackground(graphics, mouseX, mouseY, partialTick);')
    text=text.replace('this.renderBackground(g);','this.renderBackground(g, mouseX, mouseY, partialTick);')
    text=text.replace('source.getTag()', 'source.getComponents()').replace('scroll.getTag()', 'scroll.getComponents()')
    # Minecraft 1.21 EditBox drives its caret from rendering; tick() was removed.
    text=text.replace('search.tick();', '').replace('nameField.tick();', '')
    text=text.replace('net.minecraftforge.fml.ModList','net.neoforged.fml.ModList')
    text=text.replace('net.minecraftforge.api.distmarker.Dist','net.neoforged.api.distmarker.Dist')
    text=text.replace('net.minecraftforge.event.TickEvent','net.neoforged.neoforge.client.event.ClientTickEvent')
    text=text.replace('net.minecraftforge.eventbus.api.SubscribeEvent','net.neoforged.bus.api.SubscribeEvent')
    text=text.replace('net.minecraftforge.client.event.RenderGuiEvent','net.neoforged.neoforge.client.event.RenderGuiEvent')
    text=text.replace('net.minecraftforge.fml.common.Mod','net.neoforged.fml.common.EventBusSubscriber')
    text=text.replace('@Mod.EventBusSubscriber','@EventBusSubscriber')
    text=text.replace('TickEvent.ClientTickEvent event','ClientTickEvent.Post event')
    text=text.replace('event.phase != TickEvent.Phase.END || ', '')
    if relative in ('client/screen/SpellIconPickerScreen.java', 'client/screen/CompatibilityScreen.java', 'client/icons/IconReviewScreen.java'):
        # 1.21 Screen.render invokes renderBackground before its widgets. These
        # screens paint their own panel before super.render; a second blur would
        # blur that panel and its text. Apply panorama/blur only before our paint.
        text=text.replace('        renderBackground(graphics, mouseX, mouseY, partialTick);',
                          '        super.renderBackground(graphics, mouseX, mouseY, partialTick);')
        at=text.rfind('}')
        text=text[:at]+'    @Override public void renderBackground(GuiGraphics g, int x, int y, float tick) {}\n'+text[at:]
    (neo/p).parent.mkdir(parents=True,exist_ok=True);(neo/p).write_text(text,encoding='utf-8')
print('Shared icon art published to NeoForge with explicit client API adaptations; no gameplay files overwritten.')
# The GUI has its own 3.3.0 adapter, including shared panel and scrolling widgets.
from port_gui import main as port_gui
port_gui()
