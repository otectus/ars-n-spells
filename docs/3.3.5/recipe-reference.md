# 3.3.5 recipe reference

Generated from every shipped ANS recipe in this loader checkout. Conditions and ingredients are shown verbatim; tag contents are resolved by the installed datapacks. A recipe ingredient is consumed when crafting that output, independently of whether the eventual ritual preserves its source book.

7 recipe files.

## `src/main/resources/data/ars_n_spells/recipe/apparatus/mana_infusion.json`

```json
{
  "neoforge:conditions": [
    {
      "type": "neoforge:mod_loaded",
      "modid": "irons_spellbooks"
    }
  ],
  "type": "ars_nouveau:enchanting_apparatus",
  "keepNbtOfReagent": false,
  "reagent": {
    "item": "ars_nouveau:blank_parchment"
  },
  "pedestalItems": [
    {
      "item": "ars_nouveau:source_gem_block"
    },
    {
      "item": "ars_nouveau:source_gem"
    },
    {
      "item": "irons_spellbooks:arcane_essence"
    },
    {
      "tag": "c:logs/archwood"
    }
  ],
  "result": {
    "id": "ars_n_spells:mana_infusion",
    "count": 1
  },
  "sourceCost": 1500
}
```

## `src/main/resources/data/ars_n_spells/recipe/apparatus/mana_well.json`

```json
{
  "neoforge:conditions": [
    {
      "type": "neoforge:mod_loaded",
      "modid": "irons_spellbooks"
    }
  ],
  "type": "ars_nouveau:enchanting_apparatus",
  "keepNbtOfReagent": false,
  "reagent": {
    "item": "ars_nouveau:blank_parchment"
  },
  "pedestalItems": [
    {
      "item": "ars_nouveau:source_gem_block"
    },
    {
      "item": "irons_spellbooks:arcane_essence"
    },
    {
      "item": "minecraft:water_bucket"
    },
    {
      "tag": "c:logs/archwood"
    }
  ],
  "result": {
    "id": "ars_n_spells:mana_well",
    "count": 1
  },
  "sourceCost": 2000
}
```

## `src/main/resources/data/ars_n_spells/recipe/apparatus/spell_transcription.json`

```json
{
  "neoforge:conditions": [
    {
      "type": "neoforge:mod_loaded",
      "modid": "irons_spellbooks"
    }
  ],
  "type": "ars_nouveau:enchanting_apparatus",
  "keepNbtOfReagent": false,
  "reagent": {
    "item": "ars_nouveau:novice_spell_book"
  },
  "pedestalItems": [
    {
      "tag": "ars_n_spells:irons_spell_books"
    },
    {
      "tag": "c:logs/archwood"
    },
    {
      "item": "ars_nouveau:source_gem_block"
    }
  ],
  "result": {
    "id": "ars_n_spells:spell_transcription",
    "count": 1
  },
  "sourceCost": 2000
}
```

## `src/main/resources/data/ars_n_spells/recipe/apparatus/spell_uninscription.json`

```json
{
  "type": "ars_nouveau:enchanting_apparatus",
  "keepNbtOfReagent": false,
  "reagent": {
    "item": "ars_nouveau:blank_parchment"
  },
  "pedestalItems": [
    {
      "item": "minecraft:water_bucket"
    },
    {
      "item": "ars_nouveau:source_gem"
    },
    {
      "tag": "c:logs/archwood"
    }
  ],
  "result": {
    "id": "ars_n_spells:spell_uninscription",
    "count": 1
  },
  "sourceCost": 500
}
```

## `src/main/resources/data/ars_n_spells/recipe/apparatus/spellbook_binding.json`

```json
{
  "neoforge:conditions": [
    {
      "type": "neoforge:mod_loaded",
      "modid": "irons_spellbooks"
    }
  ],
  "type": "ars_nouveau:enchanting_apparatus",
  "keepNbtOfReagent": false,
  "reagent": {
    "item": "ars_nouveau:novice_spell_book"
  },
  "pedestalItems": [
    {
      "tag": "ars_n_spells:irons_spell_books"
    },
    {
      "item": "irons_spellbooks:scroll"
    },
    {
      "item": "ars_nouveau:source_gem_block"
    },
    {
      "tag": "c:logs/archwood"
    }
  ],
  "result": {
    "id": "ars_n_spells:spellbook_binding",
    "count": 1
  },
  "sourceCost": 2500
}
```

## `src/main/resources/data/ars_n_spells/recipe/blank_scroll.json`

```json
{
  "type": "minecraft:crafting_shapeless",
  "ingredients": [
    {
      "item": "ars_nouveau:blank_parchment"
    },
    {
      "item": "ars_nouveau:source_gem"
    }
  ],
  "result": {
    "id": "ars_n_spells:blank_scroll",
    "count": 1
  }
}
```

## `src/main/resources/data/ars_n_spells/recipe/spell_loom.json`

```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": [
    " G ",
    "LBL",
    "OOO"
  ],
  "key": {
    "G": {
      "item": "minecraft:gold_ingot"
    },
    "L": {
      "item": "minecraft:lapis_lazuli"
    },
    "B": {
      "item": "minecraft:book"
    },
    "O": {
      "item": "minecraft:obsidian"
    }
  },
  "result": {
    "id": "ars_n_spells:spell_loom",
    "count": 1
  }
}
```
