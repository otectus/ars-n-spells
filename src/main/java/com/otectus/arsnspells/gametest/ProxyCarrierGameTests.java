package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectBreak;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import com.otectus.arsnspells.spell.irons.ProxyCarrierResolver;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** R02: two actual native books sharing pool 1 must never resolve to each other. */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class ProxyCarrierGameTests {
    @GameTest(template = "platform")
    public static void twoNativeBooksSharingPoolRefuseAmbiguity(GameTestHelper helper) {
        if (OptionalModGate.skipIfAbsent(helper, IronsCompat.MODID)) return;
        var item = CrossCastGameTests.findIronsSpellBook();
        if (item == null) { helper.fail("native book fixture missing"); return; }
        ItemStack alpha = new ItemStack(item), beta = new ItemStack(item);
        var first = IronsBookBindingUtil.appendArsSpellToBook(alpha, new Spell(MethodSelf.INSTANCE, EffectHeal.INSTANCE).serialize(), "Alpha", "fire", "spell/heal", -1);
        var second = IronsBookBindingUtil.appendArsSpellToBook(beta, new Spell(MethodSelf.INSTANCE, EffectBreak.INSTANCE).serialize(), "Beta", "ice", "spell/break_block", -1);
        if (!first.wasAdded() || !second.wasAdded()) { helper.fail("both distinct real native books must bind pool 1"); return; }
        if (ProxyCarrierResolver.unique(1, alpha, beta) != null) { helper.fail("context-free lookup must refuse two matching carriers"); return; }
        if (ProxyCarrierResolver.unique(1, alpha, alpha).stack() != alpha) { helper.fail("the same physical stack is not ambiguous"); return; }
        if (ProxyCarrierResolver.from(beta, 1).stack() != beta) { helper.fail("exact hovered/native carrier must resolve itself"); return; }
        if (ProxyCarrierResolver.from(ItemStack.EMPTY, 1) != null) { helper.fail("missing native carrier must not invent a match"); return; }
        helper.succeed();
    }
}
