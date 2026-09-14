package com.otectus.arsnspells.util;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.affinity.AffinityBonuses;
import com.otectus.arsnspells.affinity.SchoolKeys;
import com.otectus.arsnspells.augmentation.ResonanceManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.MultiSchoolPowerPolicy;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Iron's Spellbooks-aware spell scaling for Ars Nouveau spells. The {@code ELEMENT_MAP}
 * resolves Iron's {@code AttributeRegistry} entries, so this class must never be loaded
 * on a server that does not have Iron's installed. All callers are expected to gate
 * on {@link com.otectus.arsnspells.compat.IronsCompat#isLoaded()} first; the map itself
 * is lazily built so that even a stray accidental import will not trip a static initializer
 * crash before the gate runs.
 *
 * <p>NeoForge 1.21.1 note: Iron's {@code AttributeRegistry} entries are
 * {@code DeferredHolder<Attribute, Attribute>} (a {@link Holder}) rather than Forge
 * {@code RegistryObject<Attribute>}; {@link Player#getAttributeValue(Holder)} takes the
 * holder directly, so the former {@code .get()} unwrap is gone.
 *
 * <h2>Which school scales a multi-school spell</h2>
 * Damage scaling aggregates over <em>every</em> school {@link SpellAnalysis.Result#schools()}
 * resolved, under the configured {@code multi_school_power_policy}
 * ({@link MultiSchoolPowerPolicy}); affinity and progression credit the primary
 * ({@link SpellAnalysis.Result#dominantSchool()}) school only. That split is deliberate: a
 * dual-element spell should be able to scale with either of the caster's elemental powers, but
 * it must still train exactly one affinity track. No policy sums the matching bonuses, so a
 * spell never gets stronger merely for carrying more school labels.
 */
public class SpellScalingUtil {
    private static volatile Map<String, Holder<Attribute>> ELEMENT_MAP;

    private static Map<String, Holder<Attribute>> elementMap() {
        Map<String, Holder<Attribute>> m = ELEMENT_MAP;
        if (m == null) {
            synchronized (SpellScalingUtil.class) {
                m = ELEMENT_MAP;
                if (m == null) {
                    m = new HashMap<>();
                    m.put("fire", AttributeRegistry.FIRE_SPELL_POWER);
                    m.put("ice", AttributeRegistry.ICE_SPELL_POWER);
                    m.put("lightning", AttributeRegistry.LIGHTNING_SPELL_POWER);
                    m.put("holy", AttributeRegistry.HOLY_SPELL_POWER);
                    m.put("ender", AttributeRegistry.ENDER_SPELL_POWER);
                    m.put("blood", AttributeRegistry.BLOOD_SPELL_POWER);
                    m.put("evocation", AttributeRegistry.EVOCATION_SPELL_POWER);
                    m.put("nature", AttributeRegistry.NATURE_SPELL_POWER);
                    m.put("eldritch", AttributeRegistry.ELDRITCH_SPELL_POWER);
                    ELEMENT_MAP = m;
                }
            }
        }
        return m;
    }

    /**
     * The scaling factors that produced one multiplier, kept together so callers and the
     * later diagnostics work can explain a number instead of just reporting it.
     *
     * @param matchedSchool the Ars school {@link SpellAnalysis} resolved, or {@code null}
     *                      when no school matched and only generic power applied
     * @param schoolPower   the matching elemental attribute value, or {@code 1.0} when
     *                      {@code matchedSchool} is null (so its additive term is zero)
     * @param uncapped      the product before {@code spell_power_cap} was applied
     */
    public record SpellPowerBreakdown(float globalPower, String matchedSchool, float schoolPower,
                                      float affinity, float resonance, float uncapped,
                                      float multiplier) {}

    /**
     * The school that ended up scaling a spell and the attribute value it contributed.
     *
     * @param matchedSchool the school whose Iron's attribute was used, {@code null} when none
     *                      matched; for {@link MultiSchoolPowerPolicy#AVERAGE} the matched
     *                      schools joined with {@code +}, since no single one won
     * @param schoolPower   the aggregated attribute value, or {@code 1.0} when nothing matched
     *                      (so its additive term in {@link #combine} is zero)
     */
    public record SchoolAggregate(String matchedSchool, float schoolPower) {}

    /**
     * Reduce the caster's matching elemental attribute values to the one value that scales the
     * spell, under {@code policy}.
     *
     * <p>Pure: {@code values} is an already-collected, insertion-ordered map of school id →
     * attribute value, containing only schools that have an Iron's counterpart. Kept free of
     * {@link Player} and of config reads so the policy can be unit-tested directly - the
     * failure mode it guards against (an all-element spell collecting four bonuses at once) is
     * pure arithmetic and deserves a test that does not need a Minecraft bootstrap.
     *
     * <p>No policy adds the values together; see {@link MultiSchoolPowerPolicy}. An empty map
     * yields {@code (null, 1.0f)}, the same neutral result as a spell whose school Iron's has no
     * attribute for.
     *
     * @param primarySchool the spell's dominant school, the only one {@code PRIMARY} considers
     */
    public static SchoolAggregate aggregateSchoolPower(MultiSchoolPowerPolicy policy,
                                                       Map<String, Float> values,
                                                       String primarySchool) {
        if (values == null || values.isEmpty()) {
            return new SchoolAggregate(null, 1.0f);
        }

        if (policy == MultiSchoolPowerPolicy.PRIMARY) {
            // A primary school with no Iron's counterpart contributes nothing, even when a
            // secondary school of the same spell does have one.
            Float value = values.get(primarySchool);
            return value == null ? new SchoolAggregate(null, 1.0f)
                                 : new SchoolAggregate(primarySchool, value);
        }

        if (policy == MultiSchoolPowerPolicy.AVERAGE) {
            float total = 0.0f;
            StringBuilder matched = new StringBuilder();
            for (Map.Entry<String, Float> entry : values.entrySet()) {
                total += entry.getValue();
                if (matched.length() > 0) {
                    matched.append('+');
                }
                matched.append(entry.getKey());
            }
            return new SchoolAggregate(matched.toString(), total / values.size());
        }

        // MAX (the default, and the fallback for any policy added without a branch here).
        // Ties keep the earliest-resolved school so the reported answer is deterministic.
        String bestSchool = null;
        float best = Float.NEGATIVE_INFINITY;
        for (Map.Entry<String, Float> entry : values.entrySet()) {
            if (entry.getValue() > best) {
                best = entry.getValue();
                bestSchool = entry.getKey();
            }
        }
        return new SchoolAggregate(bestSchool, best);
    }

    /**
     * The scaling formula itself, with every input already resolved.
     *
     * <p>Split out of {@link #getMultiplierForCaster} so it can be unit-tested without a
     * {@link Player} or a Minecraft bootstrap: it touches neither registry nor config. The
     * arithmetic is unchanged - additive school stacking, then multiplicative affinity and
     * resonance, then the cap.
     *
     * <p>Pass {@code schoolPower = 1.0f} when no school matched; the additive term is
     * {@code schoolPower - 1.0f}, so that contributes nothing.
     */
    public static float combine(float globalPower, float schoolPower, float affinity,
                                float resonance, float cap) {
        float value = (globalPower + (schoolPower - 1.0f)) * affinity * resonance;
        return Math.max(0.0f, Math.min(value, cap));
    }

    /**
     * Resolve the full spell-power multiplier for {@code player} casting {@code spell}, along
     * with the factors that produced it.
     *
     * <p>Reads Iron's {@code SPELL_POWER} and the elemental attributes for the spell's schools,
     * so it inherits this class's Iron's-only contract: callers must be behind the Iron's-loaded
     * gate.
     */
    public static SpellPowerBreakdown getMultiplierForCaster(Player player, Spell spell) {
        float globalPower = (float) player.getAttributeValue(AttributeRegistry.SPELL_POWER);

        SpellAnalysis.Result analysis = SpellAnalysis.analyze(spell);
        String school = analysis.schoolKey();

        // Additive scaling: base power + (elemental bonus - 1.0) prevents exponential stacking.
        //
        // This uses the SAME schools the rest of the mod uses. It previously called
        // SpellAnalysis, discarded the answer, and re-derived the element with a different
        // substring test over the map - so Firework counted as generic for affinity but
        // matched "fire" for scaling, glyph_ender_inventory scaled as ender, and a path
        // containing two element words resolved by hash iteration order.
        //
        // A spell may resolve to several schools; collect the caster's attribute for each one
        // Iron's actually has, then let the policy pick a single value out of them. The policy
        // is read once here, not once per school.
        MultiSchoolPowerPolicy policy =
            MultiSchoolPowerPolicy.fromString(AnsConfig.MULTI_SCHOOL_POWER_POLICY.get());
        Map<String, Float> schoolPowers = new LinkedHashMap<>();
        for (String candidate : analysis.allSchoolKeys()) {
            Holder<Attribute> elemental = com.otectus.arsnspells.compat.IronsSchoolAttributes.power(candidate);
            if (elemental != null && player.getAttribute(elemental) != null) {
                schoolPowers.put(candidate, (float) player.getAttributeValue(elemental));
            }
        }
        SchoolAggregate aggregate = aggregateSchoolPower(policy, schoolPowers, school);
        String matchedSchool = aggregate.matchedSchool();
        float schoolPower = aggregate.schoolPower();

        // Apply affinity bonus: 0.5% per affinity level for the matching school.
        // The key is the same one AffinityHandler stored under, so the lookup hits
        // the correct track (including the aqua/geo/wind ars_n_spells:* tracks).
        float affinity = 1.0f;
        if (AnsConfig.ENABLE_AFFINITY_SYSTEM.get()) {
            String affinityKey = com.otectus.arsnspells.compat.IronsSchoolAttributes.power(school) == null ? null : school;
            if (affinityKey != null) {
                affinity = AffinityBonuses.getAttributeMultiplier(player, affinityKey);
            }
        }

        // Apply resonance multiplier from cross-mod mana synergy. enable_ars_resonance is
        // the per-direction toggle the config has always advertised and nothing ever read;
        // this is the Ars half of it.
        float resonance = 1.0f;
        if (AnsConfig.flag(AnsConfig.ENABLE_RESONANCE_SYSTEM, false)
            && AnsConfig.flag(AnsConfig.ENABLE_ARS_RESONANCE, true)) {
            resonance = (float) ResonanceManager.getResonance(player);
        }

        float cap = AnsConfig.SPELL_POWER_CAP.get().floatValue();
        float uncapped = (globalPower + (schoolPower - 1.0f)) * affinity * resonance;
        float multiplier = combine(globalPower, schoolPower, affinity, resonance, cap);
        return new SpellPowerBreakdown(globalPower, matchedSchool, schoolPower, affinity,
                                       resonance, uncapped, multiplier);
    }
}
