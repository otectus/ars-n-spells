package com.otectus.arsnspells.rituals;

import com.hollingsworth.arsnouveau.api.ritual.AbstractRitual;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossCastingHandler;
import com.otectus.arsnspells.spell.CrossModSpell;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;

/**
 * Binds an exported Ars spell carried by a real Iron's scroll onto a real Iron's
 * spellbook. Second leg of the Ars &rarr; scroll &rarr; spellbook workflow.
 *
 * Usage:
 *  <ol>
 *    <li>Drop exactly one carrier scroll (an Iron's scroll exported via the
 *        Spell Loom or {@code /ans export_to_irons_scroll}) near the
 *        brazier.</li>
 *    <li>Drop exactly one Iron's spellbook in the same area.</li>
 *    <li>Activate the ritual. On success the scroll's Ars spell is appended to
 *        the book's cross-cast sidecar (and mirrored into Iron's native wheel
 *        via a proxy slot) and the scroll is consumed.</li>
 *  </ol>
 *
 * Validation runs fully before any item mutation. The bound entry coexists with
 * the book's native spell container and casts through Iron's native flow via
 * the ars_cross proxy spells.
 */
public class SpellbookBindingRitual extends AbstractRitual {
    public static final String REGISTRY_PATH = "spellbook_binding";
    private static final String LANG_PREFIX = "ritual.ars_n_spells.spellbook_binding.";
    private static final int SEARCH_RADIUS = 3;

    @Override
    protected void tick() {}

    @Override
    public void onEnd() {
        Level level = getWorld();
        BlockPos pos = getPos();
        if (level == null || pos == null || level.isClientSide()) {
            return;
        }

        AABB area = new AABB(pos).inflate(SEARCH_RADIUS);
        List<ItemEntity> entities = level.getEntitiesOfClass(ItemEntity.class, area,
            e -> e.isAlive() && !e.getItem().isEmpty());

        if (entities.isEmpty()) {
            RitualFeedback.error(level, pos, LANG_PREFIX + "error.empty_range");
            return;
        }

        SpellbookBindingInputs inputs = SpellbookBindingInputs.classify(entities);

        if (inputs.carrierScrolls.isEmpty()) {
            RitualFeedback.error(level, pos, LANG_PREFIX + "error.no_scroll");
            return;
        }
        if (inputs.carrierScrolls.size() > 1) {
            RitualFeedback.error(level, pos, LANG_PREFIX + "error.multiple_scrolls",
                inputs.carrierScrolls.size(), InscriptionInputs.joinNames(inputs.carrierScrolls));
            return;
        }
        if (inputs.spellbooks.isEmpty()) {
            RitualFeedback.error(level, pos, LANG_PREFIX + "error.no_book");
            return;
        }
        if (inputs.spellbooks.size() > 1) {
            RitualFeedback.error(level, pos, LANG_PREFIX + "error.multiple_books",
                inputs.spellbooks.size(), InscriptionInputs.joinNames(inputs.spellbooks));
            return;
        }
        // Strict: refuse rather than risk binding the wrong stack when extra
        // items share the brazier, mirroring the uninscribe ritual's philosophy.
        if (!inputs.other.isEmpty()) {
            RitualFeedback.error(level, pos, LANG_PREFIX + "error.unexpected_items",
                InscriptionInputs.joinNames(inputs.other));
            return;
        }

        ItemEntity scrollEntity = inputs.carrierScrolls.get(0);
        ItemEntity bookEntity = inputs.spellbooks.get(0);
        ItemStack scrollStack = scrollEntity.getItem();
        ItemStack bookStack = bookEntity.getItem();

        Optional<CrossModSpell> entryOpt = IronsBookBindingUtil.extractSingleEntry(scrollStack);
        if (entryOpt.isEmpty() || entryOpt.get().arsSpellTag().isEmpty()) {
            // Classification said this was a valid carrier but a second read
            // failed -- a transient parse problem, not a validation error.
            RitualFeedback.error(level, pos, LANG_PREFIX + "error.scroll_parse_failed",
                scrollStack.getHoverName().getString());
            return;
        }
        CrossModSpell entry = entryOpt.get();
        CompoundTag arsTag = entry.arsSpellTag().get();

        if (!AnsConfig.ALLOW_ARS_SPELLS_IN_IRONS_SPELLBOOKS.get()) {
            RitualFeedback.error(level, pos, LANG_PREFIX + "error.disabled");
            return;
        }

        // Validation complete -- mutation begins here. The util allocates a
        // native-wheel proxy slot and mirrors the entry (with the scroll's chosen
        // display name/nature/icon) into Iron's container.
        int maxCap = AnsConfig.MAX_ARS_CROSS_SPELLS_PER_IRONS_SPELLBOOK.get();
        IronsBookBindingUtil.AppendResult result =
            IronsBookBindingUtil.appendArsSpellToBook(bookStack, arsTag,
                entry.customName().orElse(null),
                entry.nature().orElse(null),
                entry.iconSymbol().orElse(null),
                maxCap);
        switch (result) {
            case ADDED:
                break;
            case DUPLICATE:
                RitualFeedback.error(level, pos, LANG_PREFIX + "error.duplicate",
                    bookStack.getHoverName().getString());
                return;
            case BOOK_FULL:
                RitualFeedback.error(level, pos, LANG_PREFIX + "error.book_full",
                    bookStack.getHoverName().getString(),
                    IronsBookBindingUtil.effectiveProxyCeiling(maxCap));
                return;
            case FAILED:
            default:
                RitualFeedback.error(level, pos, LANG_PREFIX + "error.scroll_parse_failed",
                    scrollStack.getHoverName().getString());
                return;
        }
        bookEntity.setItem(bookStack);
        // Consume ONE scroll, not the whole entity — Iron's scrolls stack to 16,
        // and discarding a stacked carrier destroyed the extras.
        scrollStack.shrink(1);
        if (scrollStack.isEmpty()) {
            scrollEntity.discard();
        } else {
            scrollEntity.setItem(scrollStack);
        }

        playBindEffects(level, pos);
        RitualFeedback.success(level, pos, LANG_PREFIX + "success", spellLabel(arsTag));
    }

    private void playBindEffects(Level level, BlockPos pos) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 1.2;
        double cz = pos.getZ() + 0.5;
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ENCHANT, cx, cy, cz, 60, 0.6, 0.8, 0.6, 0.2);
            server.sendParticles(ParticleTypes.WITCH, cx, cy, cz, 12, 0.4, 0.5, 0.4, 0.05);
        }
        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE,
            SoundSource.BLOCKS, 0.8f, 0.9f);
    }

    private String spellLabel(CompoundTag arsTag) {
        try {
            Spell spell = CrossCastingHandler.decodeArsSpell(arsTag);
            return ArsSpellExportUtil.buildDisplayLabel(spell);
        } catch (Exception ignored) {
            return "Ars Spell";
        }
    }

    @Override
    public ResourceLocation getRegistryName() {
        return ResourceLocation.fromNamespaceAndPath("ars_n_spells", REGISTRY_PATH);
    }
}
