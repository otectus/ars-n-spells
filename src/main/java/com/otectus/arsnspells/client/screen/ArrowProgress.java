package com.otectus.arsnspells.client.screen;

/** Pixel spans of a right-pointing recipe arrow; progress reveals columns from left to right. */
final class ArrowProgress {
    static final int WIDTH = 22;
    static final int HEIGHT = 15;

    private ArrowProgress() {}

    static int start(int row) {
        return row >= 4 && row < 11 ? 0 : 14;
    }

    static int end(int row) {
        return 15 + 7 - Math.abs(7 - row);
    }

    static int columns(int progress, int duration) {
        if (duration <= 0) return 0;
        return (int) (Math.min((long) duration, Math.max(0L, progress)) * WIDTH / duration);
    }
}
