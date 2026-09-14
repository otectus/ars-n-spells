package com.otectus.arsnspells.client.icons;

import net.minecraft.world.item.ItemStack;
import java.util.ArrayDeque;

/** Lexically scoped tooltip carrier. Never guesses an equipped book for a hovered item. */
public final class CarrierRenderContext {
    private static final ThreadLocal<ArrayDeque<ItemStack>> STACKS = ThreadLocal.withInitial(ArrayDeque::new);
    private CarrierRenderContext() {}
    public static void push(ItemStack stack) { STACKS.get().push(stack); }
    public static void pop() {
        var stack = STACKS.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) STACKS.remove();
    }
    public static ItemStack current() { return STACKS.get().peek(); }
}
