package com.otectus.arsnspells.config;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audits ANS-007 and ANS-008: the settings snapshot carries every SERVER value the client HUD reads,
 * and a ring hides the mana bars only while its resource route is actually active.
 */
class ClientPolicyTest {

    @Test
    void snapshotRoundTripCarriesThePresentationPolicy() {
        var policy = new AnsSettings.ClientPolicy("irons", false, true, false);
        var snapshot = new AnsSettings.Snapshot(Map.of("debug_mode", "false"), "hybrid", true, false, "", "", policy);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            snapshot.write(buffer);
            var read = AnsSettings.Snapshot.read(buffer);
            assertEquals(snapshot, read);
            assertEquals(policy, read.policy());
            assertEquals(0, buffer.readableBytes(), "the reader consumes exactly what the writer wrote");
        } finally {
            buffer.release();
        }
    }

    @Test
    void hybridBarChoiceIsNormalized() {
        assertTrue(new AnsSettings.ClientPolicy("IRONS", true, true, true).hybridShowsIrons());
        assertFalse(new AnsSettings.ClientPolicy("ars", true, true, true).hybridShowsIrons());
        assertFalse(new AnsSettings.ClientPolicy("bogus", true, true, true).hybridShowsIrons(), "anything else is the Ars bar");
        assertFalse(new AnsSettings.ClientPolicy(null, true, true, true).hybridShowsIrons());
    }

    @Test
    void ringHidesManaOnlyWhileItsRouteIsActive() {
        var on = new AnsSettings.ClientPolicy("ars", true, true, true);
        var auraOff = new AnsSettings.ClientPolicy("ars", true, true, false);
        var lpOff = new AnsSettings.ClientPolicy("ars", true, false, true);
        var bothOff = new AnsSettings.ClientPolicy("ars", true, false, false);
        var hideOff = new AnsSettings.ClientPolicy("ars", false, true, true);
        // The audit's case: Virtue worn, aura integration off -> casts use mana, the bar stays.
        assertFalse(RingHudPolicy.hidesManaBars(auraOff, true, false, true));
        assertTrue(RingHudPolicy.hidesManaBars(on, true, false, true), "Virtue with aura on hides");
        assertTrue(RingHudPolicy.hidesManaBars(on, true, true, false), "Cursed with LP on hides");
        assertTrue(RingHudPolicy.hidesManaBars(auraOff, true, true, false), "Cursed with LP on, aura off, hides");
        assertFalse(RingHudPolicy.hidesManaBars(lpOff, true, true, false), "Cursed with LP off keeps the bar");
        assertTrue(RingHudPolicy.hidesManaBars(lpOff, true, false, true), "Virtue with aura on, LP off, hides");
        // Both rings cancel each other (neither is "only"); neither ring is no ring.
        assertFalse(RingHudPolicy.hidesManaBars(on, true, false, false));
        for (var policy : new AnsSettings.ClientPolicy[]{on, auraOff, lpOff}) {
            assertFalse(RingHudPolicy.hidesManaBars(policy, true, false, false));
        }
        assertFalse(RingHudPolicy.hidesManaBars(bothOff, true, true, false));
        assertFalse(RingHudPolicy.hidesManaBars(bothOff, true, false, true));
        // The player's own preference, and no ring mod at all.
        assertFalse(RingHudPolicy.hidesManaBars(hideOff, true, true, false));
        assertFalse(RingHudPolicy.hidesManaBars(hideOff, true, false, true));
        assertFalse(RingHudPolicy.hidesManaBars(on, false, true, false));
        assertFalse(RingHudPolicy.hidesManaBars(null, true, true, false));
    }
}
