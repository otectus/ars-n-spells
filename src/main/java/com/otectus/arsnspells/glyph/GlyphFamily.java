package com.otectus.arsnspells.glyph;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Set;

/**
 * How an imported Iron's spell meets the target Ars resolved for it.
 *
 * <p>Every family runs the Iron's spell's own code as its caster; they differ in what the Ars
 * target is allowed to change. The forms each family accepts are the four Ars Nouveau 4.12.7
 * forms by id; an addon form is refused at composition rather than guessed at.
 */
public enum GlyphFamily {
    /**
     * Resolves at the caster: self buffs, and areas, cones and dashes measured from the caster's
     * own position or look. Self only, because nothing in the spell can be pointed elsewhere and
     * resolving it at the caster for a Touch or a Projectile would silently ignore the target.
     */
    CASTER(Set.of(Forms.SELF)),
    /**
     * The spell's own targeting (Iron's {@code preCastTargetHelper}, {@code RaycastBuilder},
     * {@code raycastForEntity}, {@code raycastForBlock}, {@code getTargetBlock}) receives the Ars
     * target in place of the caster's aim, for that one execution. The spell's own target
     * filters, ranges of effect and placement rules still apply to it.
     */
    AIMED(Set.of(Forms.SELF, Forms.TOUCH, Forms.PROJECTILE, Forms.UNDERFOOT)),
    /**
     * The spell's own projectile is delivered to the Ars target: launched by its native entity
     * from just before the target, aimed at it, so collision, damage, owner and effects are the
     * projectile's own. With Self it launches from the caster along the caster's look.
     */
    PROJECTILE(Set.of(Forms.SELF, Forms.TOUCH, Forms.PROJECTILE, Forms.UNDERFOOT)),
    /** Moves the caster, never the target, to the Ars target position; with Self, to its own aim. */
    MOVEMENT(Set.of(Forms.SELF, Forms.TOUCH, Forms.PROJECTILE, Forms.UNDERFOOT)),
    /** The spell's own summons, owned by the caster, appear at the Ars target position. */
    SUMMON(Set.of(Forms.SELF, Forms.TOUCH, Forms.PROJECTILE, Forms.UNDERFOOT));

    private final Set<ResourceLocation> forms;

    GlyphFamily(Set<ResourceLocation> forms) {
        this.forms = forms;
    }

    public boolean supportsForm(ResourceLocation formId) {
        return formId != null && forms.contains(formId);
    }

    public Set<ResourceLocation> forms() {
        return forms;
    }

    /** {@code ars_n_spells:<family>} as written in the catalog. */
    public String adapterId() {
        return "ars_n_spells:" + name().toLowerCase(Locale.ROOT);
    }

    public static GlyphFamily fromAdapterId(String adapterId) {
        if (adapterId == null || !adapterId.startsWith("ars_n_spells:")) throw new IllegalArgumentException("Unknown adapter " + adapterId);
        return valueOf(adapterId.substring("ars_n_spells:".length()).toUpperCase(Locale.ROOT));
    }

    /** The four Ars Nouveau 4.12.7 forms, by registry id. */
    public static final class Forms {
        public static final ResourceLocation SELF = new ResourceLocation("ars_nouveau", "glyph_self");
        public static final ResourceLocation TOUCH = new ResourceLocation("ars_nouveau", "glyph_touch");
        public static final ResourceLocation PROJECTILE = new ResourceLocation("ars_nouveau", "glyph_projectile");
        public static final ResourceLocation UNDERFOOT = new ResourceLocation("ars_nouveau", "glyph_underfoot");

        private Forms() {}
    }
}
