package com.otectus.arsnspells.contract;

import java.util.function.DoubleSupplier;

/** Observation of one API call. Unknown after-reads must never become invented refunds. */
public record ResourceMovement(double before, double requested, double after, double reported,
                               boolean attempted, RuntimeException error) {
    public boolean known() { return Double.isFinite(before) && Double.isFinite(after); }
    public double debited() { return known() ? Math.max(0, before - after) : 0; }
    public double credited() { return known() ? Math.max(0, after - before) : 0; }

    public static ResourceMovement observe(double amount, DoubleSupplier read, DoubleSupplier operation) {
        if (amount == 0) return new ResourceMovement(0, 0, 0, 0, false, null);
        double before;
        try { before = read.getAsDouble(); }
        catch (RuntimeException error) { return new ResourceMovement(Double.NaN, amount, Double.NaN, 0, false, error); }
        if (!Double.isFinite(before) || before < 0 || !Double.isFinite(amount) || amount < 0)
            return new ResourceMovement(before, amount, before, 0, false, new IllegalArgumentException("Invalid resource value"));
        double reported = 0, after = Double.NaN;
        RuntimeException error = null;
        try { reported = operation.getAsDouble(); }
        catch (RuntimeException failure) { error = failure; }
        try { after = read.getAsDouble(); }
        catch (RuntimeException failure) {
            if (error == null) error = failure; else error.addSuppressed(failure);
        }
        return new ResourceMovement(before, amount, after, reported, true, error);
    }
}
