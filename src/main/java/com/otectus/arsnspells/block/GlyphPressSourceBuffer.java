package com.otectus.arsnspells.block;

import com.hollingsworth.arsnouveau.api.source.ISourceCap;

/**
 * The Glyph Press's Source store, exposed to Ars through its source capability so relays and
 * other Ars machinery can fill it. It only receives: Source that entered the Press is spent by a
 * conversion or dissipates when the block is destroyed, never drawn back out.
 *
 * <p>The capacity covers both the free Source and what a running job has reserved, so cancelling a
 * job always has room to return its reservation.
 */
final class GlyphPressSourceBuffer implements ISourceCap {
    private static final int MAX_RECEIVE = 1000;
    private final GlyphPressBlockEntity press;
    private int free;

    GlyphPressSourceBuffer(GlyphPressBlockEntity press) {
        this.press = press;
    }

    int free() {
        return free;
    }

    void setFree(int amount) {
        free = Math.max(0, amount);
    }

    private int room() {
        return Math.max(0, press.sourceCapacity() - free - press.reservedSource());
    }

    @Override
    public boolean canAcceptSource(int amount) {
        return room() >= Math.max(1, amount);
    }

    @Override
    public boolean canProvideSource(int amount) {
        return false;
    }

    @Override
    public int getMaxExtract() {
        return 0;
    }

    @Override
    public int getMaxReceive() {
        return MAX_RECEIVE;
    }

    @Override
    public int getSource() {
        return free;
    }

    @Override
    public int getSourceCapacity() {
        return Math.max(0, press.sourceCapacity() - press.reservedSource());
    }

    @Override
    public void setSource(int amount) {
        free = Math.max(0, Math.min(amount, press.sourceCapacity() - press.reservedSource()));
        press.sourceChanged();
    }

    @Override
    public void setMaxSource(int max) {
        // The capacity is server configuration, not something another machine sets.
    }

    @Override
    public int receiveSource(int amount, boolean simulate) {
        int accepted = Math.max(0, Math.min(Math.min(amount, MAX_RECEIVE), room()));
        if (!simulate && accepted > 0) {
            free += accepted;
            press.sourceChanged();
        }
        return accepted;
    }

    @Override
    public int extractSource(int amount, boolean simulate) {
        return 0;
    }
}
