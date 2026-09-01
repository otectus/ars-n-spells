# Covenant of the Seven integration — not compiled on 1.21.1

**Status: blocked upstream. Nothing in this directory is on any source set, so none of it is
compiled or shipped.**

## Why it is here and not deleted

Ars 'n' Spells 3.2.0 on Forge 1.20.1 integrates with **Covenant of the Seven** (mod id
`covenant_of_the_seven`, root package `net.llenzzz.covenant_of_the_seven`, published across the
`covenant-of-the-seven`, `covenant-of-the-seven-curses` and `sanctified-legacy` CurseForge
slugs). That integration is a substantial share of the mod's feature surface:

- **Ring of Seven Curses** — Ars and Iron's mana costs become Life Points, with three LP source
  modes, a configurable cost ladder, and a death-on-insufficient-LP option.
- **Ring of Seven Virtues** — Ars mana costs become Covenant aura.
- **13 Blasphemy curios** — per-school mana and LP discounts.
- **Aura HUD rewiring** — a `@Pseudo` mixin that replaces Covenant's hardcoded 2,000,000 aura
  divisor with the player's session peak.
- Ring-conflict handling, Blood Magic and Nature's Aura reflection bridges, and same-tick death
  prevention.

**Covenant of the Seven has no 1.21.1 release.** As of August 2026 CurseForge lists it for
1.20.1 / Forge only, newest file `covenant_of_the_seven-1.0.3.jar` (March 2026), and no public
source repository exists. There is therefore nothing to compile against and nothing to test
against.

The previous porting attempt deleted this integration outright (commit `72ee2a0`, "…+ remove
Sanctified Legacy"). Keeping the sources instead means the parity gap is *documented and
recoverable* rather than silently lost — the 1.20.1 line took two audits to get this subsystem
correct, and re-deriving it from scratch later would repeat that work.

## What these files are

**1.20.1 Forge sources, unported.** They still import `net.minecraftforge.*`, read ItemStack NBT
directly, and use Forge capabilities. They are a reference for the *behaviour and its rationale*,
not drop-in code. Porting them is a real task, not a copy — at minimum:

| Area | Work needed |
|---|---|
| Events | `net.minecraftforge.eventbus` → `net.neoforged.bus`; `TickEvent.PlayerTickEvent` → `PlayerTickEvent.Post`; `LivingHurtEvent` → `LivingIncomingDamageEvent` |
| `LPDeathPrevention` | `LivingHurtEvent`/`LivingDeathEvent` at HIGHEST, same-tick immunity scope |
| `MixinResourceBarOverlay` | Pinned to Covenant **2.2.6** bytecode (`@ModifyConstant` on the literal `2_000_000`, `@Redirect` on `String.valueOf` at `ordinal = 0`). Covenant's 1.21.1 build, if it ever exists, will also have had to move off Forge's removed `IGuiOverlay` to `LayeredDraw.Layer`, so this mixin should be assumed dead until re-verified against a real jar. |
| Item NBT | The ring/curio checks read tags; on 1.21.1 they must go through data components |

## What is already in place for re-enabling

- **Item tags** `cursed_rings`, `virtue_rings` and `blasphemy_curios` are declared in
  `registry/ModTags` and their JSON is staged here. All entries are `"required": false`, so they
  load cleanly with Covenant absent.
- **Config keys are intentionally still defined** in `AnsConfig` (`enable_lp_system`,
  `lp_source_mode`, `death_on_insufficient_lp`, the `ars_lp_*` / `irons_lp_*` ladders,
  `blasphemy_*`, `enable_virtue_aura_system`, `ars_virtue_aura_multiplier`, `aura_failure_mode`,
  `hide_mana_bar_with_ring`, `show_lp_cost_messages`), documented as inert. Keeping them means a
  server's existing TOML carries over unchanged and re-enabling is a compile-scope change rather
  than a config migration.
- **Extension points are marked in place** rather than merely deleted. Each carries a comment
  saying what belongs there and why the ordering matters:
  - `mixin/irons/MixinScrollItem` — the Cursed-Ring LP branch runs *ahead of* the mana check and
    returns early, which is what makes LP *replace* mana instead of adding to it.
  - `mixin/irons/MixinIronsCastValidation` — the ring bypass returns `Float.MAX_VALUE` and must
    win over the ARS_PRIMARY conversion, so it belongs at the top of the redirect.
  - `mixin/ars/MixinSpellResolverPreCast` — LP/aura validation lives in the `cost <= 0` branch,
    because the ring handlers zero the mana cost during `SpellCostCalcEvent` and stash a pending
    alternate cost.

## Consequences while blocked

- `scroll_cost_mode = lp_only` behaves as `free` (no LP system to charge).
- Ring and blasphemy config keys have no effect.
- `/ans aura` reports nothing useful.
- `hide_mana_bar_with_ring` never triggers.

## Re-enabling

1. Confirm a 1.21.1 build of Covenant exists and note its mod id and root package.
2. Move these sources into `src/main/java/`, port them per the table above.
3. Re-add `sanctified.MixinSanctifiedAbstractSpell` and `covenant.MixinResourceBarOverlay` to
   `ars_n_spells.mixins.json` **and** to `ArsNSpellsMixinPlugin`'s gate — the plugin must probe
   for Covenant with `getResource(".class")`, never `Class.forName`, which classloads the target
   and caused a real `MixinTargetAlreadyLoadedException` against Covenant's own mixin.
4. Move the three tag JSONs into `src/main/resources/data/ars_n_spells/tags/item/`.
5. Fill in the three marked extension points above.
