package com.otectus.arsnspells.rituals;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import com.otectus.arsnspells.spell.IronsSpellbookBinder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Binds an exported Ars spell carried by a real Iron's scroll onto a real Iron's
 * spellbook. Second leg of the Ars &rarr; scroll &rarr; spellbook workflow.
 *
 * Usage:
 *  <ol>
 *    <li>Drop exactly one carrier scroll (a real Iron's scroll woven at the Spell
 *        Loom, or exported with {@code /ans export_to_irons_scroll}) within three
 *        blocks of the brazier.</li>
 *    <li>Drop exactly one Iron's spellbook in the same area, with nothing else in
 *        range -- any other item aborts the ritual rather than risk binding the
 *        wrong stack.</li>
 *    <li>Light the brazier by right-clicking it empty-handed. On success the
 *        scroll's Ars spell is appended to the book's cross-cast sidecar and one
 *        scroll is consumed.</li>
 *  </ol>
 *
 * Validation runs fully before any item mutation. The bound entry coexists with
 * the book's native {@code ISB_Spells} container and appears as its own entry in
 * Iron's native spell wheel, cast through the ANS cross-cast pipeline.
 */
public class SpellbookBindingRitual extends AnsRitual {
    public static final String REGISTRY_PATH = "spellbook_binding";
    private static final String LANG_PREFIX = "ritual.ars_n_spells.spellbook_binding.";
    private static final int SEARCH_RADIUS = 3;

    @Override
    public void onEnd() {
        Level level = getWorld();
        BlockPos pos = getPos();
        if (level == null || pos == null || level.isClientSide()) {
            return;
        }

        // Feature-state checks come before input validation, which reads backwards until you see
        // why: with binding switched off the player used to be told "unexpected item(s) in range"
        // or "drop a carrier scroll and a spell book" -- troubleshooting advice for a ritual that
        // was never going to run -- and never learned the server had the feature disabled.
        if (!AnsConfig.ALLOW_ARS_SPELLS_IN_IRONS_SPELLBOOKS.get()) {
            error(LANG_PREFIX + "error.disabled");
            return;
        }

        AABB area = new AABB(pos).inflate(SEARCH_RADIUS);
        List<ItemEntity> entities = level.getEntitiesOfClass(ItemEntity.class, area,
            e -> e.isAlive() && !e.getItem().isEmpty());

        if (entities.isEmpty()) {
            error(LANG_PREFIX + "error.empty_range");
            return;
        }

        SpellbookBindingInputs inputs = SpellbookBindingInputs.classify(entities);

        if (inputs.carrierScrolls.isEmpty()) {
            error(LANG_PREFIX + "error.no_scroll");
            return;
        }
        if (inputs.carrierScrolls.size() > 1) {
            error(LANG_PREFIX + "error.multiple_scrolls",
                inputs.carrierScrolls.size(), InscriptionInputs.joinNames(inputs.carrierScrolls));
            return;
        }
        if (inputs.spellbooks.isEmpty()) {
            error(LANG_PREFIX + "error.no_book");
            return;
        }
        if (inputs.spellbooks.size() > 1) {
            error(LANG_PREFIX + "error.multiple_books",
                inputs.spellbooks.size(), InscriptionInputs.joinNames(inputs.spellbooks));
            return;
        }
        // Strict: refuse rather than risk binding the wrong stack when extra
        // items share the brazier, mirroring the uninscribe ritual's philosophy.
        if (!inputs.other.isEmpty()) {
            error(LANG_PREFIX + "error.unexpected_items",
                InscriptionInputs.joinNames(inputs.other));
            return;
        }

        ItemEntity scrollEntity = inputs.carrierScrolls.get(0);
        ItemEntity bookEntity = inputs.spellbooks.get(0);
        ItemStack scrollStack = scrollEntity.getItem();
        ItemStack bookStack = bookEntity.getItem();

        // Whoever the feedback goes to is also whoever the bind is credited to: the ritual has
        // no owning player of its own, so resolving it once here keeps the message, the
        // diagnostics and the advancement from ever disagreeing (audit H4).
        ServerPlayer credited =
            recipient() instanceof ServerPlayer player ? player : null;

        // Read the entry for the success label only. The binder is the authority on whether
        // this scroll may be bound; a label we cannot build degrades to a generic one.
        CompoundTag arsTag = IronsBookBindingUtil.extractSingleEntry(scrollStack)
            .map(entry -> entry.getCompound(com.otectus.arsnspells.spell.CrossCastNbt.TAG_ARS_SPELL))
            .orElseGet(CompoundTag::new);

        // Validation and mutation both live in the shared binder, so the ritual, the command
        // and Iron's Inscription Table cannot drift apart on what a bind is allowed to do.
        // Consumption, feedback wording and the advancement stay here.
        int maxCap = AnsConfig.MAX_ARS_CROSS_SPELLS_PER_IRONS_SPELLBOOK.get();
        IronsSpellbookBinder.BindResult result = IronsSpellbookBinder.bind(
            credited, scrollStack, bookStack, IronsSpellbookBinder.Caller.RITUAL);
        switch (result) {
            case ADDED:
                break;
            case DUPLICATE:
                error(LANG_PREFIX + "error.duplicate",
                    bookStack.getHoverName().getString());
                return;
            case BOOK_FULL:
                error(LANG_PREFIX + "error.book_full",
                    bookStack.getHoverName().getString(),
                    IronsBookBindingUtil.effectiveProxyCeiling(maxCap));
                return;
            case DISABLED:
                error(LANG_PREFIX + "error.disabled");
                return;
            case NO_BOOK:
                error(LANG_PREFIX + "error.no_book");
                return;
            case UNCASTABLE:
                // An unreadable payload is refused before anything is consumed: binding it
                // would produce a wheel entry that selects and does nothing.
                error(LANG_PREFIX + "error.uncastable",
                    scrollStack.getHoverName().getString());
                return;
            case NOT_A_CARRIER:
            case INVALID_CARRIER:
                // Classification said this was a valid carrier but the binder's own read
                // failed -- a transient parse problem, not a validation error.
                error(LANG_PREFIX + "error.scroll_parse_failed",
                    scrollStack.getHoverName().getString());
                return;
            case FAILED:
            default:
                // The book refused the entry (native container write failed) and the binder
                // has already rolled the sidecar back. Reported against the BOOK -- the
                // scroll parsed fine, so "scroll parse failed" sent players to re-export a
                // scroll that was never the problem.
                error(LANG_PREFIX + "error.bind_failed",
                    bookStack.getHoverName().getString());
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
        success(LANG_PREFIX + "success", spellLabel(arsTag));

        if (credited != null) {
            com.otectus.arsnspells.util.AdvancementUtil.grant(credited, "bind_spell");
        }
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
            Spell spell = Spell.fromTag(arsTag);
            return ArsSpellExportUtil.buildDisplayLabel(spell);
        } catch (Exception ignored) {
            return "Ars Spell";
        }
    }

    @Override
    public ResourceLocation getRegistryName() {
        return new ResourceLocation("ars_n_spells", REGISTRY_PATH);
    }
}
