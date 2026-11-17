package com.otectus.arsnspells.glyph.behavior;

import org.jetbrains.annotations.Nullable;

/**
 * A remote client's assembly of one server publication from its two packets (3.4.1 review SYNC-01):
 * the mapping view and the live facts of one accepted generation share a publication id, and the
 * client publishes them together, once both have arrived, so nothing ever runs on new mappings with
 * old facts (or the reverse) and the recipe viewer refreshes once per generation.
 *
 * <p>A part of an older publication than one already applied is stale and ignored; a part of a newer
 * one discards whatever was staged for an older one. {@link #reset} forgets everything (a new
 * connection), so nothing of a previous server is ever completed with parts of the next. Pure, so the
 * ordering rules are unit-tested.
 *
 * @param <M> the mapping view
 * @param <L> the live snapshot
 */
public final class PublicationStage<M, L> {
    /** A complete publication, ready to apply. */
    public record Complete<M, L>(long publication, M mapping, L live) {}

    private long applied;
    private long staged;
    @Nullable private M mapping;
    @Nullable private L live;

    /** Stage the mapping view of {@code publication}; the complete publication when this was the missing half. */
    @Nullable
    public synchronized Complete<M, L> mapping(long publication, M view) {
        if (!begin(publication)) return null;
        mapping = view;
        return complete();
    }

    /** Stage the live facts of {@code publication}; the complete publication when this was the missing half. */
    @Nullable
    public synchronized Complete<M, L> live(long publication, L facts) {
        if (!begin(publication)) return null;
        live = facts;
        return complete();
    }

    /** A part of {@code publication} was refused: nothing of it is applied. */
    public synchronized void refused(long publication) {
        if (publication == staged) {
            mapping = null;
            live = null;
        }
        if (publication > applied) applied = publication;
    }

    public synchronized void reset() {
        applied = 0;
        staged = 0;
        mapping = null;
        live = null;
    }

    public synchronized long applied() {
        return applied;
    }

    private boolean begin(long publication) {
        // Older than what was applied, or than the publication being assembled: stale, never mixed in.
        if (publication <= applied || publication < staged) return false;
        if (publication != staged) {
            staged = publication;
            mapping = null;
            live = null;
        }
        return true;
    }

    @Nullable
    private Complete<M, L> complete() {
        if (mapping == null || live == null) return null;
        Complete<M, L> done = new Complete<>(staged, mapping, live);
        applied = staged;
        mapping = null;
        live = null;
        return done;
    }
}
