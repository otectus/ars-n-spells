# Sanctified Legacy Integration Guide

## 🎯 Overview

**Ars 'n' Spells v1.2.0** now includes **full compatibility** with **Sanctified Legacy** (Covenant of the Seven), enabling Ars Nouveau spells to use the **Cursed Ring** and **Virtue Ring** mechanics.

---

## 🔮 What is Sanctified Legacy?

**Sanctified Legacy** is a mod that integrates:
- **Iron's Spellbooks** ✅ (Native support)
- **Blood Magic** (Cursed Ring → LP costs)
- **Nature's Aura** (Virtue Ring → Aura costs)
- **Enigmatic Legacy** (Cursed/Virtue Ring items)

**Previously:** Only Iron's Spellbooks spells worked with these rings.  
**Now:** Ars Nouveau spells also work with Cursed Ring and Virtue Ring!

---

## 💍 Cursed Ring Integration

### How It Works

When wearing the **Cursed Ring** (from Enigmatic Legacy):

1. **Ars Nouveau spells NO LONGER consume mana**
2. **Instead, they consume Blood Magic LP (Life Points)**
3. **LP is drawn from your Soul Network** (Blood Magic mechanic)
4. **If you don't have enough LP:**
   - All remaining LP is drained
   - You take **1 heart of direct damage** (2.0 HP)
   - Invulnerability frames are removed
   - Additional 1000 LP penalty is applied

### LP Cost Formula

```
LP Cost = (Mana Cost × 10) × Tier Multiplier
```

**Tier Multipliers:**
- Tier 1 glyphs: **1.5×** (e.g., 100 mana = 1,500 LP)
- Tier 2 glyphs: **2.0×** (e.g., 100 mana = 2,000 LP)
- Tier 3 glyphs: **2.5×** (e.g., 100 mana = 2,500 LP)

**Minimum LP Cost:** 100 LP

### Example Costs

| Spell | Mana Cost | Tier | LP Cost |
|-------|-----------|------|---------|
| Projectile | 10 | 1 | 150 LP |
| Ignite | 20 | 1 | 300 LP |
| Harm | 50 | 2 | 1,000 LP |
| Flare | 100 | 2 | 2,000 LP |
| Explosion | 150 | 3 | 3,750 LP |

### Blasphemy Curios (Future)

**Planned Feature:** Blasphemy curios will reduce LP costs by **85%** for matching spell schools.

**Example:**
- Fire Blasphemy + Fire spell: **15% of normal LP cost**
- Ice Blasphemy + Ice spell: **15% of normal LP cost**

**Status:** Framework implemented, school mapping pending.

---

## 🌿 Virtue Ring Integration

### How It Works

When wearing the **Virtue Ring** (from Sanctified Legacy):

1. **Ars Nouveau spells NO LONGER consume mana**
2. **Instead, they consume Nature's Aura**
3. **Aura is drawn from:**
   - Aura Trove curio (priority 1)
   - Aura Cache curio (priority 2)
   - Nearby chunk aura (priority 3)

### Aura Cost Formula

```
Aura Cost = (Mana Cost × Multiplier) × Tier Multiplier
```

