package com.snowglobe.overlay;
import org.junit.Test;
import static org.junit.Assert.*;
public class PremiumPolicyTest {
    @Test public void betaCannotChargeAndHasNoPaywall(){assertTrue(PremiumPolicy.allFeaturesAvailable());assertFalse(PremiumPolicy.PAYWALL_VISIBLE);assertFalse(PremiumPolicy.mayLaunchCheckout());assertEquals(299,PremiumPolicy.PROPOSED_USD_PRICE_CENTS);}
    @Test public void pendingCanceledOrRevokedAreNeverOwned(){for(PremiumPolicy.PurchaseState state:new PremiumPolicy.PurchaseState[]{PremiumPolicy.PurchaseState.PENDING,PremiumPolicy.PurchaseState.CANCELED,PremiumPolicy.PurchaseState.REVOKED,PremiumPolicy.PurchaseState.NONE})assertFalse(PremiumPolicy.verifiedOwnership(state,PremiumPolicy.Verification.VERIFIED,true));}
    @Test public void unverifiedOrUnacknowledgedCannotBecomeOwned(){assertFalse(PremiumPolicy.verifiedOwnership(PremiumPolicy.PurchaseState.PURCHASED,PremiumPolicy.Verification.UNKNOWN,true));assertFalse(PremiumPolicy.verifiedOwnership(PremiumPolicy.PurchaseState.PURCHASED,PremiumPolicy.Verification.REJECTED,true));assertFalse(PremiumPolicy.verifiedOwnership(PremiumPolicy.PurchaseState.PURCHASED,PremiumPolicy.Verification.VERIFIED,false));assertTrue(PremiumPolicy.verifiedOwnership(PremiumPolicy.PurchaseState.PURCHASED,PremiumPolicy.Verification.VERIFIED,true));}
}
