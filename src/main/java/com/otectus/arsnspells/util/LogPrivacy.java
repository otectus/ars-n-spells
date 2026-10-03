package com.otectus.arsnspells.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Pseudonymous player tokens for diagnostics that players paste into public bug reports.
 *
 * <p>A token is stable for one player, so every line of one failing attempt still correlates,
 * but it is a truncated one-way digest rather than the account UUID or name.
 */
public final class LogPrivacy {
    private LogPrivacy() {}

    public static String token(UUID player) {
        if (player == null) return "p-none";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(("ans-diagnostic/" + player).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder("p-");
            for (int i = 0; i < 4; i++) out.append(String.format("%02x", digest[i]));
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            // Every Java platform ships SHA-256; never fall back to the raw identifier.
            return "p-unavailable";
        }
    }
}
