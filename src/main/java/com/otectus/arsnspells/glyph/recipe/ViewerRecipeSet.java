package com.otectus.arsnspells.glyph.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The recipe objects one recipe-viewer runtime has been given, and what each refresh must change
 * (3.4.1 review VIEW-01). Pure, so its bounds are unit-tested without a viewer.
 *
 * <p>Two entries are the same recipe only when every material input is the same: the glyph, its
 * terms, the catalyst tag's resolved members and the live learning rule, all part of the caller's key.
 * A refresh hides what left, unhides what returns (the viewer kept it) and adds only what this runtime
 * has never been given, so unchanged refreshes change nothing and a recurring state never duplicates.
 * The viewer has no removal operation, so it retains every distinct recipe it was given; this set
 * mirrors exactly that and nothing more, and {@link #clear} forgets it when the runtime ends, so a new
 * runtime (another world or server) starts empty and is never told a recipe is already registered.
 */
public final class ViewerRecipeSet<R> {
    /** What one refresh does: hide, unhide and add, then what is shown. Empty lists mean no change. */
    public record Plan<R>(List<R> hide, List<R> unhide, List<R> add, List<R> shown) {
        public boolean changes() {
            return !hide.isEmpty() || !unhide.isEmpty() || !add.isEmpty();
        }
    }

    private final Function<R, String> key;
    private final Map<String, R> given = new HashMap<>();
    private List<String> shown = List.of();

    public ViewerRecipeSet(Function<R, String> key) {
        this.key = key;
    }

    /** The plan that makes the viewer show exactly {@code fresh}, reusing every object it already has. */
    public synchronized Plan<R> next(List<R> fresh) {
        Set<String> nextKeys = new LinkedHashSet<>();
        List<R> next = new ArrayList<>();
        List<R> add = new ArrayList<>(), unhide = new ArrayList<>();
        Set<String> previous = new HashSet<>(shown);
        for (R recipe : fresh) {
            String k = key.apply(recipe);
            if (!nextKeys.add(k)) continue;
            R known = given.get(k);
            if (known == null) {
                given.put(k, recipe);
                add.add(recipe);
                next.add(recipe);
            } else {
                next.add(known);
                if (!previous.contains(k)) unhide.add(known);
            }
        }
        List<R> hide = new ArrayList<>();
        for (String k : shown) if (!nextKeys.contains(k)) hide.add(given.get(k));
        shown = List.copyOf(nextKeys);
        return new Plan<>(hide, unhide, add, next);
    }

    /** The viewer runtime ended: nothing of it carries into the next one. */
    public synchronized void clear() {
        given.clear();
        shown = List.of();
    }

    /** How many distinct recipe objects this runtime holds (shown or hidden). */
    public synchronized int retained() {
        return given.size();
    }

    public synchronized int shownCount() {
        return shown.size();
    }
}
