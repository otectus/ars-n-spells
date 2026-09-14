package com.otectus.arsnspells.client.icons;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpellIconCompositionTest {
    @Test void transparentSymbolKeepsBackground() {
        assertEquals(0xFF654321, SpellIconRegistry.blend(0, 0xFF654321));
    }
    @Test void opaqueSymbolKeepsExactColor() {
        assertEquals(0xFFABCDEF, SpellIconRegistry.blend(0xFFABCDEF, 0xFF123456));
    }
    @Test void translucentPackPixelsBlendWithoutOpaqueBlackHalos() {
        int halfWhiteOverBlack = SpellIconRegistry.blend(0x80FFFFFF, 0xFF000000);
        assertEquals(0xFF808080, halfWhiteOverBlack);
        assertEquals(0x80FFFFFF, SpellIconRegistry.blend(0x80FFFFFF, 0));
        assertEquals(0, SpellIconRegistry.blend(0, 0));
    }
}
