package com.otectus.arsnspells.mixin;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-009 — verifies the Sanctified-presence gating in
 * {@link ArsNSpellsMixinPlugin}.
 *
 * <p>The Sanctified Legacy marker class probe is a NEEDS-VERIFY item: we try
 * three candidate class paths. The test exercises the gating logic itself,
 * not the probe paths.
 */
class ArsNSpellsMixinPluginSanctifiedGatingTest {

    private static ArsNSpellsMixinPlugin plugin(boolean irons, boolean sanctified) throws Exception {
        ArsNSpellsMixinPlugin p = new ArsNSpellsMixinPlugin();
        Field fi = ArsNSpellsMixinPlugin.class.getDeclaredField("ironsPresent");
        fi.setAccessible(true);
        fi.setBoolean(p, irons);
        Field fs = ArsNSpellsMixinPlugin.class.getDeclaredField("sanctifiedPresent");
        fs.setAccessible(true);
        fs.setBoolean(p, sanctified);
        return p;
    }

    @Test
    void sanctifiedMixin_appliesOnlyWhenBothPresent() throws Exception {
        String fqn = "com.otectus.arsnspells.mixin.sanctified.MixinSanctifiedAbstractSpell";
        assertTrue(plugin(true, true).shouldApplyMixin("any.target", fqn),
            "MixinSanctifiedAbstractSpell must apply when both Iron's AND Sanctified are present");
        assertFalse(plugin(true, false).shouldApplyMixin("any.target", fqn),
            "MixinSanctifiedAbstractSpell must NOT apply when only Iron's is present "
                + "(Sanctified adds the canBeCraftedBy method this mixin targets)");
        assertFalse(plugin(false, true).shouldApplyMixin("any.target", fqn),
            "MixinSanctifiedAbstractSpell must NOT apply when only Sanctified is present "
                + "(the target class AbstractSpell is Iron's)");
        assertFalse(plugin(false, false).shouldApplyMixin("any.target", fqn),
            "MixinSanctifiedAbstractSpell must NOT apply when neither is present");
    }

    @Test
    void sanctifiedPresentField_exists() throws NoSuchFieldException {
        Field f = ArsNSpellsMixinPlugin.class.getDeclaredField("sanctifiedPresent");
        f.setAccessible(true);
        // Field exists; type/value not asserted here (verified by the gating test above).
    }

    @Test
    void onLoad_probesSanctifiedMarkerClasses() throws Exception {
        // Just verify onLoad() runs without throwing — actual probe paths are NEEDS-VERIFY.
        ArsNSpellsMixinPlugin p = new ArsNSpellsMixinPlugin();
        Method onLoad = ArsNSpellsMixinPlugin.class.getDeclaredMethod("onLoad", String.class);
        onLoad.setAccessible(true);
        onLoad.invoke(p, "com.otectus.arsnspells.mixin");
        // In a unit-test classpath, Sanctified isn't present — sanctifiedPresent should be false.
        Field fs = ArsNSpellsMixinPlugin.class.getDeclaredField("sanctifiedPresent");
        fs.setAccessible(true);
        assertFalse(fs.getBoolean(p),
            "Sanctified should not be detected in the unit-test classpath");
    }
}
