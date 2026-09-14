"""Synchronize the 3.3.0 GUI presentation, with explicit Minecraft API adapters.

Only the listed client files and GUI translations are owned by this port. Menu
geometry is updated in place so loader-specific inventory handling is preserved.
"""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
NEO = ROOT / '.worktrees/neoforge-3.3.0'
CLIENT = Path('src/main/java/com/otectus/arsnspells/client')
FILES = [
    'screen/VanillaGui.java', 'screen/VanillaPanelScreen.java', 'screen/ScrollableTextPanel.java',
    'screen/SpellLoomScreen.java', 'screen/SpellLoomDetailsScreen.java', 'screen/SpellIconPickerScreen.java',
    'screen/SchoolJournalScreen.java', 'screen/CompatibilityScreen.java', 'screen/ConfigScreenFactory.java',
    'icons/IconReviewScreen.java', 'icons/IconClientSmoke.java',
]

def adapt(name, text):
    text = text.replace('SpellLoomExportPacket', 'SpellLoomExportPayload').replace('SpellLoomResultPacket', 'SpellLoomResultPayload')
    text = text.replace('spell.CrossCastNbt.NATURE_KEYS', 'spell.CrossModSpellComponents.NATURE_KEYS')
    text = text.replace('nameField.tick();', '').replace('search.tick();', '')
    text = text.replace('net.minecraftforge.fml.ModList', 'net.neoforged.fml.ModList')
    text = text.replace('net.minecraftforge.api.distmarker.Dist', 'net.neoforged.api.distmarker.Dist')
    text = text.replace('net.minecraftforge.eventbus.api.SubscribeEvent', 'net.neoforged.bus.api.SubscribeEvent')
    text = text.replace('net.minecraftforge.client.event.RenderGuiEvent', 'net.neoforged.neoforge.client.event.RenderGuiEvent')
    text = text.replace('net.minecraftforge.fml.common.Mod;', 'net.neoforged.fml.common.EventBusSubscriber;')
    text = text.replace('@Mod.EventBusSubscriber', '@EventBusSubscriber')
    text = text.replace('net.minecraftforge.event.TickEvent', 'net.neoforged.neoforge.client.event.ClientTickEvent')
    text = text.replace('TickEvent.ClientTickEvent event', 'ClientTickEvent.Post event')
    text = text.replace('event.phase != TickEvent.Phase.END || ', '')
    text = text.replace('double mouseX, double mouseY, double delta)', 'double mouseX, double mouseY, double scrollX, double delta)')
    text = text.replace('double x, double y, double delta)', 'double x, double y, double scrollX, double delta)')
    if name == 'screen/VanillaPanelScreen.java':
        text = text.replace('super.renderBackground(g);', 'super.renderBackground(g, mouseX, mouseY, tick);')
        at = text.rfind('}')
        text = text[:at] + '''    // Screen.render calls this again in 1.21. The native backdrop was already
    // rendered before the panel; applying blur here would blur the controls.
    @Override public void renderBackground(GuiGraphics g, int x, int y, float tick) {}
''' + text[at:]
    if name == 'screen/SpellLoomScreen.java':
        text = text.replace('        this.renderBackground(g);', '        // 1.21 AbstractContainerScreen renders the backdrop and container once.')
    if name == 'screen/ConfigScreenFactory.java':
        at = text.index('        @Override\n        public void render(')
        text = text[:at] + '''        // Settings owns its opaque backdrop; do not apply the 1.21 blur over it.
        @Override public void renderBackground(GuiGraphics g, int x, int y, float tick) {}

''' + text[at:]
    return text

def main():
    assert (NEO / 'gradle.properties').is_file(), 'NeoForge worktree is missing'
    for name in FILES:
        target = NEO / CLIENT / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(adapt(name, (ROOT / CLIENT / name).read_text(encoding='utf-8')), encoding='utf-8', newline='\n')
    path = NEO / 'src/main/java/com/otectus/arsnspells/menu/SpellLoomMenu.java'
    text = path.read_text(encoding='utf-8')
    import re
    for key, value in [('GUI_HEIGHT', 224), ('SLOT_SOURCE_X', 26), ('SLOT_SCROLL_X', 62), ('SLOT_OUTPUT_X', 120)]:
        text, count = re.subn(r'(public static final int ' + key + r' = )\d+;', rf'\g<1>{value};', text)
        assert count == 1, key
    text = text.replace('176x240:', '176x224:').replace('240-tall container', '224-tall container')
    text = text.replace('   // 126', '').replace('  // 184', '')
    path.write_text(text, encoding='utf-8', newline='\n')
    relative = Path('src/main/resources/assets/ars_n_spells/lang/en_us.json')
    source = json.loads((ROOT / relative).read_text(encoding='utf-8'))
    target = json.loads((NEO / relative).read_text(encoding='utf-8'))
    target.update({k:v for k,v in source.items() if k.startswith('ars_n_spells.spell_loom.status.') or k == 'ars_n_spells.icon_picker.selected'})
    (NEO / relative).write_text(json.dumps(target, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    print('Ported GUI presentation to NeoForge; loader-specific menus and gameplay preserved.')

if __name__ == '__main__':
    main()
