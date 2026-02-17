# Quick Fix Reference

## 🎯 What Was Fixed

| Issue | Status | Fix |
|-------|--------|-----|
| Infinite casting with 0 mana | ✅ FIXED | Pre-cast validation (hard-gate) |
| Ars potions not working | ✅ FIXED | Redirect effects to unified mana |
| Global cooldown contamination | ✅ FIXED | Per-mod namespace separation |
| Ring exploit (alternate costs) | ✅ FIXED | Resource-agnostic validation |

---

## 🔧 Key Changes

### 1. Casting Authority System
**New File:** `CastingAuthority.java`
- Validates resources **BEFORE** spell execution
- Handles mana, health, aura, and other resources
- Single source of truth for "can this spell be cast?"

### 2. Pre-Cast Validation Mixin
**New File:** `MixinSpellResolverPreCast.java`
- Injects at `SpellResolver.resolve()` HEAD
- Blocks spell execution if resources insufficient
- No more post-cast spam

### 3. Potion Effect Redirection
**New File:** `MixinArsPotionEffects.java`
- Detects Ars potion effects
- Applies them to Iron's attributes
- Uses conversion rates for scaling

### 4. Cooldown Namespacing
**Updated:** `UnifiedCooldownManager.java`
- Cooldowns now tracked per-mod: `"ars:OFFENSIVE"` vs `"irons:OFFENSIVE"`
- New config: `ENABLE_CROSS_MOD_COOLDOWNS` (default: false)
- Iron's cooldowns are independent from Ars

---

## ⚙️ Configuration

### Recommended Settings (Default)

```toml
# Mana unification enabled
enable_mana_unification = true

# Iron's Spellbooks is primary mana pool
mana_unification_mode = "ISS_PRIMARY"

# Cross-mod cooldowns DISABLED (each mod independent)
enable_cross_mod_cooldowns = false

# Cooldown system enabled (but per-mod)
enable_cooldown_system = true
```

### If You Want Cross-Mod Cooldowns

```toml
# Enable cross-mod cooldown interference
enable_cross_mod_cooldowns = true
```

**Warning:** This will make Iron's spells trigger Ars cooldowns and vice versa.

---

## 🧪 Testing Commands

### Test Mana Validation
1. Set mana to 50: `/irons_spellbooks mana set @s 50`
2. Try casting Ars spell with 90 cost
3. **Expected:** Spell doesn't fire, no spam

### Test Potion Effects
1. Drink Ars mana regen potion
2. Check Iron's mana regen rate
3. **Expected:** Mana regenerates faster

### Test Cooldown Independence
1. Cast Iron's Firebolt rapidly
2. Immediately cast Ars spell
3. **Expected:** Ars spell works (no cooldown interference)

### Test Alternate Resources
1. Equip Ring of Seven Curses
2. Try casting with 0 mana but full health
3. **Expected:** Spell casts, health is consumed

---

## 🐛 Debug Mode

Enable detailed logging:

```toml
debug_mode = true
```

**Debug Output:**
- `[CastingAuthority] [DEBUG] Mana validation failed for Player: cost=90.0, available=50.0, fromArs=true`
- `[Cooldown] [DEBUG] Applied cooldown to Player for OFFENSIVE (namespace: ars): 100 ticks`
- `[EquipmentHandler] [DEBUG] Applied Ars gear bonuses to Iron's mana for Player: max=50.0, regen=2.5`

---

## 📋 Build Info

**Version:** 1.2.0  
**Build Status:** ✅ SUCCESS  
**Warnings:** 0  
**Errors:** 0  
**JAR Location:** `build/libs/ars_n_spells-1.2.0.jar`

---

## 🚨 Known Limitations

### Potion Effect Detection
Currently uses heuristic detection (effect name contains "mana_regen", "spell_damage", etc.).

**Future Improvement:** Direct potion registry lookup for more accurate detection.

### Alternate Resource Detection
Ring detection is placeholder-based. Full curio integration pending.

**Future Improvement:** Proper Curios API integration for ring detection.

---

## 📞 Support

If you encounter issues:

1. Enable `debug_mode = true`
2. Check logs for `[CastingAuthority]`, `[Cooldown]`, or `[EquipmentHandler]` messages
3. Report with:
   - Exact steps to reproduce
   - Debug log output
   - Config settings
   - Mana mode used

---

**Ready to test!** 🎮
