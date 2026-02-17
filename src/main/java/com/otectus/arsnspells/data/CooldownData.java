package com.otectus.arsnspells.data;

import com.otectus.arsnspells.cooldown.CooldownCategory;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import java.util.HashMap;
import java.util.Map;

public class CooldownData {
    public static final Capability<CooldownData> COOLDOWN_CAP = CapabilityManager.get(new CapabilityToken<>() {});
    // Stores cooldown end tick per category
    private final Map<CooldownCategory, Long> cooldowns = new HashMap<>();

    public long getLastCast(CooldownCategory cat) { return cooldowns.getOrDefault(cat, 0L); }
    public void setLastCast(CooldownCategory cat, long time) { cooldowns.put(cat, time); }

    public void save(CompoundTag nbt) {
        CompoundTag tag = new CompoundTag();
        cooldowns.forEach((cat, time) -> tag.putLong(cat.name(), time));
        nbt.put("BridgeCooldowns", tag);
    }

    public void load(CompoundTag nbt) {
        if (nbt.contains("BridgeCooldowns")) {
            CompoundTag tag = nbt.getCompound("BridgeCooldowns");
            for (CooldownCategory cat : CooldownCategory.values()) {
                if (tag.contains(cat.name())) cooldowns.put(cat, tag.getLong(cat.name()));
            }
        }
    }
}
