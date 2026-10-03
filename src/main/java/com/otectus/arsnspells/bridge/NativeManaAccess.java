package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.contract.ResourceUnit;
import net.minecraft.world.entity.player.Player;
import java.util.*;
import java.util.function.Supplier;

/** Scoped native adapter access; an origin's raw pool never routes through the active mode. */
public final class NativeManaAccess {
    private static final ThreadLocal<Map<UUID, EnumMap<ResourceUnit, Integer>>> SCOPES =
        ThreadLocal.withInitial(HashMap::new);
    private NativeManaAccess() {}

    public static boolean active(Player player, ResourceUnit unit) {
        if (player == null) return false;
        var scopes = SCOPES.get().get(player.getUUID());
        return scopes != null && scopes.getOrDefault(unit, 0) > 0;
    }

    public static <T> T with(Player player, ResourceUnit unit, Supplier<T> action) {
        if (player == null) return action.get();
        var players = SCOPES.get();
        var scopes = players.computeIfAbsent(player.getUUID(), ignored -> new EnumMap<>(ResourceUnit.class));
        scopes.merge(unit, 1, Integer::sum);
        try { return action.get(); }
        finally {
            int remaining = scopes.get(unit) - 1;
            if (remaining == 0) scopes.remove(unit); else scopes.put(unit, remaining);
            if (scopes.isEmpty()) players.remove(player.getUUID());
            if (players.isEmpty()) SCOPES.remove();
        }
    }

    public static IManaBridge wrap(IManaBridge delegate, ResourceUnit unit) {
        if (delegate == null) return null;
        if (delegate instanceof Scoped scoped && scoped.unit == unit) return delegate;
        return new Scoped(delegate, unit);
    }
    private record Scoped(IManaBridge delegate, ResourceUnit unit) implements IManaBridge {
            public double transactionMana(Player p) { return with(p, unit, () -> delegate.transactionMana(p)); }
            public double transactionMax(Player p) { return with(p, unit, () -> delegate.transactionMax(p)); }
            public boolean transactionDebit(Player p, double amount) { return with(p, unit, () -> delegate.transactionDebit(p, amount)); }
            public void transactionCredit(Player p, double amount) { with(p, unit, () -> { delegate.transactionCredit(p, amount); return null; }); }
            public float getMana(Player p) { return with(p, unit, () -> delegate.getMana(p)); }
            public float getMaxMana(Player p) { return with(p, unit, () -> delegate.getMaxMana(p)); }
            public void setMana(Player p, float amount) { with(p, unit, () -> { delegate.setMana(p, amount); return null; }); }
            public void addMana(Player p, float amount) { with(p, unit, () -> { delegate.addMana(p, amount); return null; }); }
            public boolean consumeMana(Player p, float amount) { return with(p, unit, () -> delegate.consumeMana(p, amount)); }
            public String getBridgeType() { return delegate.getBridgeType(); }
    }
}
