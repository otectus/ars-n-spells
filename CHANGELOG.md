# Changelog - Ars 'n' Spells

All notable changes to this project will be documented in this file.

## [1.2.0] - 2026-02-02

### Added - Covenant of the Seven Integration

#### Ring of Virtue & Blasphemy Curio Discounts
- **Ring of the Seven Virtues Support**
  - Provides 20% mana cost reduction for all Ars Nouveau spells (configurable)
  - Automatically detected when equipped in curio slot
  - Stacks multiplicatively with Blasphemy discounts (if enabled)

- **Blasphemy Curio Support (All 13 Variants)**
  - Base 15% mana discount for all Ars Nouveau spells
  - Additional 10% bonus when Blasphemy school matches spell school (25% total)
  - Supported variants:
    - Fire, Ice, Lightning, Holy, Ender, Blood, Evocation, Nature, Eldritch, Aqua, Geo, Wind, Dormant
  - School-specific matching with intelligent keyword detection
  - Configurable base discount and matching bonus

- **Discount Stacking System**
  - Multiplicative stacking: Ring of Virtue + Blasphemy = 32-40% total discount
  - Maximum discount with matching school: 40%
  - Configurable stacking behavior (can be disabled)

#### Cursed Ring LP Consumption System
- **Ars Nouveau Spell LP Costs**
  - Spells consume Life Points (LP) from Blood Magic instead of mana
  - Configurable LP formula: `LP = (Mana × Base) × Tier Multiplier`
  - Separate multipliers for Tier 1, 2, and 3 glyphs
  - Minimum LP cost enforcement
  - Spell effects apply correctly (fixed visual-only bug)
  - Blasphemy discounts apply to LP costs (85% for matching schools)

- **Iron's Spellbooks Spell LP Costs**
  - Enhanced integration with Sanctified Legacy's native LP system
  - Configurable LP formula: `LP = (Mana × Base) × (1 + Level × LevelMult) × RarityMult`
  - Separate multipliers for each rarity tier (Common through Legendary)
  - LP cost messages now displayed for Iron's spells
  - Insufficient LP messages shown consistently

- **Death Penalty System**
  - **Safe Mode** (default): Spell cancelled, 1 heart damage, player survives
  - **Death Mode**: Spell casts, player dies instantly
  - Configurable via `death_on_insufficient_lp` setting
  - Triple-layer death prevention:
    - Layer 1: Damage interception (LivingHurtEvent)
    - Layer 2: Damage application (LivingDamageEvent)
    - Layer 3: Death event cancellation (LivingDeathEvent)
  - Handles multiple death events from Blood Magic
  - Intercepts "sacrifice" damage type
  - 2-second window for death prevention

- **LP Cost Messages**
  - Shows "Consumed XXX LP" on successful casts
  - Shows "Insufficient LP - Spell Cancelled" on failures
  - Configurable via `show_lp_cost_messages` setting
  - Consistent messaging for both Ars and Iron's spells

### Added - Configuration Options

#### Curio Discount System (5 new options)
- `enable_curio_discounts` - Master toggle (default: true)
- `virtue_ring_discount` - Ring of Virtue discount percentage (default: 0.2)
- `blasphemy_discount` - Blasphemy base discount (default: 0.15)
- `blasphemy_matching_school_bonus` - Matching school bonus (default: 0.1)
- `allow_discount_stacking` - Enable discount stacking (default: true)

#### Cursed Ring LP System (2 new options)
- `death_on_insufficient_lp` - Death penalty toggle (default: false)
- `show_lp_cost_messages` - Show LP cost messages (default: true)

#### LP Calculation - Ars Nouveau (5 new options)
- `ars_lp_base_multiplier` - Base LP conversion (default: 10.0)
- `ars_lp_tier1_multiplier` - Tier 1 multiplier (default: 1.5)
- `ars_lp_tier2_multiplier` - Tier 2 multiplier (default: 2.0)
- `ars_lp_tier3_multiplier` - Tier 3 multiplier (default: 2.5)
- `ars_lp_minimum_cost` - Minimum LP cost (default: 100)

#### LP Calculation - Iron's Spellbooks (8 new options)
- `irons_lp_base_multiplier` - Base LP conversion (default: 10.0)
- `irons_lp_per_level_multiplier` - Level scaling (default: 0.1)
- `irons_lp_minimum_cost` - Minimum LP cost (default: 100)
- `irons_lp_common_multiplier` - Common rarity (default: 1.0)
- `irons_lp_uncommon_multiplier` - Uncommon rarity (default: 1.5)
- `irons_lp_rare_multiplier` - Rare rarity (default: 2.0)
- `irons_lp_epic_multiplier` - Epic rarity (default: 3.0)
- `irons_lp_legendary_multiplier` - Legendary rarity (default: 5.0)

**Total: 22 new configuration options**

### Added - New Event Handlers
- `CurioDiscountHandler` - Applies Ring of Virtue and Blasphemy mana discounts
- `CursedRingHandler` - Handles Cursed Ring LP consumption for Ars Nouveau spells
- `IronsLPHandler` - Handles LP cost messages for Iron's Spellbooks spells
- `LPDeathPrevention` - Prevents death from insufficient LP in safe mode

