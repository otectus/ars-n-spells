package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellCaster;
import com.hollingsworth.arsnouveau.common.spell.method.MethodProjectile;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistration;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.ArrayList;

/** Opt-in fixture for the isolated client/dedicated-server editor smoke. */
@EventBusSubscriber(modid = "ars_n_spells")
public final class GlyphBrowserFixture {
    private GlyphBrowserFixture() {}
    public static ImportedIronEffect bolt(int level) {
        return ImportedGlyphRegistration.effect(new ImportedGlyphKey(ResourceLocation.parse("irons_spellbooks:firebolt"), level));
    }
    @SubscribeEvent public static void seedRemote(PlayerTickEvent.Post event) {
        if (Boolean.getBoolean("ans.browserSmoke.server") && event.getEntity() instanceof ServerPlayer player && player.tickCount == 30) seed(player);
    }
    public record CastResult(double paid, float healthRestored, boolean nativeCooldown) {}

    /** The same ordinary saved archmage book is exercised after each local display change. */
    public static void equipCastFixture(ServerPlayer player) {
        var saved = ItemsRegistry.ARCHMAGE_SPELLBOOK.get().getDefaultInstance();
        new SpellCaster().setSpell(new Spell(com.hollingsworth.arsnouveau.common.spell.method.MethodSelf.INSTANCE,
            ImportedGlyphGameTests.glyph("heal", 3))).saveToStack(saved);
        player.setItemInHand(InteractionHand.MAIN_HAND, saved);
    }

    public static CastResult castSavedBook(ServerPlayer player) {
        ImportedGlyphGameTests.clearCooldowns(player);
        IronsProxyCastDriver.resetCastingState(player);
        ImportedGlyphGameTests.fund(player);
        // Give each cast the same two-point wound, including after normal school progression.
        float wounded = player.getMaxHealth() - 2.0f;
        player.setHealth(wounded);
        double before = ImportedGlyphGameTests.pools(player);
        var caster = com.hollingsworth.arsnouveau.api.registry.SpellCasterRegistry.from(player.getMainHandItem());
        caster.castSpell(player.level(), player, InteractionHand.MAIN_HAND, null);
        return new CastResult(before - ImportedGlyphGameTests.pools(player), player.getHealth() - wounded,
            IronsProxyCastDriver.onNativeCooldown(player, "irons_spellbooks:heal"));
    }

    public static void seed(ServerPlayer player) {
        var cap = CapabilityRegistry.getPlayerDataCap(player);
        cap.setKnownGlyphs(new ArrayList<>());
        player.setGameMode(GameType.SURVIVAL);
        if (bolt(1).isEnabled()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, bolt(1).getGlyph().getDefaultInstance());
            bolt(1).getGlyph().use(player.level(), player, InteractionHand.MAIN_HAND);
            if (!cap.getKnownGlyphs().contains(bolt(1))) throw new IllegalStateException("Physical glyph did not teach Firebolt I");
        }
        var learned = new ArrayList<AbstractSpellPart>();
        learned.add(MethodProjectile.INSTANCE);
        // A deterministic upgrade fixture with real catalog IDs, gaps and dormant reserved levels.
        for (var e : ImportedGlyphRegistration.effects().entrySet()) {
            if (e.getKey().baseLevel() % 2 == 1 || e.getKey().baseLevel() == 10) learned.add(e.getValue());
        }
        cap.setKnownGlyphs(learned);
        CapabilityRegistry.EventHandler.syncPlayerCap(player);
        var book = ItemsRegistry.ARCHMAGE_SPELLBOOK.get().getDefaultInstance();
        new SpellCaster().setSpell(new Spell(MethodProjectile.INSTANCE)).saveToStack(book);
        player.setItemInHand(InteractionHand.MAIN_HAND, book);
        player.inventoryMenu.broadcastChanges();
    }
}