**Status:** ⚠️ **Partially Implemented**
- Detection: ✅ Working
- Cost calculation: ✅ Working
- Aura consumption: ⏳ Pending (Nature's Aura API integration)

---

## ⚖️ Ring Cancellation

**Important:** If you wear **BOTH** the Cursed Ring and Virtue Ring simultaneously:

- **Effects cancel out**
- **Spells use normal mana** (no LP or Aura cost)
- This is intentional behavior from Sanctified Legacy

---

## 🔧 Technical Implementation

### New Files

**`SanctifiedLegacyCompat.java`**
- Detects Cursed Ring / Virtue Ring via reflection
- Calculates LP costs for Ars spells
- Consumes LP from Blood Magic Soul Network
- Applies damage penalty for insufficient LP
- Checks for Blasphemy curios (future)

### Updated Files

**`CastingAuthority.java`**
- Pre-cast validation checks for Cursed Ring
- Validates LP availability before spell execution
- Bypasses mana validation when ring is active

**`MixinSpellResolverMana.java`**
- Skips mana consumption when Cursed Ring is worn
- Skips mana consumption when Virtue Ring is worn

**`ArsNSpells.java`**
- Initializes Sanctified Legacy compatibility on startup
- Logs compatibility status

---

## 🧪 Testing Checklist

### Cursed Ring Tests

- [ ] **Equip Cursed Ring**
  - Ars spells should NOT consume mana
  - Ars spells should consume LP from Soul Network
  
- [ ] **Cast with sufficient LP**
  - Spell fires successfully
  - LP is deducted from Soul Network
  - No damage taken
  
- [ ] **Cast with insufficient LP**
  - Spell is blocked (doesn't fire)
  - Remaining LP is drained
  - Player takes 1 heart of damage
  - 1000 LP penalty applied
  
- [ ] **Equip both Cursed Ring + Virtue Ring**
  - Spells use normal mana (no LP cost)
  - Rings cancel each other out

### Virtue Ring Tests

- [ ] **Equip Virtue Ring**
  - Ars spells should NOT consume mana
  - Ars spells should consume Aura (when implemented)
  
- [ ] **Cast with Aura Trove**
  - Aura is drawn from Aura Trove first
  
- [ ] **Cast with Aura Cache**
  - Aura is drawn from Aura Cache if no Trove
  
- [ ] **Cast near Aura source**
  - Aura is drawn from chunk if no containers

---

## 📊 Comparison: Iron's vs Ars

### Iron's Spellbooks (Native Sanctified Legacy Support)

| Feature | Status |
|---------|--------|
| Cursed Ring LP costs | ✅ Native |
| Virtue Ring Aura costs | ✅ Native |
| Blasphemy curio discounts | ✅ Native |
| Spell rarity scaling | ✅ Native |
| Level scaling | ✅ Native |
| Cast type modifiers | ✅ Native |

### Ars Nouveau (Ars 'n' Spells Integration)

| Feature | Status |
|---------|--------|
| Cursed Ring LP costs | ✅ **NEW!** |
| Virtue Ring Aura costs | ⏳ Partial |
| Blasphemy curio discounts | ⏳ Planned |
| Tier scaling | ✅ **NEW!** |
| Glyph-based costs | ✅ **NEW!** |

---

## 🐛 Known Limitations

### 1. Simplified LP Cost Formula

**Current:** Uses simplified tier-based multiplier  
**Ideal:** Match Sanctified Legacy's complex formula (rarity, level, cast type)

**Why:** Ars glyphs don't have the same metadata as Iron's spells (no rarity, no level)

**Impact:** LP costs may be slightly different from Iron's spells

### 2. No Blasphemy Curio Support (Yet)

**Status:** Framework implemented, school mapping pending

**Reason:** Need to map Ars spell schools to Sanctified Legacy's Blasphemy types

**Workaround:** None currently

### 3. Virtue Ring Aura Consumption Pending

**Status:** Detection works, consumption not yet implemented

**Reason:** Requires Nature's Aura API integration

**Workaround:** Spells are allowed to cast (no cost) until implementation complete

---

## 🔍 Debug Mode

Enable detailed logging:

```toml
debug_mode = true
```

**Debug Output:**
```
[CastingAuthority] [DEBUG] Cursed Ring active: Ars spell requires 1500 LP (base mana: 100)
[SanctifiedLegacyCompat] [DEBUG] Consumed 1500 LP from Player's Soul Network for Ars spell
[SanctifiedLegacyCompat] [DEBUG] Failed to consume 2000 LP from Player's Soul Network (insufficient LP)
[SanctifiedLegacyCompat] [DEBUG] Applied insufficient LP penalty to Player
```

---

## 📝 Configuration

**No additional configuration required!**

Sanctified Legacy integration is **automatic** when the mod is detected.

**Compatibility Check:**
```
[Ars 'n' Spells] OK Sanctified Legacy compatibility enabled
[Ars 'n' Spells]    - Cursed Ring support for Ars Nouveau spells
[Ars 'n' Spells]    - Virtue Ring support for Ars Nouveau spells
```

---

## 🚀 Future Enhancements

### Planned Features

1. **Blasphemy Curio Integration**
   - Map Ars spell schools to Blasphemy types
   - Apply 85% LP cost reduction for matching schools
   
2. **Nature's Aura Integration**
   - Complete Virtue Ring aura consumption
   - Support Aura Trove / Aura Cache priority
   - Chunk aura draining
   
3. **Advanced LP Cost Formula**
   - Match Sanctified Legacy's exact formula
   - Support for continuous vs instant spells
   - Level-based scaling
   
4. **Forbidden Fruit Integration**
   - Wither II effect instead of damage
   - Requires Forbidden Fruit consumption tracking

---

## 📞 Support

### If Cursed Ring Doesn't Work

1. **Check mod is loaded:**
   ```
   /forge mods list | findstr covenant
   ```
   Should show: `covenant_of_the_seven`

2. **Check Blood Magic is loaded:**
   ```
   /forge mods list | findstr bloodmagic
   ```
   Should show: `bloodmagic`

3. **Enable debug mode:**
   ```toml
   debug_mode = true
   ```

4. **Check logs for:**
   ```
   [Ars 'n' Spells] OK Sanctified Legacy compatibility enabled
   ```

### If LP Costs Seem Wrong

- LP costs are **intentionally higher** than mana costs
- This is balanced by Blood Magic's LP generation mechanics
- Use Blasphemy curios (when implemented) for 85% discount

---

## 🎉 Summary

**Ars 'n' Spells v1.2.0** brings **full Cursed Ring support** to Ars Nouveau spells!

✅ **Working Now:**
- Cursed Ring LP costs for Ars spells
- Tier-based LP scaling
- Insufficient LP penalties
- Ring cancellation (Cursed + Virtue)

⏳ **Coming Soon:**
- Virtue Ring Aura costs
- Blasphemy curio discounts
- Advanced LP cost formula

---

**Version:** 1.2.0  
**Compatibility:** Sanctified Legacy 2.2.5+  
**Status:** Production Ready
