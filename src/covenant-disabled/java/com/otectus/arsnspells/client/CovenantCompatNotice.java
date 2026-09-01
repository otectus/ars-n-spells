package com.otectus.arsnspells.client;

import com.otectus.arsnspells.ArsNSpells;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Audit D3: shows a once-per-session chat notice on login when
 * {@link ArsNSpellsClient} detected an untested Covenant of the Seven version.
 * The HUD mixin ({@code MixinResourceBarOverlay}, {@code require = 0}) degrades
 * SILENTLY on version drift — the aura bar falls back to Covenant's 2,000,000
 * divisor — and the setup-time log warning is invisible to the players who
 * actually see the wrongly-scaled bar.
 */
@Mod.EventBusSubscriber(modid = ArsNSpells.MODID, value = Dist.CLIENT)
public final class CovenantCompatNotice {

    private static boolean shownThisSession = false;

    private CovenantCompatNotice() {}

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        String untested = ArsNSpellsClient.getUntestedCovenantVersion();
        if (untested == null || shownThisSession || event.getPlayer() == null) {
            return;
        }
        shownThisSession = true;
        event.getPlayer().displayClientMessage(
            Component.translatable("message.ars_n_spells.covenant_hud_untested",
                untested, ArsNSpellsClient.getTestedCovenantVersion())
                .withStyle(ChatFormatting.GOLD),
            false);
    }
}
