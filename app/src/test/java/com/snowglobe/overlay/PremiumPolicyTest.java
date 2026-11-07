package com.snowglobe.overlay;

import org.junit.Test;
import static org.junit.Assert.*;

public class PremiumPolicyTest {
    @Test public void releaseHasNoBetaBypassOrUnconditionalCheckout() {
        assertFalse(PremiumPolicy.allFeaturesAvailable());
        assertFalse(PremiumPolicy.BETA_UNLOCKED);
        assertTrue(PremiumPolicy.PAYWALL_VISIBLE);
        assertFalse(PremiumPolicy.mayLaunchCheckout());
        assertEquals("holiday_plus_lifetime", PremiumPolicy.PRODUCT_ID);
        assertEquals(299, PremiumPolicy.PROPOSED_USD_PRICE_CENTS);
    }

    @Test public void pendingCanceledOrRevokedAreNeverOwned() {
        for (PremiumPolicy.PurchaseState state : new PremiumPolicy.PurchaseState[]{
                PremiumPolicy.PurchaseState.PENDING, PremiumPolicy.PurchaseState.CANCELED,
                PremiumPolicy.PurchaseState.REVOKED, PremiumPolicy.PurchaseState.NONE}) {
            assertFalse(PremiumPolicy.verifiedOwnership(state, PremiumPolicy.Verification.VERIFIED, true));
        }
    }

    @Test public void unverifiedOrUnacknowledgedCannotBecomeOwned() {
        assertFalse(PremiumPolicy.verifiedOwnership(PremiumPolicy.PurchaseState.PURCHASED, PremiumPolicy.Verification.UNKNOWN, true));
        assertFalse(PremiumPolicy.verifiedOwnership(PremiumPolicy.PurchaseState.PURCHASED, PremiumPolicy.Verification.REJECTED, true));
        assertFalse(PremiumPolicy.verifiedOwnership(PremiumPolicy.PurchaseState.PURCHASED, PremiumPolicy.Verification.VERIFIED, false));
        assertTrue(PremiumPolicy.verifiedOwnership(PremiumPolicy.PurchaseState.PURCHASED, PremiumPolicy.Verification.VERIFIED, true));
    }

    @Test public void checkoutRequiresEveryPrerequisiteAndCannotRepeatOwnedOrPendingPurchase() {
        assertTrue(PremiumPolicy.mayLaunchCheckout(true, true, true, false, false, false));
        assertFalse(PremiumPolicy.mayLaunchCheckout(false, true, true, false, false, false));
        assertFalse(PremiumPolicy.mayLaunchCheckout(true, false, true, false, false, false));
        assertFalse(PremiumPolicy.mayLaunchCheckout(true, true, false, false, false, false));
        assertFalse(PremiumPolicy.mayLaunchCheckout(true, true, true, true, false, false));
        assertFalse(PremiumPolicy.mayLaunchCheckout(true, true, true, false, true, false));
        assertFalse(PremiumPolicy.mayLaunchCheckout(true, true, true, false, false, true));
    }
}
