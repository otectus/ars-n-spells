package com.otectus.arsnspells.client.screen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArrowProgressTest {
    @Test
    void silhouetteIsARightPointingArrowIncludingItsTip() {
        String[] shape = {
            "..............#.......", "..............##......", "..............###.....",
            "..............####....", "###################...", "####################..",
            "#####################.", "######################", "#####################.",
            "####################..", "###################...", "..............####....",
            "..............###.....", "..............##......", "..............#......."
        };
        assertEquals(ArrowProgress.HEIGHT, shape.length);
        for (int y = 0; y < shape.length; y++) {
            assertEquals(ArrowProgress.WIDTH, shape[y].length());
            for (int x = 0; x < ArrowProgress.WIDTH; x++)
                assertEquals(shape[y].charAt(x) == '#', x >= ArrowProgress.start(y) && x < ArrowProgress.end(y),
                    "silhouette at " + x + "," + y);
        }
    }

    @Test
    void progressRevealsColumnsFromShaftToTipWithoutPaintingOutsideTheArrow() {
        for (int progress = 0; progress <= 100; progress++) {
            int columns = ArrowProgress.columns(progress, 100);
            assertEquals(progress * 22 / 100, columns);
            for (int y = 0; y < ArrowProgress.HEIGHT; y++) {
                int end = Math.min(ArrowProgress.end(y), columns);
                for (int x = ArrowProgress.start(y); x < end; x++) {
                    assertTrue(x < columns && x < ArrowProgress.end(y));
                    assertTrue(x >= 14 || (y >= 4 && y <= 10), "no fill outside the shaft/head");
                }
            }
        }
        assertEquals(11, ArrowProgress.columns(50, 100));
        assertEquals(22, ArrowProgress.columns(100, 100));
    }

    @Test
    void clampsInvalidAndOversizedSynchronizedProgressWithoutOverflow() {
        assertEquals(0, ArrowProgress.columns(-1, 100));
        assertEquals(0, ArrowProgress.columns(30, 0));
        assertEquals(0, ArrowProgress.columns(30, -1));
        assertEquals(22, ArrowProgress.columns(101, 100));
        assertEquals(22, ArrowProgress.columns(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(21, ArrowProgress.columns(Integer.MAX_VALUE - 1, Integer.MAX_VALUE));
    }
}
