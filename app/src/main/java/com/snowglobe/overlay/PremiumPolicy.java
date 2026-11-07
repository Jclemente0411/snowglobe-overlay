package com.snowglobe.overlay;

/** Commercial rules for the single, non-consumable Holiday Plus upgrade. */
public final class PremiumPolicy {
    public static final String PRODUCT_ID = "holiday_plus_lifetime";
    // A Console configuration target, never a substitute for Google's localized checkout price.
    public static final int PROPOSED_USD_PRICE_CENTS = 299;
    public static final boolean PAYWALL_VISIBLE = true;
    public static final boolean BETA_UNLOCKED = false;
    public enum PurchaseState { NONE, PENDING, PURCHASED, CANCELED, REVOKED }
    public enum Verification { UNKNOWN, VERIFIED, REJECTED }
    private PremiumPolicy() {}
    public static boolean allFeaturesAvailable() { return false; }
    /** A build alone cannot enable charging: a key, live product and ownership query are required. */
    public static boolean mayLaunchCheckout() { return false; }
    public static boolean mayLaunchCheckout(boolean verifierConfigured, boolean productAvailable,
            boolean ownershipChecked, boolean owned, boolean pending, boolean busy) {
        return verifierConfigured && productAvailable && ownershipChecked && !owned && !pending && !busy;
    }
    public static boolean verifiedOwnership(PurchaseState state, Verification verification,
            boolean acknowledged) {
        return state == PurchaseState.PURCHASED && verification == Verification.VERIFIED && acknowledged;
    }
}
