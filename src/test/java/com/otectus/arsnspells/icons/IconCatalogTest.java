package com.otectus.arsnspells.icons;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IconCatalogTest {
    @Test void completeAndUniqueCatalog() {
        assertEquals(274, IconCatalog.IDS.size());
        assertEquals(274, new java.util.HashSet<>(IconCatalog.IDS).size());
        assertEquals(11, IconCatalog.BACKGROUNDS.size());
    }
    @Test void legacyAliasesAndLogicalIdsResolve() {
        assertEquals("spell/ignite", IconCatalog.canonical("flame"));
        assertEquals("spell/ignite", IconCatalog.canonical("icon_flame"));
        assertEquals("school/evocation", IconCatalog.canonical("nature_arcane"));
        assertEquals("spell/fireball", IconCatalog.canonical("ars_n_spells:spell/fireball"));
        assertEquals(IconCatalog.DEFAULT, IconCatalog.canonical("ars_cross_8"));
    }
    @Test void unknownPathsCannotEscapeTheCatalog() {
        for (String key : new String[] {"../../secret", "other:spell/fireball", "nature_unknown_mod", "textures/a.png", "ars_cross_9", "future/symbol"}) {
            assertNull(IconCatalog.canonical(key), key);
        }
        assertNull(IconCatalog.canonical(null));
        assertEquals("none", IconCatalog.background("other:fire"));
        assertEquals("icon.ars_n_spells.school.generic", IconCatalog.label("unknown"));
    }
    @Test void everySelectionHasAnAccessibleLabel() {
        for (String id : IconCatalog.IDS) assertEquals("icon.ars_n_spells."+id.replace('/','.'), IconCatalog.label(id));
    }
}
