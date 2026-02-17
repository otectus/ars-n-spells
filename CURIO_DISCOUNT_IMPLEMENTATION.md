# Ring of Virtue and Blasphemy Curio Discount Implementation

## Overview
This implementation adds mana cost discount support for Covenant of the Seven curios (Ring of Virtue and Blasphemy items) to Ars Nouveau spells in the Ars 'n' Spells integration mod.

## Features Implemented

### 1. Ring of the Seven Virtues Discount
- **Default Discount:** 20% mana cost reduction for all Ars Nouveau spells
- **Configurable:** Adjustable via `virtue_ring_discount` in config
- **Detection:** Uses Sanctified Legacy's `hasVirtue()` method via reflection

### 2. Blasphemy Curio Discounts
- **Base Discount:** 15% mana cost reduction for all Ars Nouveau spells
- **School Matching Bonus:** Additional 10% discount when Blasphemy school matches spell school
- **Total Possible:** Up to 25% discount with matching school (15% base + 10% bonus)
- **Configurable:** Both base and bonus are adjustable in config

### 3. Supported Blasphemy Types (13 variants)
- `fire_blasphemy` - Matches fire/ignite/flare/burn spells
- `ice_blasphemy` - Matches ice/freeze/frost/cold spells
- `lightning_blasphemy` - Matches lightning/shock/storm spells
- `holy_blasphemy` - Matches heal/holy/light spells
- `ender_blasphemy` - Matches ender/blink/warp/teleport spells
- `blood_blasphemy` - Matches blood/drain/life spells
- `evocation_blasphemy` - Matches projectile/fang/evocation spells
- `nature_blasphemy` - Matches grow/nature/plant/harvest spells
- `eldritch_blasphemy` - Matches wither/dark/hex spells
- `aqua_blasphemy` - Matches water/conjure_water spells
- `geo_blasphemy` - Matches earth/stone/crush spells
- `wind_blasphemy` - Matches wind/gust/air spells
- `dormant_blasphemy` - Inactive variant (no bonuses)

### 4. Discount Stacking
- **Multiplicative Stacking:** Ring of Virtue + Blasphemy = 32% total discount
  - Formula: `1 - ((1 - 0.20) × (1 - 0.15)) = 0.32` (32%)
- **With Matching School:** Ring of Virtue + Matching Blasphemy = 40% total discount
  - Formula: `1 - ((1 - 0.20) × (1 - 0.25)) = 0.40` (40%)
- **Configurable:** Can be disabled via `allow_discount_stacking`

## Configuration Options

Added to `config/ars_n_spells-common.toml`:

```toml
["Curio Discount System"]
    # Enable mana cost discounts from Ring of Virtue and Blasphemy curios
    enable_curio_discounts = true
    
    # Mana cost discount from Ring of Virtue (0.20 = 20% reduction)
    # Range: 0.0 ~ 1.0
    virtue_ring_discount = 0.2
    
    # Base mana cost discount from Blasphemy curios (0.15 = 15% reduction)
    # Range: 0.0 ~ 1.0
    blasphemy_discount = 0.15
    
    # Additional discount when Blasphemy school matches spell school (0.10 = 10% extra)
    # Range: 0.0 ~ 1.0
    blasphemy_matching_school_bonus = 0.1
    
    # Allow Ring of Virtue and Blasphemy discounts to stack multiplicatively
    allow_discount_stacking = true
```

## Files Modified

### 1. `AnsConfig.java`
- Added 5 new configuration options for curio discounts
- Added configuration section "Curio Discount System"

### 2. `SanctifiedLegacyCompat.java`
- Added `hasVirtueRing(Player)` - Detects Ring of Virtue
- Added `hasAnyBlasphemy(Player)` - Detects any Blasphemy curio
- Added `hasBlasphemyType(Player, String)` - Detects specific Blasphemy type
- Added `getMatchingBlasphemyType(String)` - Maps spell school to Blasphemy type
- Added `determineSpellSchool(AbstractSpellPart)` - Determines spell school from glyph

### 3. `CurioDiscountHandler.java` (NEW)
- Event handler for `SpellCostCalcEvent`
- Calculates and applies curio discounts
- Handles school matching for Blasphemy curios
- Supports discount stacking
- Includes debug logging

### 4. `EquipmentIntegration.java`
- Added `CurioDiscountData` class for caching discount information
- Added `getCurioDiscounts(Player)` method
- Updated `CachedEquipmentData` to include curio discounts
- Added internal discount calculation during equipment scans

