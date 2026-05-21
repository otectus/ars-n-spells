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
    // Stores cooldown end tick per category. NBT key is "BridgeCooldowns" for
    // backwards-compatibility with existing player save files (ANS-LOW-016).
    /**
     * ANS-MED-013: server-main-thread only; see AffinityData for the same invariant.
     */
    private final Map<CooldownCategory, Long> cooldowns = new HashMap<>();

    // ANS-MED-039: getLastCast/setLastCast names were misleading — these store the
    // cooldown END tick, not the last-cast tick. Renamed accessors below preserve
    // the old names as deprecated forwarders so the rename can land without breaking
    // every call site in one diff.
    public long getCooldownEnd(CooldownCategory cat) { return cooldowns.getOrDefault(cat, 0L); }
    public void setCooldownEnd(CooldownCategory cat, long endTick) { cooldowns.put(cat, endTick); }

    /** @deprecated use {@link #getCooldownEnd(CooldownCategory)} — historic name */
    @Deprecated
    public long getLastCast(CooldownCategory cat) { return getCooldownEnd(cat); }
    /** @deprecated use {@link #setCooldownEnd(CooldownCategory, long)} — historic name */
    @Deprecated
    public void setLastCast(CooldownCategory cat, long time) { setCooldownEnd(cat, time); }

    public void save(CompoundTag nbt) {
        CompoundTag tag = new CompoundTag();
        cooldowns.forEach((cat, time) -> tag.putLong(cat.name(), time));
        nbt.put("BridgeCooldowns", tag);
    }

    public void load(CompoundTag nbt) {
        // ANS-LOW-017: clear before load, mirroring AffinityData (ANS-MED-012).
        cooldowns.clear();
        if (nbt.contains("BridgeCooldowns")) {
            CompoundTag tag = nbt.getCompound("BridgeCooldowns");
            for (CooldownCategory cat : CooldownCategory.values()) {
                if (tag.contains(cat.name())) cooldowns.put(cat, tag.getLong(cat.name()));
            }
        }
    }
}
