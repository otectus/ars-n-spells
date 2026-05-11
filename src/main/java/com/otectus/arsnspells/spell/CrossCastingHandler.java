package com.otectus.arsnspells.spell;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stripped-down cross-cast handler. The 1.20.1 implementation hosted the
 * right-click intercept, the cross-cast cost-event hook into AN's
 * {@code SpellCostCalcEvent}, and the player-tick context cleanup. The
 * data-component side of the inscription pipeline is fully ported (see
 * {@link CrossModSpellComponents}, {@link CrossModSpellList},
 * {@link ModDataComponents}); the AN-side cast invocation needs Phase 3
 * work because {@code Spell.fromTag}, {@code SpellCaster.castSpell}, and
 * the public field accesses on {@code SpellCostCalcEvent} all changed
 * shape between 4.12 and 5.x.
 *
 * Public {@link #addCrossModSpell} entry points stay live so the rituals
 * (transcription / uninscription) keep their current contract.
 *
 * Phase 3: restore {@code @EventBusSubscriber(modid = ArsNSpells.MODID)}
 * once the right-click / SpellCostCalcEvent / player-tick handlers regain
 * their @SubscribeEvent methods. NeoForge 1.21.1 rejects EventBus
 * registration when a class has no @SubscribeEvent methods.
 */
public class CrossCastingHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrossCastingHandler.class);

    /** Inscribe an Ars Nouveau spell onto a stack. */
    public static void addCrossModSpell(ItemStack stack, Spell spell) {
        if (spell == null) return;
        // TODO(Phase 11): serialise via AN 5.x Spell.CODEC instead of the deprecated fromTag/serialize pair.
        CrossModSpellComponents.addCrossModSpell(
            stack,
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "spell"),
            1,
            CrossSpellType.ARS_NOUVEAU,
            null
        );
    }

    /** Inscribe a foreign-mod spell by id + level + type. */
    public static void addCrossModSpell(ItemStack stack, ResourceLocation spellId, int spellLevel,
                                        CrossSpellType type) {
        addCrossModSpell(stack, spellId, spellLevel, type, null);
    }

    public static void addCrossModSpell(ItemStack stack, ResourceLocation spellId, int spellLevel,
                                        CrossSpellType type, CompoundTag arsSpellTag) {
        CrossModSpellComponents.addCrossModSpell(stack, spellId, spellLevel, type, arsSpellTag);
    }

    /** Strip every inscription artifact from a stack. */
    public static void clearCrossModSpells(ItemStack stack) {
        CrossModSpellComponents.clear(stack);
    }
}