### 5. `ArsNSpells.java`
- Registered `CurioDiscountHandler` to the Forge event bus

## Technical Details

### Event Priority
- Uses `EventPriority.LOW` to apply discounts **after** other cost modifiers
- Ensures proper interaction with Ars Nouveau's native discount rings

### Spell School Detection
- Uses `CasterContext` ThreadLocal to access spell information
- Falls back to "generic" school if spell data unavailable
- School matching is case-insensitive and keyword-based

### Performance
- Curio discount data is cached in `EquipmentIntegration`
- Cache duration: 1 second (same as other equipment bonuses)
- Cache invalidated on equipment changes

### Compatibility
- Works with all mana unification modes (ISS_PRIMARY, ARS_PRIMARY, HYBRID, SEPARATE)
- Compatible with Ars Nouveau's native discount rings
- Does not interfere with Cursed Ring LP costs
- Respects creative mode (no discounts needed)

## Testing Recommendations

### In-Game Testing
1. **Ring of Virtue Only:**
   - Equip Ring of Virtue
   - Cast any Ars Nouveau spell
   - Verify 20% mana cost reduction in debug mode

2. **Blasphemy Only:**
   - Equip a Blasphemy curio (e.g., fire_blasphemy)
   - Cast a matching spell (e.g., Ignite)
   - Verify 25% discount (15% base + 10% matching)
   - Cast a non-matching spell
   - Verify 15% discount (base only)

3. **Ring of Virtue + Blasphemy:**
   - Equip both Ring of Virtue and a Blasphemy curio
   - Cast a matching spell
   - Verify 40% total discount
   - Cast a non-matching spell
   - Verify 32% total discount

4. **With Ars Nouveau Discount Rings:**
   - Equip Ring of Virtue + Ars Nouveau Greater Discount Ring
   - Verify discounts stack properly
   - Check for any conflicts or issues

### Debug Mode
Enable debug logging in `config/ars_n_spells-common.toml`:
```toml
debug_mode = true
```

This will log:
- Curio detection results
- Discount calculations
- School matching results
- Final mana costs

## Example Discount Calculations

### Scenario 1: Ring of Virtue Only
- Base Cost: 100 mana
- Ring of Virtue: 20% discount
- **Final Cost: 80 mana**

### Scenario 2: Fire Blasphemy + Fire Spell
- Base Cost: 100 mana
- Blasphemy Base: 15% discount
- Matching School Bonus: 10% discount
- **Final Cost: 75 mana** (25% total)

### Scenario 3: Ring of Virtue + Fire Blasphemy + Fire Spell
- Base Cost: 100 mana
- Ring of Virtue: 20% discount → 80 mana
- Blasphemy (matching): 25% discount → 80 × 0.75 = 60 mana
- **Final Cost: 60 mana** (40% total)

### Scenario 4: Ring of Virtue + Ice Blasphemy + Fire Spell (non-matching)
- Base Cost: 100 mana
- Ring of Virtue: 20% discount → 80 mana
- Blasphemy (non-matching): 15% discount → 80 × 0.85 = 68 mana
- **Final Cost: 68 mana** (32% total)

## Known Limitations

1. **School Detection:** Currently uses keyword-based heuristics. Some custom glyphs may not be categorized correctly.
2. **Spell Context:** School-specific discounts rely on `CasterContext` ThreadLocal, which may not always be populated.
3. **Fallback Behavior:** If school cannot be determined, generic discount is applied (no matching bonus).

## Future Enhancements

1. **Improved School Detection:** Use Ars Nouveau's spell tags or metadata for more accurate school detection
2. **Per-School Configuration:** Allow different discount percentages per spell school
3. **Discount Cap:** Add maximum total discount limit (e.g., 75% max)
4. **Visual Feedback:** Add tooltip showing active discounts on spell items
5. **Statistics Tracking:** Track total mana saved from curio discounts

## Version Information
- **Mod Version:** 1.2.0
- **Minecraft Version:** 1.20.1
- **Forge Version:** 47.2.0
- **Ars Nouveau Version:** 4.12.7
- **Iron's Spellbooks Version:** 3.15.2
- **Covenant of the Seven Version:** 2.2.5

## Build Information
- **Build Date:** February 2, 2026
- **Build Tool:** Gradle 8.4
- **Java Version:** 17
- **Output JAR:** `build/libs/ars_n_spells-1.2.0.jar`
