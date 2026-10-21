package com.otectus.arsnspells.compat;

import com.otectus.arsnspells.util.SchoolKeys;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import java.lang.reflect.Field;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/** Iron's 1.20.1-3.15.0 exposes registry school identity but no public attribute getter.
 * Reads the verified Supplier fields; an unavailable binding is safely absent, never guessed. */
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
    /**
     * Iron's own display name for a registered school, without its colour, or null when no
     * school with this id is registered. Used only for labels; never for identity.
     */
    @Nullable public static net.minecraft.network.chat.MutableComponent displayName(String key) {
        String normalized = SchoolKeys.normalize(key);
        if (!SchoolKeys.isNamespaced(normalized)) return null;
        try {
            var registry = SchoolRegistry.REGISTRY.get();
            ResourceLocation id = ResourceLocation.tryParse(normalized);
            if (registry == null || id == null || !registry.containsKey(id)) return null;
            SchoolType school = registry.getValue(id);
            return school == null || school.getDisplayName() == null ? null
                : school.getDisplayName().copy().setStyle(net.minecraft.network.chat.Style.EMPTY);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }
    @Nullable public static Attribute power(String key) { return attribute(key, POWER); }
    @Nullable public static Attribute resistance(String key) { return attribute(key, RESISTANCE); }
    @Nullable private static Attribute attribute(String key, @Nullable Field binding) {
        String normalized = SchoolKeys.normalize(key);
        if (binding == null || !SchoolKeys.isNamespaced(normalized)) return null;
        try {
            // getSchool has an upstream default; query the registry directly so unknown IDs
            // cannot acquire another school's attribute.
            var registry = SchoolRegistry.REGISTRY.get();
            ResourceLocation id = new ResourceLocation(normalized);
            if (registry == null || !registry.containsKey(id)) return null;
            SchoolType school = registry.getValue(id);
            Object value = binding.get(school);
            if (!(value instanceof Supplier<?> supplier)) return null;
            Object attribute = supplier.get();
            return attribute instanceof Attribute result ? result : null;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }
}
