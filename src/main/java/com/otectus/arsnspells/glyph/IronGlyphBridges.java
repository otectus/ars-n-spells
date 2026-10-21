package com.otectus.arsnspells.glyph;

import com.otectus.arsnspells.compat.IronsCompat;

/** Hands out the {@link IronGlyphBridge} for this install, resolving the Iron's one lazily. */
public final class IronGlyphBridges {

    private static volatile IronGlyphBridge bridge;

    private IronGlyphBridges() {
    }

    public static IronGlyphBridge get() {
        IronGlyphBridge current = bridge;
        if (current == null) {
            synchronized (IronGlyphBridges.class) {
                current = bridge;
                if (current == null) {
                    current = IronsCompat.isLoaded() ? IronsHolder.create() : AbsentIronGlyphBridge.INSTANCE;
                    bridge = current;
                }
            }
        }
        return current;
    }

    /** Only loaded when Iron's is present; the Iron's-backed class is named nowhere else in common code. */
    private static final class IronsHolder {
        private IronsHolder() {
        }

        static IronGlyphBridge create() {
            return new com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridgeImpl();
        }
    }
}
