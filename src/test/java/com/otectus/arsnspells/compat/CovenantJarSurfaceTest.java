package com.otectus.arsnspells.compat;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the Covenant of the Seven 2.2.6 bytecode surface that
 * {@link SanctifiedLegacyCompat} reflects on and that
 * {@code MixinResourceBarOverlay} injects into. The mixin is {@code @Pseudo}
 * with {@code require = 0}, so target drift is otherwise silent.
 *
 * <p>Read straight out of {@code libs/covenant_of_the_seven-2.2.6-hotfix.jar} with
 * ASM — never a classloader, since none of Covenant's hard dependencies (Blood
 * Magic, Enigmatic Legacy, Nature's Aura, Iron's, Curios) are on the test
 * classpath. {@code libs/} is git-ignored, so CI skips the whole class.
 */
class CovenantJarSurfaceTest {

    private static final String JAR_NAME = "covenant_of_the_seven-2.2.6-hotfix.jar";

    private static final String MOD_UTILS = "net/llenzzz/covenant_of_the_seven/util/ModUtils";
    private static final String CLIENT_RESOURCE_DATA =
        "net/llenzzz/covenant_of_the_seven/client/ClientResourceData";
    private static final String RESOURCE_BAR_OVERLAY =
        "net/llenzzz/covenant_of_the_seven/gui/ResourceBarOverlay";
    private static final String RESOURCE_SYNC_EVENTS =
        "net/llenzzz/covenant_of_the_seven/events/ResourceSyncEvents";

    /** The sampling radius SanctifiedLegacyCompat mirrors in COVENANT_AURA_RADIUS. */
    private static final int EXPECTED_AURA_RADIUS = 35;

    private static Path jar() {
        Path relative = Paths.get("libs", JAR_NAME);
        if (Files.exists(relative)) {
            return relative;
        }
        return Paths.get(System.getProperty("user.dir", "."), "libs", JAR_NAME);
    }

    private static ClassNode read(String internalName) throws IOException {
        Path jar = jar();
        Assumptions.assumeTrue(Files.exists(jar),
            "libs/" + JAR_NAME + " is git-ignored and absent; skipping Covenant surface check");
        try (JarFile file = new JarFile(jar.toFile())) {
            JarEntry entry = file.getJarEntry(internalName + ".class");
            assertNotNull(entry, internalName + " is missing from " + JAR_NAME);
            try (InputStream in = file.getInputStream(entry)) {
                ClassNode node = new ClassNode();
                new ClassReader(in).accept(node, ClassReader.SKIP_FRAMES);
                return node;
            }
        }
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        for (MethodNode m : owner.methods) {
            if (m.name.equals(name) && m.desc.equals(descriptor)) {
                return m;
            }
        }
        return null;
    }

    private static MethodNode methodByName(ClassNode owner, String name) {
        for (MethodNode m : owner.methods) {
            if (m.name.equals(name)) {
                return m;
            }
        }
        return null;
    }

    @Test
    void modUtils_exposesGetVirtuousFraction() throws IOException {
        ClassNode node = read(MOD_UTILS);
        MethodNode m = method(node, "getVirtuousFraction",
            "(Lnet/minecraft/world/entity/player/Player;)D");
        assertNotNull(m, "ModUtils.getVirtuousFraction(Player)D is reflected by "
            + "SanctifiedLegacyCompat.initCovenantAuraReflection");
        assertTrue(Modifier.isStatic(m.access), "getVirtuousFraction must stay static");
        assertTrue(Modifier.isPublic(m.access), "getVirtuousFraction must stay public");
    }

    @Test
    void modUtils_hasNoPerPlayerAuraApi() throws IOException {
        ClassNode node = read(MOD_UTILS);
        // The whole Nature's Aura read/drain path in SanctifiedLegacyCompat exists
        // because these do not. If Covenant ever adds one, revisit that decision.
        for (String name : new String[]{
            "consumeAura", "drainAura", "spendAura", "tryConsumeAura",
            "getCurrentAura", "getAura", "getStoredAura", "getVirtuousAmount",
            "getMaxAura", "getMaxStoredAura", "getMaxVirtuousAmount"}) {
            assertNull(methodByName(node, name),
                "ModUtils." + name + " appeared in Covenant 2.2.6 — the compat layer "
                    + "assumes there is no per-player aura API");
        }
    }

    @Test
    void clientResourceData_exposesGetCurrentAura() throws IOException {
        ClassNode node = read(CLIENT_RESOURCE_DATA);
        MethodNode m = method(node, "getCurrentAura", "()I");
        assertNotNull(m, "ClientResourceData.getCurrentAura()I is the HUD-only read path");
        assertTrue(Modifier.isStatic(m.access), "getCurrentAura must stay static");
        assertTrue(Modifier.isPublic(m.access), "getCurrentAura must stay public");
    }

    @Test
    void resourceBarOverlay_render_keepsOurInjectionPoints() throws IOException {
        ClassNode node = read(RESOURCE_BAR_OVERLAY);
        MethodNode m = method(node, "render",
            "(Lnet/minecraftforge/client/gui/overlay/ForgeGui;"
                + "Lnet/minecraft/client/gui/GuiGraphics;FII)V");
        assertNotNull(m, "ResourceBarOverlay.render(ForgeGui, GuiGraphics, float, int, int) "
            + "is MixinResourceBarOverlay's target");

        int magicConstants = 0;
        boolean valueOfInt = false;
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof LdcInsnNode ldc
                && ldc.cst instanceof Integer value
                && value == 2000000) {
                magicConstants++;
            }
            if (insn instanceof MethodInsnNode call
                && call.getOpcode() == Opcodes.INVOKESTATIC
                && call.owner.equals("java/lang/String")
                && call.name.equals("valueOf")
                && call.desc.equals("(I)Ljava/lang/String;")) {
                valueOfInt = true;
            }
        }
        assertEquals(2, magicConstants,
            "render must still push the 2000000 aura ceiling twice");
        assertTrue(valueOfInt, "render must still call String.valueOf(int) for the bar label");
    }

    @Test
    void resourceSyncEvents_samplesAmbientAuraAtOurRadius() throws IOException {
        ClassNode node = read(RESOURCE_SYNC_EVENTS);
        MethodNode m = method(node, "getPlayerAuraChunk",
            "(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V");
        assertNotNull(m, "ResourceSyncEvents.getPlayerAuraChunk(PlayerTickEvent) is where "
            + "Covenant's server-side aura number comes from");

        Integer radius = null;
        AbstractInsnNode previous = null;
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof MethodInsnNode call
                && call.name.equals("triangulateAuraInArea")
                && call.owner.equals("de/ellpeck/naturesaura/api/aura/chunk/IAuraChunk")) {
                if (previous instanceof IntInsnNode push) {
                    radius = push.operand;
                } else if (previous instanceof LdcInsnNode ldc && ldc.cst instanceof Integer value) {
                    radius = value;
                }
                break;
            }
            if (insn.getOpcode() >= 0) {
                previous = insn;
            }
        }
        assertNotNull(radius, "getPlayerAuraChunk must still call "
            + "IAuraChunk.triangulateAuraInArea with a constant radius");
        assertEquals(EXPECTED_AURA_RADIUS, radius.intValue(),
            "SanctifiedLegacyCompat.COVENANT_AURA_RADIUS must match Covenant's sampling radius");
    }
}
