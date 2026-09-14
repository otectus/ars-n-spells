package com.otectus.arsnspells.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.otectus.arsnspells.registry.ModItemsRegistry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.fml.ModList;

/**
 * Adds Ars 'n' Spells blank scrolls to chest loot, but only when Iron's
 * Spellbooks is installed.
 *
 * <p>The Java gate is the real guard, not the datapack conditions. A blank
 * scroll is a Spell Loom substrate: its only use is to be woven into an Iron's
 * scroll carrier, so on an install without Iron's it is inert and seeding it
 * into stronghold and dungeon chests would be pure chest pollution. Gating in
 * {@link #doApply} rather than with a {@code forge:mod_loaded} condition keeps
 * the modifier itself always-registered, so a datapack reload cannot leave a
 * dangling reference, and {@link ModList} is the only Iron's touch point here -
 * no Iron's class is loaded.
 *
 * <p>The chance roll happens after that gate, and deliberately in Java rather
 * than as a {@code minecraft:random_chance} loot condition, so the RNG is only
 * consumed on installs where the drop can actually occur.
 *
 * <p>Both {@code chance} and {@code count} live in the modifier JSON under
 * {@code data/ars_n_spells/loot_modifiers/}, so pack authors can retune or
 * disable any tier with a datapack override. That is the intended tuning
 * mechanism; there is no config key.
 */
public class BlankScrollLootModifier extends LootModifier {

    public static final Codec<BlankScrollLootModifier> CODEC =
        RecordCodecBuilder.create(inst -> codecStart(inst)
            .and(Codec.FLOAT.fieldOf("chance").forGetter(m -> m.chance))
            .and(Codec.INT.optionalFieldOf("count", 1).forGetter(m -> m.count))
            .apply(inst, BlankScrollLootModifier::new));

    /**
     * Cached so the drop path does not walk the mod list on every chest roll.
     * A holder rather than a field on the modifier so the lookup is deferred to
     * the first roll: the class itself (and its codec) can be loaded before the
     * mod list exists.
     */
    private static final class IronsPresence {
        static final boolean LOADED = ModList.get().isLoaded("irons_spellbooks");
    }

    private final float chance;
    private final int count;

    public BlankScrollLootModifier(LootItemCondition[] conditionsIn, float chance, int count) {
        super(conditionsIn);
        this.chance = chance;
        this.count = count;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!IronsPresence.LOADED) {
            return generatedLoot;
        }
        if (context.getRandom().nextFloat() < chance) {
            generatedLoot.add(new ItemStack(ModItemsRegistry.blankScroll().get(), count));
        }
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
