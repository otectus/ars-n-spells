# 3.3.5 recipe reference

Generated from every shipped ANS recipe in this loader checkout. Conditions and ingredients are shown verbatim; tag contents are resolved by the installed datapacks. A recipe ingredient is consumed when crafting that output, independently of whether the eventual ritual preserves its source book.

7 recipe files.

## `src/main/resources/data/ars_n_spells/recipes/apparatus/mana_infusion.json`

```json
{
  "type": "forge:conditional",
  "recipes": [
    {
      "conditions": [
        {
          "type": "forge:mod_loaded",
          "modid": "irons_spellbooks"
        }
      ],
      "recipe": {
        "type": "ars_nouveau:enchanting_apparatus",
        "keepNbtOfReagent": false,
        "output": {
          "item": "ars_n_spells:mana_infusion"
        },
        "reagent": [
          {
            "item": "ars_nouveau:blank_parchment"
          }
        ],
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
            "tag": "forge:logs/archwood"
          }
        ],
        "sourceCost": 1500
      }
    }
  ]
}
```

## `src/main/resources/data/ars_n_spells/recipes/apparatus/mana_well.json`

```json
{
  "type": "forge:conditional",
  "recipes": [
    {
      "conditions": [
        {
          "type": "forge:mod_loaded",
          "modid": "irons_spellbooks"
        }
      ],
      "recipe": {
        "type": "ars_nouveau:enchanting_apparatus",
        "keepNbtOfReagent": false,
        "output": {
          "item": "ars_n_spells:mana_well"
        },
        "reagent": [
          {
            "item": "ars_nouveau:blank_parchment"
          }
        ],
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
            "tag": "forge:logs/archwood"
          }
        ],
        "sourceCost": 2000
      }
    }
  ]
}
```

## `src/main/resources/data/ars_n_spells/recipes/apparatus/spell_transcription.json`

```json
{
  "type": "forge:conditional",
  "recipes": [
    {
      "conditions": [
        {
          "type": "forge:mod_loaded",
          "modid": "irons_spellbooks"
        }
      ],
      "recipe": {
        "type": "ars_nouveau:enchanting_apparatus",
        "keepNbtOfReagent": false,
        "output": {
          "item": "ars_n_spells:spell_transcription"
        },
        "reagent": [
          {
            "item": "ars_nouveau:novice_spell_book"
          }
        ],
        "pedestalItems": [
          {
            "tag": "ars_n_spells:irons_spell_books"
          },
          {
            "tag": "forge:logs/archwood"
          },
          {
            "item": "ars_nouveau:source_gem_block"
          }
        ],
        "sourceCost": 2000
      }
    }
  ]
}
```

## `src/main/resources/data/ars_n_spells/recipes/apparatus/spell_uninscription.json`

```json
{
  "type": "ars_nouveau:enchanting_apparatus",
  "keepNbtOfReagent": false,
  "output": {
    "item": "ars_n_spells:spell_uninscription"
  },
  "reagent": [
    {
      "item": "ars_nouveau:blank_parchment"
    }
  ],
  "pedestalItems": [
    {
      "item": "minecraft:water_bucket"
    },
    {
      "item": "ars_nouveau:source_gem"
    },
    {
      "tag": "forge:logs/archwood"
    }
  ],
  "sourceCost": 500
}
```

## `src/main/resources/data/ars_n_spells/recipes/apparatus/spellbook_binding.json`

```json
{
  "type": "forge:conditional",
  "recipes": [
    {
      "conditions": [
        {
          "type": "forge:mod_loaded",
          "modid": "irons_spellbooks"
        }
      ],
      "recipe": {
        "type": "ars_nouveau:enchanting_apparatus",
        "keepNbtOfReagent": false,
        "output": {
          "item": "ars_n_spells:spellbook_binding"
        },
        "reagent": [
          {
            "item": "ars_nouveau:novice_spell_book"
          }
        ],
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
            "tag": "forge:logs/archwood"
          }
        ],
        "sourceCost": 2500
      }
    }
  ]
}
```

## `src/main/resources/data/ars_n_spells/recipes/blank_scroll.json`

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
    "item": "ars_n_spells:blank_scroll"
  }
}
```

## `src/main/resources/data/ars_n_spells/recipes/spell_loom.json`

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
    "item": "ars_n_spells:spell_loom"
  }
}
```
