package com.otectus.arsnspells.menu;

import com.otectus.arsnspells.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Structural guards for the Spell Loom GUI.
 *
 * <p>These read the sources rather than loaded classes, for the same reason the other
 * structural tests in this suite do: the numbers are {@code static final int}s, so a test that
 * referenced them would compare compile-time constants the compiler had already inlined into
 * the test itself, and could never fail. Reading the source also lets the geometry check work
 * whether the layout is written as bare literals or as named constants, which is what makes it
 * a real before/after guard rather than a restatement of the current values.
 *
 * <p>Motivated by two regressions. The port hardcoded the container at the vanilla 166px
 * height while keeping the taller layout's button rows, so the Inscribe button (y+74..92)
 * overlapped the inventory's top row (y+84..100); {@code AbstractContainerScreen} draws
 * widgets before slot items and the hover highlight, so items painted over the button and the
 * top nine pixels of nine inventory slots stopped taking clicks. Separately, eight tooltip and
 * error lang keys the screen references were dropped from {@code en_us.json}, which renders on
 * screen as the raw key.
 */
class SpellLoomLayoutTest {

    private static final Path MENU =
        TestPaths.of("src/main/java/com/otectus/arsnspells/menu/SpellLoomMenu.java");
    private static final Path SCREEN =
        TestPaths.of("src/main/java/com/otectus/arsnspells/client/screen/SpellLoomScreen.java");
    private static final Path LANG =
        TestPaths.of("src/main/resources/assets/ars_n_spells/lang/en_us.json");

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    /**
     * Resolve an integer token that is either a literal or the name of an {@code int} constant
     * declared in one of the given sources, including the one-level {@code NAME = OTHER - 82}
     * form the menu uses. Returns null when it cannot be resolved.
     */
    private static Integer resolve(String token, String... sources) {
        if (token.matches("-?\\d+")) {
            return Integer.parseInt(token);
        }
        for (String source : sources) {
            Matcher m = Pattern.compile(
                "static\\s+final\\s+int\\s+" + Pattern.quote(token)
                    + "\\s*=\\s*([A-Za-z_][A-Za-z0-9_]*|-?\\d+)\\s*(?:([-+])\\s*(\\d+)\\s*)?;")
                .matcher(source);
            if (!m.find()) {
                continue;
            }
            Integer base = resolve(m.group(1), sources);
            if (base == null) {
                return null;
            }
            if (m.group(2) == null) {
                return base;
            }
            int offset = Integer.parseInt(m.group(3));
            return "-".equals(m.group(2)) ? base - offset : base + offset;
        }
        return null;
    }

    /** The y of the player inventory's first row, from the menu's {@code addSlot} call. */
    private static int firstInventoryRowY(String menu, String screen) {
        Matcher m = Pattern.compile(
            "addSlot\\(new Slot\\(inv, col \\+ row \\* 9 \\+ 9,[^;]*?,\\s*"
                + "([A-Za-z_][A-Za-z0-9_]*|\\d+)\\s*\\+\\s*row\\s*\\*", Pattern.DOTALL)
            .matcher(menu);
        if (!m.find()) {
            fail("could not find the player-inventory addSlot call in SpellLoomMenu");
        }
        Integer y = resolve(m.group(1), menu, screen);
        if (y == null) {
            fail("could not resolve the inventory row y from '" + m.group(1) + "'");
        }
        return y;
    }

    /** The y offset and height of the Inscribe button, from its {@code bounds(...)} call. */
    private static int[] inscribeButtonBounds(String screen, String menu) {
        Matcher m = Pattern.compile(
            "spell_loom\\.export.*?\\.bounds\\(\\s*[^,]+,\\s*y\\s*\\+\\s*"
                + "([A-Za-z_][A-Za-z0-9_]*|\\d+)\\s*,\\s*[^,]+,\\s*"
                + "([A-Za-z_][A-Za-z0-9_]*|\\d+)\\s*\\)", Pattern.DOTALL)
            .matcher(screen);
        if (!m.find()) {
            fail("could not find the Inscribe button's bounds() call in SpellLoomScreen");
        }
        Integer y = resolve(m.group(1), screen, menu);
        Integer h = resolve(m.group(2), screen, menu);
        if (y == null || h == null) {
            fail("could not resolve the Inscribe button geometry from '"
                + m.group(1) + "' / '" + m.group(2) + "'");
        }
        return new int[] {y, h};
    }

    @Test
    void inscribeButtonDoesNotOverlapThePlayerInventory() {
        String menu = read(MENU);
        String screen = read(SCREEN);

        int[] button = inscribeButtonBounds(screen, menu);
        int buttonBottom = button[0] + button[1];
        int invTop = firstInventoryRowY(menu, screen);

        assertTrue(buttonBottom <= invTop,
            "the Inscribe button (y " + button[0] + ".." + buttonBottom + ") runs into the "
                + "player inventory (first row at y " + invTop + "). Widgets draw under slot "
                + "items and the hover highlight, so the button gets painted over and the "
                + "overlapped slot rows stop taking clicks.");
    }

    @Test
    void thePlayerInventoryFitsInsideTheContainerBackground() {
        String menu = read(MENU);
        String screen = read(SCREEN);
        int invTop = firstInventoryRowY(menu, screen);
        // Three rows of 18, a 4px gap, then the hotbar row: the vanilla stack below invTop.
        int contentBottom = invTop + 3 * 18 + 4 + 16;

        Matcher m = Pattern.compile("this\\.imageHeight\\s*=\\s*"
            + "([A-Za-z_][A-Za-z0-9_.]*|\\d+)\\s*;").matcher(screen);
        if (!m.find()) {
            fail("could not find the imageHeight assignment in SpellLoomScreen");
        }
        String token = m.group(1);
        Integer height = resolve(token.substring(token.lastIndexOf('.') + 1), menu, screen);
        if (height == null) {
            fail("could not resolve imageHeight from '" + token + "'");
        }

        assertTrue(contentBottom <= height,
            "the player inventory (ending at y " + contentBottom + ") overflows the "
                + height + "px container background");
    }

    @Test
    void everySpellLoomLangKeyTheGuiUsesIsTranslated() {
        String lang = read(LANG);
        Set<String> used = new LinkedHashSet<>();
        Pattern key = Pattern.compile("\"(ars_n_spells\\.spell_loom\\.[a-z_.]+)\"");
        for (Path source : List.of(SCREEN, MENU)) {
            Matcher m = key.matcher(read(source));
            while (m.find()) {
                used.add(m.group(1));
            }
        }
        assertTrue(used.size() >= 8,
            "expected the screen to reference several lang keys, saw " + used.size());

        List<String> missing = new ArrayList<>();
        for (String k : used) {
            if (!lang.contains("\"" + k + "\"")) {
                missing.add(k);
            }
        }
        if (!missing.isEmpty()) {
            fail("lang keys referenced by the Spell Loom GUI but absent from en_us.json "
                + "(these render as the raw key on screen): " + missing);
        }
    }
}
