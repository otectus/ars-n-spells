package com.otectus.arsnspells.progression;

import com.otectus.arsnspells.contract.AnsModifierIds;
import com.otectus.arsnspells.data.ProgressionData;
import com.otectus.arsnspells.modifier.AnsFeatureCleanup;
import com.otectus.arsnspells.modifier.AnsModifierIdMapper;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one owner of the cross-mod school progression modifier: apply, remove and replay
 * (audit V07).
 *
 * <p><b>The defect this closes.</b> One progression bonus had two identities. The Iron's-side
 * handler wrote it as {@code ars_n_spells:cross_mod_school_progression} through
 * {@link ProgressionAttributes}; the Ars-side handler wrote it as
 * {@code ars_n_spells:progression_element_xp} from its own private constant, and each removed
 * only the id it wrote. Cast an Ars spell and then an Iron's spell of the same school and both
 * modifiers sat on {@code <school>_spell_power} at once, so the bonus was paid twice and half
 * of it survived every removal path. Both call sites now route here, and applying removes
 * <em>every</em> historical id before adding the single canonical one.
 *
 * <p><b>Cast counts are never touched.</b> Only the transient modifier is rebuilt; the
 * {@code progression} attachment is the source of truth for the bonus and is read, never
 * written, by anything in this class.
 *
 * <p>1.21.1 identifies an {@code AttributeModifier} by {@link ResourceLocation}, not by
 * {@code UUID}; {@link AnsModifierIdMapper} is what turns the loader-neutral contract keys into
 * that identity, so the id list is not a literal here.
 */
public final class ProgressionModifiers {

    private static final String IRONS_NAMESPACE = "irons_spellbooks";

    /** The single identity the bonus is written under from now on. */
    public static final ResourceLocation CANONICAL_ID =
        AnsModifierIdMapper.INSTANCE.map(AnsModifierIds.CROSS_MOD_SCHOOL_PROGRESSION);

    /** Canonical plus every historical id, i.e. everything an apply or a clear must remove. */
    public static final List<ResourceLocation> ALL_IDS =
        AnsModifierIdMapper.INSTANCE.mapAll(AnsModifierIds.CROSS_MOD_SCHOOL_PROGRESSION);

    private ProgressionModifiers() {}

    /**
     * Apply the bonus for one school, collapsing any historical modifier onto the canonical id.
     *
     * <p>A non-positive bonus removes and adds nothing, which is what makes this the removal
     * path as well as the apply path.
     */
    public static void apply(Player player, String school, double bonus) {
        applyTo(schoolAttribute(player, school), bonus);
    }

    /**
     * The attribute-level primitive: every ANS progression id off, then the canonical one on.
     *
     * <p>Exposed so the invariant - exactly one ANS modifier on the attribute afterwards,
     * whatever was there before - can be asserted without a live server.
     */
    public static void applyTo(AttributeInstance instance, double bonus) {
        if (instance == null) {
            return;
        }
        for (ResourceLocation id : ALL_IDS) {
            instance.removeModifier(id);
        }
        if (bonus > 0 && CANONICAL_ID != null) {
            instance.addTransientModifier(new AttributeModifier(
                CANONICAL_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /**
     * Drop the progression bonus from every attribute, under every id, with no feature gate.
     *
     * <p>The cast counts behind it are deliberately left alone, so re-enabling the feature
     * replays the same bonuses instead of restarting the player's progression.
     */
    public static void clearAll(Player player) {
        AnsFeatureCleanup.removeIds(player, ALL_IDS);
    }

    /**
     * Every school with a positive bonus, read from the persisted cast counts.
     *
     * <p>Pure: the replay paths iterate this rather than the raw counts, so the mapping from
     * "what is stored" to "what is applied" exists once.
     */
    public static Map<String, Double> bonuses(ProgressionData data) {
        Map<String, Double> bonuses = new LinkedHashMap<>();
        if (data == null) {
            return bonuses;
        }
        data.getAllCastCounts().forEach((school, count) -> {
            double bonus = data.getBonusForSchool(school);
            if (bonus > 0) {
                bonuses.put(school, bonus);
            }
        });
        return bonuses;
    }

    /**
     * The Iron's {@code <school>_spell_power} instance for this player, or {@code null} when
     * the attribute does not exist (Iron's absent, or an unknown school name).
     */
    public static AttributeInstance schoolAttribute(Player player, String school) {
        if (player == null || school == null || school.isEmpty()) {
            return null;
        }
        Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE
            .getHolder(ResourceLocation.fromNamespaceAndPath(IRONS_NAMESPACE, school + "_spell_power"))
            .orElse(null);
        return attribute == null ? null : player.getAttribute(attribute);
    }
}
