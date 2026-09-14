package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.data.AffinityData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.Map;

/** Forge capabilities require the loader transformer; exercise replacement in the real runtime. */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class AffinitySnapshotGameTests {
    @GameTest(template = "platform")
    public static void snapshotRemovesMissingAndDisabledSchools(GameTestHelper helper) {
        AffinityData data = new AffinityData();
        data.setLevel("old_pack:moon", 60); data.setLevel("irons_spellbooks:fire", 10);
        var incoming = new java.util.HashMap<>(Map.of("irons_spellbooks:fire", 3, "new_pack:moon", 7));
        data.replaceLevels(incoming); incoming.clear();
        if (data.getLevel("old_pack:moon") != 0 || data.getLevel("irons_spellbooks:fire") != 3 || data.getLevel("new_pack:moon") != 7) {
            helper.fail("replacement must remove omitted keys and own an independent copy"); return;
        }
        data.replaceLevels(Map.of());
        if (!data.getAllLevels().isEmpty()) { helper.fail("disabled snapshot must clear every old level"); return; }
        helper.succeed();
    }
}
