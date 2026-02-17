# Testing Guide: Ring of Virtue and Blasphemy Curio Discounts

## Quick Start

### 1. Installation
The mod has been automatically copied to your Minecraft instance:
- **Location:** `C:\Users\crims\curseforge\minecraft\Instances\Ars 'n Spells\mods\ars_n_spells-1.2.0.jar`
- **Status:** ✅ Ready to use

### 2. Configuration
The configuration file has been updated with default values:
- **Location:** `C:\Users\crims\curseforge\minecraft\Instances\Ars 'n Spells\config\ars_n_spells-common.toml`
- **Section:** `[Curio Discount System]`

### 3. Enable Debug Mode (Recommended for Testing)
Edit `ars_n_spells-common.toml` and set:
```toml
debug_mode = true
```

This will show detailed discount calculations in the game log.

## Testing Scenarios

### Scenario 1: Ring of Virtue Discount
**Setup:**
1. Equip Ring of the Seven Virtues in a curio slot
2. Prepare an Ars Nouveau spell (e.g., Projectile → Ignite)
3. Note the base mana cost

**Expected Result:**
- Mana cost reduced by 20%
- Example: 100 mana → 80 mana
- Debug log shows: "Ring of Virtue discount applied: 20.0%"

**Verification:**
- Cast the spell and observe mana consumption
- Check debug log for discount application
- Compare with cost when Ring is not equipped

---

### Scenario 2: Blasphemy Discount (Non-Matching School)
**Setup:**
1. Equip Fire Blasphemy curio
2. Cast a non-fire spell (e.g., Projectile → Cold Snap)
3. Note the base mana cost

**Expected Result:**
- Mana cost reduced by 15% (base discount only)
- Example: 100 mana → 85 mana
- Debug log shows: "Blasphemy discount applied: 15.0% (school: ice, matching: false)"

---

### Scenario 3: Blasphemy Discount (Matching School)
**Setup:**
1. Equip Fire Blasphemy curio
2. Cast a fire spell (e.g., Projectile → Ignite)
3. Note the base mana cost

**Expected Result:**
- Mana cost reduced by 25% (15% base + 10% matching bonus)
- Example: 100 mana → 75 mana
- Debug log shows: "Blasphemy discount applied: 25.0% (school: fire, matching: true)"

---

### Scenario 4: Ring of Virtue + Blasphemy (Stacking)
**Setup:**
1. Equip Ring of the Seven Virtues
2. Equip Fire Blasphemy curio
3. Cast a fire spell (e.g., Projectile → Ignite)

**Expected Result:**
- Discounts stack multiplicatively
- Calculation: 100 → 80 (Virtue) → 60 (Blasphemy matching)
- **Final Cost: 60 mana** (40% total discount)
- Debug log shows both discounts applied

---

### Scenario 5: Multiple Blasphemy Curios
**Setup:**
1. Equip multiple Blasphemy curios (e.g., Fire + Ice)
2. Cast a fire spell

**Expected Result:**
- Only the matching Blasphemy provides the bonus
- Fire Blasphemy: 25% discount (15% + 10% matching)
- Ice Blasphemy: No additional effect
- **Final Cost: 75 mana**

---

### Scenario 6: Discount Stacking Disabled
**Setup:**
1. Edit config: `allow_discount_stacking = false`
2. Equip Ring of Virtue + Blasphemy
3. Cast a spell

**Expected Result:**
- Only Ring of Virtue discount applies (higher priority)
- **Final Cost: 80 mana** (20% discount)
- Debug log shows: "Blasphemy discount skipped (stacking disabled and Virtue Ring active)"

---

## Spell School Mapping Reference

### Fire School
- **Glyphs:** Ignite, Flare, Burn
- **Blasphemy:** `fire_blasphemy`

### Ice School
- **Glyphs:** Cold Snap, Freeze, Frost
- **Blasphemy:** `ice_blasphemy`

### Lightning School
- **Glyphs:** Lightning, Shock
- **Blasphemy:** `lightning_blasphemy`

### Holy School
- **Glyphs:** Heal, Light
- **Blasphemy:** `holy_blasphemy`

### Ender School
- **Glyphs:** Blink, Warp, Ender Inventory
- **Blasphemy:** `ender_blasphemy`

### Blood School
- **Glyphs:** Drain, Life
- **Blasphemy:** `blood_blasphemy`

### Evocation School
- **Glyphs:** Projectile, Fangs
- **Blasphemy:** `evocation_blasphemy`

### Nature School
- **Glyphs:** Grow, Harvest, Plant
- **Blasphemy:** `nature_blasphemy`

