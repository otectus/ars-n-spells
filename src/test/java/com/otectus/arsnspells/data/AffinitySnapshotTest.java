package com.otectus.arsnspells.data;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AffinitySnapshotTest {
    @Test void replacementRemovesSchoolsOmittedByServer() {
        AffinityData data = new AffinityData();
        data.setLevel("old_pack:moon", 60);
        data.setLevel("irons_spellbooks:fire", 10);
        data.replaceLevels(Map.of("irons_spellbooks:fire", 3, "new_pack:moon", 7));
        assertEquals(0, data.getLevel("old_pack:moon"));
        assertEquals(3, data.getLevel("irons_spellbooks:fire"));
        assertEquals(7, data.getLevel("new_pack:moon"));
    }
    @Test void disabledSnapshotClearsEveryClientLevel() {
        AffinityData data = new AffinityData();
        data.setLevel("fire", 80);
        data.replaceLevels(Map.of());
        assertTrue(data.getAllLevels().isEmpty());
    }
    @Test void snapshotsCannotShareMutableBackingMap() {
        var incoming = new java.util.HashMap<String,Integer>();
        incoming.put("pack:custom", 30);
        AffinityData data = new AffinityData(); data.replaceLevels(incoming);
        incoming.clear();
        assertEquals(30, data.getLevel("pack:custom"));
        assertThrows(UnsupportedOperationException.class, () -> data.getAllLevels().clear());
    }
}
