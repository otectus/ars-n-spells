package com.otectus.arsnspells.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opens only the copied QA save, or an explicit localhost QA server. Never selects a user's world. */
@Mod.EventBusSubscriber(modid = "ars_n_spells", value = Dist.CLIENT)
public final class CastingClientSmoke {
    private static final String SAVE = "ANS Casting 334 QA";
    private static int stage, age, stable;
    private static java.util.concurrent.CompletableFuture<Void> prepare;
    private CastingClientSmoke() {}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase != TickEvent.Phase.END || !Boolean.getBoolean("ans.castingSmoke.client"))return;
        if(stage==3)return;
        Minecraft mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;
        if(++age>3600)throw new IllegalStateException("Casting smoke timed out, stage="+stage);
        if(mc.getOverlay()!=null)return;
        if(stage==0) {
            if(mc.screen==null || (!(mc.screen instanceof TitleScreen) && !mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")))return;
            String address=System.getProperty("ans.castingSmoke.address","");
            stage=1;
            if(!address.isBlank()) {
                var info=new net.minecraft.client.multiplayer.ServerData("ANS disposable QA",address,false);
                net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new TitleScreen(),mc,
                    net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),info,false);
            } else {
                if(!java.nio.file.Files.isRegularFile(mc.gameDirectory.toPath().resolve("saves").resolve(SAVE).resolve("level.dat")))
                    throw new IllegalStateException("Missing copied casting QA save");
                mc.createWorldOpenFlows().loadLevel(new TitleScreen(),SAVE);
            }
            return;
        }
        if(mc.player==null)return;
        if(stage==1) {
            if(mc.getSingleplayerServer()!=null) {
                var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
                prepare=java.util.concurrent.CompletableFuture.runAsync(()->com.otectus.arsnspells.gametest.CastingSmokeServer.prepare(server.getPlayerList().getPlayer(id)),server);
            }
            stage=2;return;
        }
        if(prepare!=null) {if(!prepare.isDone())return;prepare.join();}
        if(age%20==0 && stable==0) org.slf4j.LoggerFactory.getLogger(CastingClientSmoke.class).info(
            "ANS_CAST_CLIENT_WAIT health={} mana={} casting={} cooldown={}",mc.player.getHealth(),Loaded.mana(mc),
            io.redspace.ironsspellbooks.player.ClientMagicData.isCasting(),
            io.redspace.ironsspellbooks.player.ClientMagicData.getCooldowns().isOnCooldown(io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell("irons_spellbooks:heal")));
        if(Loaded.passed(mc)) {
            if(++stable<20)return;
            org.slf4j.LoggerFactory.getLogger(CastingClientSmoke.class).info("ANS_CAST_CLIENT_PASS integrated={} health={} mana={} nativeCooldown=true",mc.getSingleplayerServer()!=null,mc.player.getHealth(),Loaded.mana(mc));
            mc.stop();stage=3;
        } else stable=0;
    }
    private static final class Loaded {
        static float mana(Minecraft mc) {return io.redspace.ironsspellbooks.player.ClientMagicData.getPlayerMana();}
        static boolean passed(Minecraft mc) {
            var spell=io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell("irons_spellbooks:heal");
            var data=io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(mc.player);
            return mc.player.getHealth()>10 && mana(mc)==1000-spell.getManaCost(1)
                && !io.redspace.ironsspellbooks.player.ClientMagicData.isCasting() && io.redspace.ironsspellbooks.player.ClientMagicData.getCooldowns().isOnCooldown(spell);
        }
    }
}
