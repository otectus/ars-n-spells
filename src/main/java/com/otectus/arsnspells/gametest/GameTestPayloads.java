package com.otectus.arsnspells.gametest;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Proxy;

/**
 * Hands a server payload handler the context a real connection would: the sending player.
 *
 * <p>The request handlers read nothing else from the context, so the remaining methods answer
 * null or nothing. Driving {@code handleOnServer} this way keeps the admission guard, the carrier
 * fingerprint check and the target validation in the path a GameTest exercises, which calling
 * the cast method directly would skip.
 */
final class GameTestPayloads {

    private GameTestPayloads() {
    }

    static IPayloadContext from(ServerPlayer player) {
        return (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
            new Class<?>[]{IPayloadContext.class}, (proxy, method, args) -> switch (method.getName()) {
                case "player" -> player;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "GameTestPayloadContext[" + player.getGameProfile().getName() + "]";
                default -> null;
            });
    }
}