### Added - Compatibility Layer Enhancements
- Extended `SanctifiedLegacyCompat` with curio detection methods
- Added `hasVirtueRing()` - Detects Ring of the Seven Virtues
- Added `hasAnyBlasphemy()` - Detects any Blasphemy curio
- Added `hasBlasphemyType()` - Detects specific Blasphemy variant
- Added `getMatchingBlasphemyType()` - Maps spell schools to Blasphemy types
- Added `determineSpellSchool()` - Determines spell school from Ars glyphs
- Added `calculateIronsLPCost()` - Configurable LP formula for Iron's spells
- Improved `calculateLPCost()` - Now uses configurable multipliers
- Fixed curio detection to use CuriosUtil (Ars Nouveau API) instead of reflection
- Fixed Blood Magic API integration (correct method signatures)

### Added - Equipment Integration
- Added `CurioDiscountData` class for caching discount information
- Added `getCurioDiscounts()` method for retrieving cached discount data
- Updated `CachedEquipmentData` to include curio discount information
- Curio discount data cached for 1 second (same as other equipment bonuses)

### Changed
- Updated `ArsNSpells.java` to register new event handlers
- Enhanced logging throughout for better debugging
- Improved error messages and user feedback
- Updated configuration file structure with new sections

### Fixed
- Fixed Cursed Ring detection using CuriosUtil instead of non-existent SuperpositionHandler
- Fixed LP consumption for Ars Nouveau spells (spell effects now apply correctly)
- Fixed death prevention system to handle multiple death events
- Fixed "sacrifice" damage type interception (Blood Magic's LP death penalty)
- Fixed spell cast marker persistence across multiple death events
- Fixed insufficient LP message display for Iron's Spellbooks spells
- Increased death prevention window from 500ms to 2000ms for reliability

### Technical Changes
- Switched from mixin-based to event-based Cursed Ring handling for better compatibility
- Implemented triple-layer death prevention system
- Added spell cast marker tracking with 2-second window
- Enhanced curio detection with detailed logging
- Improved Blood Magic Soul Network integration
- Added comprehensive LP calculation system with configurable formulas

### Documentation
- Added `CURIO_DISCOUNT_IMPLEMENTATION.md` - Technical implementation details
- Added `TESTING_GUIDE.md` - Comprehensive testing procedures
- Added `LP_CALCULATION_GUIDE.txt` - LP formula configuration guide
- Added `COMPLETE_IMPLEMENTATION_SUMMARY.md` - Full feature documentation
- Updated `CF_DESCRIPTION.md` - CurseForge description with new features

---

## [1.1.2] - Previous Version

### Features
- Mana unification modes (ISS_PRIMARY, ARS_PRIMARY, HYBRID, SEPARATE, DISABLED)
- Gear perks and enchantments integration
- Spell scaling with Iron's attributes
- Resonance system for full-mana bonuses
- Unified cooldown system
- Progression and affinity tracking
- Cross-mod spell casting (experimental)

---

## Version Comparison

### v1.1.2 → v1.2.0 Summary
- **+22 configuration options** for curio discounts and LP costs
- **+4 new event handlers** for curio integration
- **+10 new methods** in SanctifiedLegacyCompat
- **+3 new classes** (CurioDiscountHandler, CursedRingHandler, IronsLPHandler, LPDeathPrevention)
- **Full Covenant of the Seven integration** with Ring of Virtue, Blasphemy, and Cursed Ring
- **Comprehensive LP calculation system** with configurable formulas
- **Death prevention system** with safe mode and death mode
- **Enhanced messaging** for better user experience

---

## Migration Guide: v1.1.2 → v1.2.0

### Configuration Changes
Your existing `ars_n_spells-common.toml` will automatically gain new sections:
- `["Curio Discount System"]`
- `["Cursed Ring LP System"]`
- `["LP Calculation - Ars Nouveau"]`
- `["LP Calculation - Iron's Spellbooks"]`
- `["LP Rarity Multipliers - Iron's Spells"]`

All new options have sensible defaults. No action required unless you want to customize.

### Behavior Changes
- **No breaking changes** - All existing features work the same
- **New features are opt-in** - Curio discounts can be disabled
- **Cursed Ring now works** - Previously non-functional, now fully implemented
- **Better messages** - Cleaner, more concise user feedback

### Recommended Settings
For balanced gameplay (default):
```toml
enable_curio_discounts = true
death_on_insufficient_lp = false
show_lp_cost_messages = true
```

For hardcore mode:
```toml
death_on_insufficient_lp = true
ars_lp_base_multiplier = 20.0
irons_lp_legendary_multiplier = 10.0
```

---

## Credits

**Mod Author:** Otectus  
**Integration Support:** Covenant of the Seven (Sanctified Legacy) by llenzzz  
**Dependencies:** Ars Nouveau, Iron's Spells 'n Spellbooks, Blood Magic, Curios API

---

## License

GNU GPLv3

---

## Links

- **CurseForge:** [Ars 'n' Spells](https://www.curseforge.com/minecraft/mc-mods/ars-n-spells)
- **Issues:** Report bugs and request features on CurseForge
- **Discord:** Join for support and updates

---

*Last Updated: February 2, 2026*
