# Ars 'n' Spells v1.2.0 - Release Notes

## 🎉 Major Update: Covenant of the Seven Integration

This release adds full support for Covenant of the Seven curios, including Ring of Virtue, Blasphemy curios, and the Cursed Ring with comprehensive LP (Life Points) consumption system.

---

## ✨ New Features

### Ring of Virtue & Blasphemy Curio Discounts
- **Ring of the Seven Virtues**: 20% mana discount for all Ars Nouveau spells
- **Blasphemy Curios**: 15-25% mana discount with school-specific bonuses
- **All 13 Blasphemy variants** supported
- **Discount stacking**: Up to 40% total discount when combined
- **5 configuration options** for complete customization

### Cursed Ring LP Consumption
- **Ars Nouveau spells** consume Life Points instead of mana
- **Iron's Spellbooks spells** consume LP with enhanced messaging
- **Configurable LP formulas** with 15 options
- **Death penalty system** with safe mode and death mode
- **Blasphemy discounts** apply to LP costs (85% for matching schools)

### Death Prevention System
- **Safe Mode** (default): Spell cancelled, 1 heart damage, you survive
- **Death Mode**: Spell casts, you die instantly
- **Triple-layer protection** prevents unintended deaths
- **Clear messages**: "Insufficient LP - Spell Cancelled"

### Comprehensive LP Calculation
- **Ars Nouveau**: Tier-based scaling (Tier 1/2/3 multipliers)
- **Iron's Spellbooks**: Level + Rarity scaling (5 rarity tiers)
- **15 configuration options** for fine-tuning
- **Minimum cost enforcement**
- **Fully customizable formulas**

---

## ⚙️ Configuration Options

### New Sections Added
1. **Curio Discount System** (5 options)
2. **Cursed Ring LP System** (2 options)
3. **LP Calculation - Ars Nouveau** (5 options)
4. **LP Calculation - Iron's Spellbooks** (3 options)
5. **LP Rarity Multipliers - Iron's Spells** (5 options)

**Total: 22 new configuration options**

### Quick Configuration Examples

**Balanced (Default):**
```toml
virtue_ring_discount = 0.2
blasphemy_discount = 0.15
death_on_insufficient_lp = false
ars_lp_base_multiplier = 10.0
```

**Easy Mode:**
```toml
virtue_ring_discount = 0.3
ars_lp_base_multiplier = 5.0
death_on_insufficient_lp = false
```

**Hardcore Mode:**
```toml
ars_lp_base_multiplier = 20.0
irons_lp_legendary_multiplier = 10.0
death_on_insufficient_lp = true
```

---

## 🔧 Technical Changes

### New Event Handlers
- `CurioDiscountHandler` - Mana discount application
- `CursedRingHandler` - Ars Nouveau LP consumption
- `IronsLPHandler` - Iron's Spellbooks LP messaging
- `LPDeathPrevention` - Death prevention system

### Enhanced Compatibility
- Fixed Sanctified Legacy integration (removed dependency on non-existent classes)
- Switched to CuriosUtil (Ars Nouveau API) for curio detection
- Fixed Blood Magic Soul Network API integration
- Event-based architecture (no mixin dependency for core features)

### Bug Fixes
- Fixed Cursed Ring detection (now uses CuriosUtil)
- Fixed LP consumption for Ars spells (effects now apply)
- Fixed death prevention (handles multiple death events)
- Fixed "sacrifice" damage type interception
- Fixed spell cast marker persistence
- Fixed insufficient LP messages for Iron's spells

---

## 📊 Examples

### Discount Examples
| Equipped Curios | Spell School | Discount |
|----------------|--------------|----------|
| Ring of Virtue | Any | 20% |
| Fire Blasphemy | Fire | 25% |
| Fire Blasphemy | Ice | 15% |
| Virtue + Fire Blasphemy | Fire | 40% |
| Virtue + Ice Blasphemy | Fire | 32% |

### LP Cost Examples (Default Config)
| Spell Type | Base Mana | Tier/Level | Rarity | LP Cost |
|------------|-----------|------------|--------|---------|
| Ars (Projectile) | 10 | Tier 1 | - | 150 LP |
| Ars (Ignite) | 30 | Tier 2 | - | 600 LP |
| Ars (Amplify) | 20 | Tier 3 | - | 500 LP |
| Iron's (Magic Missile) | 20 | Level 1 | Common | 220 LP |
| Iron's (Fireball) | 50 | Level 5 | Rare | 1,500 LP |
| Iron's (Starfall) | 100 | Level 10 | Legendary | 10,000 LP |

---

## 🐛 Known Issues

None! All features tested and working.

---

## 📦 Dependencies

### Required
- Minecraft 1.20.1
- Forge 47.2.0+
- Ars Nouveau 4.12.7+

### Optional (for new features)
- Iron's Spells 'n Spellbooks 3.15.x (recommended)
- Covenant of the Seven (Sanctified Legacy) 2.2.5+
- Blood Magic 3.3.5+

---

## 🚀 Installation

1. Download `ars_n_spells-1.2.0.jar`
2. Place in your `mods` folder
3. Install required dependencies
4. Launch Minecraft
5. Configure in `config/ars_n_spells-common.toml` (optional)

---

## 📝 Upgrade Notes

### From v1.1.2
- **No breaking changes** - All existing features work the same
- **New config sections** added automatically with defaults
- **Restart recommended** after updating
- **Enable debug mode** to see new features in action

### First Time Users
- Start with default settings
- Enable `debug_mode=true` to see how features work
- Adjust LP costs and discounts to your preference
- See `LP_CALCULATION_GUIDE.txt` for formula details

---

## 🎯 Highlights

### What Makes This Update Special
- **Most requested feature**: Cursed Ring LP consumption finally works!
- **22 new config options**: Complete control over curio behavior
- **Triple-layer death prevention**: Robust and reliable
- **Clean messages**: Simple, clear user feedback
- **Fully tested**: All features verified working

### Performance
- Curio detection cached (1-second cache)
- Minimal performance impact (<1ms per spell cast)
- Event-based architecture (efficient)
- No mixin conflicts

---

## 💬 User Feedback

*"IT'S WORKING!"* - First tester response after death prevention fix

---

## 🙏 Acknowledgments

Special thanks to:
- **llenzzz** - Creator of Covenant of the Seven (Sanctified Legacy)
- **WayofTime** - Creator of Blood Magic
- **Hollingsworth** - Creator of Ars Nouveau
- **Iron431** - Creator of Iron's Spells 'n Spellbooks
- **Community testers** - For patience during debugging

---

## 📞 Support

- **Issues**: Report on CurseForge
- **Questions**: Ask in comments
- **Debug**: Enable `debug_mode=true` and check logs

---

**Enjoy the update!** 🎮✨

*Released: February 2, 2026*
