# Imported glyphs in the Ars spellbook

Open an Ars spellbook and select **Iron's glyphs**. The **Iron's Glyph Library** groups the current player's learned glyphs by their original spell. A player who knows Firebolt I, III and VIII sees one Firebolt entry and can inspect those three levels. Add names the exact level it will insert. The book's usual Save or Create button saves the draft.

## Choose a display mode

Open **Mods → Ars 'n' Spells → Config → Personal display**, or choose **Display settings…** in the library. Select **Imported Iron's glyphs** to cycle the mode, then **Apply**. Cancel discards the local edit. Reset selects Organized and keeps favorites. This setting also works at the title screen, for a LAN guest and for a player without operator permission.

| Mode | Value | What appears in the editor |
| --- | --- | --- |
| Organized (default) | `grouped` | The ordinary palette contains Ars and addon glyphs. The bookmark opens one row per original imported spell. A matching native search can open the library with that query. |
| Individual levels | `flat` | Available imported glyph levels also appear in the ordinary palette. The bookmark still opens the grouped library. |
| Hidden | `hidden` | Imported candidates, search shortcuts, acquisition entries and the bookmark disappear. Open Personal display in the mod settings to bring them back. |

To hide imported browsing, edit `config/ars_n_spells-client.toml` in your game instance.

```toml
[spellbook]
    iron_glyph_display_mode = "hidden"
```

Use `"grouped"` for the default or `"flat"` for individual levels. These lowercase values are shared by Forge and NeoForge. Each client chooses independently.

**Glyphs already in a spell stay visible in all three modes.** Its recipe, name and level choices belong to that spell. An existing valid spell can still cast in Hidden mode. Your knowledge, mana bonuses and the physical glyph items are preserved. The library reduces the number of entries you browse. Registry size, models and saved identities are the same.

To disable imported-glyph gameplay for a world, the server owner can change the setting below. Forge reads `<world>/serverconfig/ars_n_spells-server.toml`. NeoForge reads an existing file at that path first and otherwise uses `config/ars_n_spells-server.toml` in the server or single-player instance.

```toml
["Imported Iron's Glyphs"]
    enable_iron_glyphs = false
```

This applies the world's existing learning, Glyph Press and casting restrictions to every player.

## Find a spell and choose its level

- Search **Learned** by localized name, original spell ID or source mod. `@namespace` searches a source namespace. An exact learned level name such as `Firebolt III` narrows the match. The full spell ID in the row tooltip distinguishes same-named spells from different addons.
- Use the source and school controls to cycle through the options. **Fits this book** starts enabled. Switch to **All book tiers** to inspect levels that require a higher-tier book. **Reset filters** returns to Learned with an empty search.
- Select a spell to inspect its levels. The initial selection is the lowest eligible learned level. Choose a different level explicitly. **Add** checks the live settings, your knowledge, the book tier and the current draft again. A rejected selection stays in the library so you can read the reason. Iron's own learning requirements still apply when casting.
- Use the star beside a spell to add or remove it from **Favorites**. Favorites follow the original spell ID across worlds. A spell appears there when this book can browse its currently available knowledge. Favorites are kept in `config/ars_n_spells/glyph_browser.json`.
- The Scribe's Table browser exposes only actual recipes for imported glyphs. Use the Glyph Press to engrave a scroll when the pack supplies no Scribe's Table recipe. Inventory items, recipe viewers and Iron's wheel retain their usual behavior.

## Dormant glyphs and server settings

Open **Dormant** to inspect learned levels disabled by the world, a datapack, a missing source or the live level limits. A spell with both available and dormant levels also has a **Dormant levels** control in its details. The reason is shown for the concrete level. Restoring support makes that same learned identity available again.

**Waiting for server spell settings…** means that the client is awaiting the accepted spell settings. Keep the editor open while the server finishes joining or reloading. If the message persists after login, check the client and server logs for the imported-glyph handshake and matching mod/catalog versions. Changing the local mode cannot resolve a server sync failure.

Existing learned sets are organized on first open. The book checks for new knowledge within ten client ticks, including knowledge granted by another addon. The supported editors are Ars Nouveau's standard and Infinity editors on Forge 1.20.1 and its modern slotted editor on NeoForge 1.21.1. A third-party replacement needs a compatible editor host to offer the library. Its own custom discovery lists can have different behavior.

[Verification results and screenshots](glyph-browser-testing.md)
