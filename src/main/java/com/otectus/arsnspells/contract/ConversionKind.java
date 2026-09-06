package com.otectus.arsnspells.contract;

/**
 * Which conversion policy prices a cross-system leg (audit V05).
 *
 * <p>Closes the finding that flat-rate conversion and equal-percentage conversion were treated
 * as interchangeable. They are not: a flat rate is an exchange rate between two pools, while an
 * equal-percentage charge asks each pool for the same share of the cast's own price. At the
 * default 1:1 rate the two coincide, which is exactly why the difference went unnoticed until a
 * pack author moved a rate off {@code 1.0} and found that one seam converted while another did
 * not. Selecting between them is a config decision now, never an implicit one.
 */
public enum ConversionKind {
    /**
     * The historical arithmetic. A leg denominated in the non-origin unit is multiplied by the
     * configured directional rate; the origin-unit leg is not. Reproduces the loaders' current
     * pricing so existing worlds see no change.
     */
    FLAT_LEGACY,
    /**
     * Each pool pays the same percentage of the cast's price in its own unit; the directional
     * rates are deliberately not consulted. Conversion is left to the resource layer, which is
     * the only layer that knows pool sizes.
     */
    EQUAL_PERCENT
}