### Eldritch School
- **Glyphs:** Wither, Hex, Dark
- **Blasphemy:** `eldritch_blasphemy`

### Aqua School
- **Glyphs:** Conjure Water
- **Blasphemy:** `aqua_blasphemy`

### Geo School
- **Glyphs:** Crush, Earth
- **Blasphemy:** `geo_blasphemy`

### Wind School
- **Glyphs:** Gust, Wind Shear
- **Blasphemy:** `wind_blasphemy`

---

## Debug Log Examples

### Successful Discount Application
```
[CurioDiscount] [DEBUG] Ring of Virtue discount applied: 20.0%
[CurioDiscount] [DEBUG] Blasphemy discount applied: 25.0% (school: fire, matching: true)
[CurioDiscount] [DEBUG] Applied curio discount to PlayerName: 100 mana -> 60 mana (saved 40 mana, 40.0% discount)
```

### No Matching School
```
[CurioDiscount] [DEBUG] Ring of Virtue discount applied: 20.0%
[CurioDiscount] [DEBUG] Blasphemy discount applied: 15.0% (school: ice, matching: false)
[CurioDiscount] [DEBUG] Applied curio discount to PlayerName: 100 mana -> 68 mana (saved 32 mana, 32.0% discount)
```

### Stacking Disabled
```
[CurioDiscount] [DEBUG] Ring of Virtue discount applied: 20.0%
[CurioDiscount] [DEBUG] Blasphemy discount skipped (stacking disabled and Virtue Ring active)
[CurioDiscount] [DEBUG] Applied curio discount to PlayerName: 100 mana -> 80 mana (saved 20 mana, 20.0% discount)
```

---

## Troubleshooting

### Discounts Not Applying
1. **Check if curio discounts are enabled:**
   - Open `config/ars_n_spells-common.toml`
   - Verify `enable_curio_discounts = true`

2. **Verify Sanctified Legacy is loaded:**
   - Check mods folder for `sanctified_legacy-2.2.5.jar`
   - Check logs for "Sanctified Legacy compatibility enabled"

3. **Check curio equipment:**
   - Ensure Ring of Virtue is in a curio slot (not inventory)
   - Ensure Blasphemy is in the "blasphemy" curio slot

4. **Enable debug mode:**
   - Set `debug_mode = true` in config
   - Check logs for discount calculation messages

### Discounts Too High/Low
1. **Adjust discount percentages in config:**
   - `virtue_ring_discount` - Default: 0.2 (20%)
   - `blasphemy_discount` - Default: 0.15 (15%)
   - `blasphemy_matching_school_bonus` - Default: 0.1 (10%)

2. **Disable stacking if needed:**
   - Set `allow_discount_stacking = false`

### School Matching Not Working
1. **Check spell glyph names:**
   - Some custom glyphs may not match keywords
   - Check debug log for detected school

2. **Verify Blasphemy type:**
   - Ensure you have the correct Blasphemy for the spell school
   - Example: Fire spells need `fire_blasphemy`

---

## Performance Notes

- Curio detection uses reflection (cached for 1 second)
- Minimal performance impact (<1ms per spell cast)
- Cache automatically cleared on equipment changes
- No impact when curio discounts are disabled

---

## Compatibility Notes

### Works With:
- ✅ All mana unification modes
- ✅ Ars Nouveau discount rings (stacks multiplicatively)
- ✅ Cursed Ring (LP costs)
- ✅ Virtue Ring (Aura costs - when implemented)
- ✅ All Ars Nouveau glyphs and spells

### Does Not Affect:
- ❌ Iron's Spellbooks spells (use Iron's native discount system)
- ❌ Non-player casters (mobs, spell turrets, etc.)
- ❌ Creative mode players (already free)

---

## Support

If you encounter issues:
1. Enable debug mode
2. Reproduce the issue
3. Check `logs/latest.log` for error messages
4. Look for `[CurioDiscount]` or `[SanctifiedLegacyCompat]` log entries
5. Verify all required mods are loaded and compatible versions

---

## Changelog

### Version 1.2.0 - February 2, 2026
- ✨ Added Ring of Virtue mana discount support (20% default)
- ✨ Added Blasphemy curio mana discount support (15% base, 25% matching)
- ✨ Added school-specific discount bonuses for Blasphemy curios
- ✨ Added configurable discount stacking
- ✨ Added 5 new configuration options
- 🔧 Extended SanctifiedLegacyCompat with curio detection methods
- 🔧 Created CurioDiscountHandler event system
- 🔧 Updated EquipmentIntegration with discount caching
- 📝 Added comprehensive debug logging
