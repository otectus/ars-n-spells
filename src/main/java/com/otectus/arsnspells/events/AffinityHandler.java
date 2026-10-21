package com.otectus.arsnspells.events;

import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.affinity.AffinityType;
import com.otectus.arsnspells.util.SpellAnalysis;
import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.network.AffinitySyncPacket;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANS school affinity for Ars casts.
 *
 * <p>3.4.0: credited once per successful root cast, at the resolver's expenditure
 * ({@code MixinSpellResolverMana}), instead of on {@code SpellCastEvent}. That event is posted
 * before the form runs, so a Touch that hit nothing, a cast another listener vetoed after this
 * one, or a cast ANS then refused for payment all trained the school. The resolver only spends
 * when the form reports {@code SUCCESS}, which is the same boundary the payment and the
 * category cooldown commit at.
 */
public class AffinityHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AffinityHandler.class);

    /** One root cast's affinity credit; called at the cast's success boundary. */
    public static void credit(ServerPlayer player, com.hollingsworth.arsnouveau.api.spell.Spell spell,
                              com.hollingsworth.arsnouveau.api.spell.SpellContext context) {
        if (!AnsConfig.ENABLE_AFFINITY_SYSTEM.get()) {
            return;
        }
        {
            // The cast's own context decides: an explicit school stamped by the cross-cast
            // replaces the glyph school, and a native cast carries none.
            String key = SpellAnalysis.analyze(spell, context).schoolKey();
            if (!com.otectus.arsnspells.util.SchoolKeys.GENERIC.equals(key)
                && com.otectus.arsnspells.util.SchoolKeys.builtin(key).isGeneric()) {
                player.getCapability(AffinityData.AFFINITY_DATA).ifPresent(data -> {
                    data.addLevel(key, 1);
                    PacketHandler.sendToClient(new AffinitySyncPacket(key, data.getLevel(key)), player);
                });
                return;
            }
            String school = com.otectus.arsnspells.util.SchoolKeys.builtin(key).id();
            if (!"generic".equals(school)) {
                player.getCapability(AffinityData.AFFINITY_DATA).ifPresent(data -> {
                    AffinityType type;
                    try {
                        // ANS-HIGH-023: narrow to IllegalArgumentException only. The previous
                        // broad catch(Exception) silently swallowed packet-send failures too,
                        // leaving the server-side affinity incremented while the client HUD
                        // stayed stale (with no log line to diagnose the desync).
                        type = AffinityType.valueOf(school.toUpperCase());
                    } catch (IllegalArgumentException unmappedSchool) {
                        LOGGER.debug("Unmapped affinity school: {}", school);
                        return;
                    }
                    data.addLevel(type, 1);
                    // High-Fidelity Sync: Push mirroring to client player for immediate UI updates.
                    // If the packet send throws, it will propagate to Forge's network layer
                    // where it is logged — that is the right place for the diagnostic.
                    PacketHandler.sendToClient(new AffinitySyncPacket(type, data.getLevel(type)), player);
                });
            }
        }
    }
}
