package com.otectus.arsnspells.compat;

import com.otectus.arsnspells.util.SchoolKeys;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import java.lang.reflect.Field;
import net.minecraft.core.Holder;
import org.jetbrains.annotations.Nullable;

/** Iron's 1.21.1-3.16.3 exposes registry school identity but no public attribute getter.
 * Reads the verified Holder fields; an unavailable binding is safely absent, never guessed. */
public final class IronsSchoolAttributes {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(IronsSchoolAttributes.class);
    private static final Field POWER = field("powerAttribute");
    private static final Field RESISTANCE = field("resistanceAttribute");
    private IronsSchoolAttributes() {}

    @Nullable private static Field field(String name) {
        try {
            Field field = SchoolType.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            LOGGER.warn("Iron's school {} binding is unavailable; custom school bonuses are disabled", name);
            return null;
        }
    }
    @Nullable public static Holder<Attribute> power(String key) { return attribute(key, POWER); }
    @Nullable public static Holder<Attribute> resistance(String key) { return attribute(key, RESISTANCE); }
    @Nullable private static Holder<Attribute> attribute(String key, @Nullable Field binding) {
        String normalized = SchoolKeys.normalize(key);
        if (binding == null || !SchoolKeys.isNamespaced(normalized)) return null;
        try {
            // getSchool has an upstream default; query the registry directly so unknown IDs
            // cannot acquire another school's attribute.
            var registry = SchoolRegistry.REGISTRY;
            ResourceLocation id = ResourceLocation.parse(normalized);
            if (registry == null || !registry.containsKey(id)) return null;
            SchoolType school = registry.get(id);
            Object value = binding.get(school);
            if (!(value instanceof Holder<?> holder) || !(holder.value() instanceof Attribute)) return null;
            @SuppressWarnings("unchecked") Holder<Attribute> result = (Holder<Attribute>) holder;
            return result;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }
}
