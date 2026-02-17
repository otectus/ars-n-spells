# Cursed Ring LP Consumption Fix

## Problem
Ars Nouveau spells were casting for free when wearing the Ring of Seven Curses, instead of consuming Life Points (LP) from Blood Magic.

## Root Cause Analysis

The Cursed Ring integration was partially implemented but had insufficient logging to diagnose why LP consumption wasn't working. The code flow is:

1. **Pre-Cast Validation** (`MixinSpellResolverPreCast`) - Validates resources BEFORE spell executes
2. **Casting Authority** (`CastingAuthority.canCastArsSpell()`) - Checks for Cursed Ring
3. **LP Consumption** (`SanctifiedLegacyCompat.consumeLP()`) - Consumes LP via Blood Magic API
4. **Mana Cancellation** (`MixinSpellResolverMana.expendMana()`) - Prevents mana consumption

## Changes Made

### 1. Enhanced Logging in `CastingAuthority.java`
Added comprehensive logging to track:
- Pre-cast validation entry
- Cursed Ring detection
- LP cost calculation
- Spell school detection
- Blasphemy multiplier application
- LP consumption success/failure

### 2. Enhanced Logging in `MixinSpellResolverPreCast.java`
Added logging to confirm:
- Mixin is being triggered
- Player and cost information
- Validation results (allow/deny)

### 3. Enhanced Logging in `SanctifiedLegacyCompat.java`
Added detailed logging for:
- Availability checks
- Soul Network retrieval
- SoulTicket creation
- LP syphon operation
- Success/failure results

### 4. Enabled Debug Mode by Default
- Set `debug_mode = true` in config
- Ensures all diagnostic messages are visible

## Expected Log Output

When working correctly, you should see:

```
[INFO] 🔍 PRE-CAST VALIDATION: Player=Steve, Cost=50, Side=SERVER
[INFO] canCastArsSpell: Spell cost = 50 mana for player Steve
[INFO] canCastArsSpell: Cursed Ring check = true
[INFO] 🔴 CURSED RING DETECTED - Using LP instead of mana for Steve
[INFO] 🔴 validateCursedRingCost called for player: Steve, mana cost: 50
[INFO]    Spell part: ars_nouveau:glyph_ignite
[INFO]    Calculated LP cost: 750 (base mana: 50)
[INFO]    Detected spell school: fire
[INFO]    Final LP cost: 750
[INFO]    Attempting to consume LP...
[INFO] 💉 Attempting to consume 750 LP from Steve's Soul Network
[INFO]    Soul Network obtained: wayoftime.bloodmagic.core.data.SoulNetwork
[INFO]    SoulTicket created for 750 LP
[INFO]    ✅ Successfully consumed 750 LP from Steve's Soul Network for Ars spell
[INFO]    ✅ LP consumption successful!
[INFO] ✅ SPELL CAST ALLOWED for Steve
```

## Diagnostic Steps

### Step 1: Verify Mods Are Loaded
Check startup logs for:
```
[INFO] OK Sanctified Legacy compatibility enabled
[INFO]    - Cursed Ring support for Ars Nouveau spells
[INFO]    - Virtue Ring support for Ars Nouveau spells
```

### Step 2: Verify Cursed Ring Is Equipped
- Must be in a curio slot (not inventory)
- Must NOT also be wearing Ring of Virtue (they cancel out)
- Item ID should be `enigmaticlegacy:cursed_ring`

### Step 3: Verify Blood Magic Network
- Must have a Blood Magic altar
- Must be bound to the altar
- Must have LP in your network

### Step 4: Cast a Spell and Check Logs
Look for the log messages above in `logs/latest.log`

## Possible Issues

### Issue 1: Pre-Cast Validation Not Running
**Symptom:** No "PRE-CAST VALIDATION" messages in log

**Causes:**
- `enable_mana_unification = false` in config
- Mixin not being applied (conflict with another mod)
- Playing in creative mode

**Solution:**
- Set `enable_mana_unification = true`
- Check for mixin conflicts
- Test in survival mode

### Issue 2: Cursed Ring Not Detected
**Symptom:** "Cursed Ring check = false" in log

**Causes:**
- Ring not equipped in curio slot
- Also wearing Ring of Virtue (they cancel out)
- Sanctified Legacy not loaded
- Reflection initialization failed

**Solution:**
- Verify ring is in curio slot
- Remove Ring of Virtue if equipped
- Check if `covenant_of_the_seven` mod is loaded
- Look for initialization errors in startup logs

### Issue 3: Soul Network Is Null
**Symptom:** "Soul Network is null for player" in log

**Causes:**
- No Blood Magic altar created
- Not bound to an altar
- Blood Magic not properly initialized

**Solution:**
- Create a Blood Magic altar
- Bind yourself to the altar (Divination Sigil)
- Add LP to your network

### Issue 4: LP Consumption Fails
**Symptom:** "Failed to consume LP (insufficient LP)" in log

**Causes:**
- Not enough LP in network
- LP cost too high for current network

**Solution:**
- Add more LP to your network
- Check calculated LP cost in logs
- Equip matching Blasphemy for 85% LP discount

### Issue 5: Reflection Errors
**Symptom:** "Failed to consume LP from Soul Network" with exception

**Causes:**
- Blood Magic API changed
- Class/method names don't match
- Version incompatibility

**Solution:**
- Verify Blood Magic version (should be 3.3.5-47)
- Check exception stack trace for specific error
- May need to update reflection code

## LP Cost Formula

```
Base LP Cost = Mana Cost × 10
Tier Multiplier = 1.0 + (Tier × 0.5)
Final LP Cost = Base LP Cost × Tier Multiplier
Minimum LP Cost = 100 LP
```

Examples:
- 50 mana, Tier 1: 50 × 10 × 1.5 = 750 LP
- 100 mana, Tier 2: 100 × 10 × 2.0 = 2000 LP
- 20 mana, Tier 1: 20 × 10 × 1.5 = 300 LP

With Blasphemy (85% discount):
- 750 LP × 0.15 = 112 LP (matching school)

## Testing Procedure

1. **Setup:**
   - Create Blood Magic altar
   - Bind to altar
   - Add 10,000 LP to network
   - Equip Ring of Seven Curses
   - Prepare simple Ars spell (Projectile → Ignite)

2. **Test:**
   - Note current LP amount
   - Cast the spell
   - Check if LP decreased
   - Check log for diagnostic messages

3. **Expected Result:**
   - LP should decrease by ~750 LP
   - Spell should cast successfully
   - Log should show all diagnostic messages

4. **If It Fails:**
   - Copy the relevant log section
   - Look for error messages
   - Check which step failed
   - Refer to "Possible Issues" section above

## Files Modified

1. `CastingAuthority.java` - Added comprehensive logging
2. `MixinSpellResolverPreCast.java` - Added validation logging
3. `SanctifiedLegacyCompat.java` - Added LP consumption logging
4. `ars_n_spells-common.toml` - Enabled debug mode

## Build Info

- **JAR:** `ars_n_spells-1.2.0.jar` (215 KB)
- **Location:** `mods/ars_n_spells-1.2.0.jar`
- **Build Date:** February 2, 2026
- **Status:** ✅ Built and deployed with enhanced logging

## Next Steps

1. Launch Minecraft
2. Test Cursed Ring with Ars Nouveau spells
3. Check `logs/latest.log` for diagnostic output
4. Report back with log findings

The enhanced logging will pinpoint EXACTLY where the LP consumption is failing!

================================================================================
