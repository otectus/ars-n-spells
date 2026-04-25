package com.otectus.arsnspells.rituals;

import com.hollingsworth.arsnouveau.api.ritual.AbstractRitual;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellCaster;
import com.otectus.arsnspells.spell.CrossCastingHandler;
import com.otectus.arsnspells.spell.CrossSpellType;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Spell Transcription ritual: stamps a source spell (Ars spell parchment or
 * Iron's scroll) onto a target item as a cross-mod spell inscription. The
 * target can then be right-clicked to cast the inscribed spell through the
 * CrossCastingHandler, consuming mana according to the active unification mode.
 *
 * Usage:
 * 1. Drop a spell source (Ars spell parchment/focus or Iron's scroll) near
 *    the ritual brazier.
 * 2. Drop a target item to receive the inscription (any item works).
 * 3. Activate the ritual with a source jar in range.
 * 4. On completion, the source is consumed and the target gains the
 *    arsnspells:cross_spells NBT tag.
 */
public class SpellTranscriptionRitual extends AbstractRitual {
    public static final String REGISTRY_PATH = "spell_transcription";
    private static final int SEARCH_RADIUS = 3;

    @Override
    protected void tick() {}

    @Override
    public void onEnd() {
        if (this.getWorld() == null || this.getWorld().isClientSide() || this.getPos() == null) {
            return;
        }

        AABB area = new AABB(this.getPos()).inflate(SEARCH_RADIUS);
        List<ItemEntity> entities = this.getWorld().getEntitiesOfClass(ItemEntity.class, area,
            e -> e.isAlive() && !e.getItem().isEmpty());

        if (entities.isEmpty()) {
            feedback("Spell Transcription requires a source and a target item.");
            return;
        }

        ItemEntity sourceEntity = null;
        Source source = null;
        List<ItemEntity> targets = new ArrayList<>();

        for (ItemEntity entity : entities) {
            Source parsed = readSource(entity.getItem());
            if (parsed != null && source == null) {
                source = parsed;
                sourceEntity = entity;
            } else {
                targets.add(entity);
            }
        }

        if (source == null) {
            feedback("Spell Transcription: no spell source found (need a filled Ars spell parchment/focus or Iron's scroll).");
            return;
        }
        if (targets.isEmpty()) {
            feedback("Spell Transcription: no target item to inscribe onto.");
            return;
        }

        ItemEntity targetEntity = targets.get(0);
        ItemStack targetStack = targetEntity.getItem();

        switch (source.type) {
            case ARS_NOUVEAU:
                CrossCastingHandler.addCrossModSpell(targetStack, source.arsSpell);
                break;
            case IRONS_SPELLBOOKS:
                CrossCastingHandler.addCrossModSpell(targetStack, source.spellId, source.spellLevel,
                    CrossSpellType.IRONS_SPELLBOOKS);
                break;
        }

        targetEntity.setItem(targetStack);
        sourceEntity.discard();

        feedback(ChatFormatting.GREEN + "Spell inscribed: " + source.label());
    }

    private Source readSource(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        // Try Ars Nouveau first: SpellCaster covers spellbooks/foci, then fall
        // back to reading the root tag directly for spell parchment.
        try {
            Spell arsSpell = new SpellCaster(stack).getSpell();
            if (arsSpell != null && arsSpell.recipe != null && !arsSpell.recipe.isEmpty()) {
                return Source.ars(arsSpell);
            }
        } catch (Throwable ignored) {
        }
        if (stack.hasTag()) {
            try {
                Spell parchmentSpell = Spell.fromTag(stack.getOrCreateTag());
                if (parchmentSpell != null && parchmentSpell.recipe != null && !parchmentSpell.recipe.isEmpty()) {
                    return Source.ars(parchmentSpell);
                }
            } catch (Throwable ignored) {
            }
        }

        // Try Iron's Spellbooks scroll.
        try {
            ISpellContainer container = ISpellContainer.get(stack);
            if (container != null) {
                SpellData data = container.getSpellAtIndex(0);
                if (data != null) {
                    AbstractSpell spell = data.getSpell();
                    if (spell != null && spell.getSpellId() != null) {
                        ResourceLocation id = ResourceLocation.tryParse(spell.getSpellId());
                        if (id != null) {
                            return Source.irons(id, Math.max(1, data.getLevel()));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    @Nullable
    private Player findNearestPlayer(int radius) {
        if (this.getWorld() == null || this.getPos() == null) return null;
        AABB area = new AABB(this.getPos()).inflate(radius);
        return this.getWorld().getEntitiesOfClass(Player.class, area).stream()
            .min(Comparator.comparingDouble(p -> p.distanceToSqr(
                this.getPos().getX() + 0.5, this.getPos().getY() + 0.5, this.getPos().getZ() + 0.5)))
            .orElse(null);
    }

    private void feedback(String message) {
        Player player = findNearestPlayer(8);
        if (player != null) {
            player.displayClientMessage(Component.literal(message), false);
        }
    }

    @Override
    public ResourceLocation getRegistryName() {
        return new ResourceLocation("ars_n_spells", REGISTRY_PATH);
    }

    private static final class Source {
        final CrossSpellType type;
        final Spell arsSpell;
        final ResourceLocation spellId;
        final int spellLevel;

        private Source(CrossSpellType type, Spell arsSpell, ResourceLocation spellId, int spellLevel) {
            this.type = type;
            this.arsSpell = arsSpell;
            this.spellId = spellId;
            this.spellLevel = spellLevel;
        }

        static Source ars(Spell spell) {
            return new Source(CrossSpellType.ARS_NOUVEAU, spell, null, 0);
        }

        static Source irons(ResourceLocation id, int level) {
            return new Source(CrossSpellType.IRONS_SPELLBOOKS, null, id, level);
        }

        String label() {
            if (type == CrossSpellType.ARS_NOUVEAU) {
                return "Ars Nouveau spell";
            }
            return spellId != null ? spellId.toString() + " (level " + spellLevel + ")" : "Iron's spell";
        }
    }
}
