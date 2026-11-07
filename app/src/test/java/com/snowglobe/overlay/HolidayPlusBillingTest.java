package com.snowglobe.overlay;

import android.app.Activity;
import android.content.Context;
import android.os.Looper;

import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Uses generated test-only RSA receipts and a fake Play boundary; never performs a real charge. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class HolidayPlusBillingTest {
    private static KeyPair signingKey;
    private static String publicKey;
    private Context context;
    private FakeGateway gateway;
    private HolidayPlusBilling billing;
    private final List<HolidayPlusBilling.Snapshot> updates = new ArrayList<>();

    @BeforeClass public static void makeTestKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        signingKey = generator.generateKeyPair();
        publicKey = Base64.getEncoder().encodeToString(signingKey.getPublic().getEncoded());
    }

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        new HolidayPlusEntitlement(context, publicKey).revoke();
        context.getSharedPreferences("snowglobe", 0).edit().remove("plusRevision").commit();
        gateway = new FakeGateway();
        billing = new HolidayPlusBilling(context, publicKey, updates::add, gateway);
    }

    @After public void tearDown() {
        billing.close();
        new HolidayPlusEntitlement(context, publicKey).revoke();
    }

    @Test public void emptyOrMalformedKeyNeverConnectsChargesOrGrantsAccess() {
        billing.close();
        billing = new HolidayPlusBilling(context, "", updates::add, gateway);
        billing.start();
        billing.buy(null);
        assertFalse(billing.snapshot().verificationConfigured);
        assertFalse(billing.snapshot().canBuy);
        assertFalse(billing.snapshot().owned);
        assertFalse(HolidayPlusEntitlement.isVerificationConfigured("not-a-public-key"));
        assertEquals(0, gateway.connectCalls);
        assertEquals(0, gateway.launchCalls);
    }

    @Test public void pendingPaymentIsVisibleButNeitherAcknowledgedNorUnlocked() throws Exception {
        billing.onPurchasesUpdated(ok(), Collections.singletonList(receipt(false, true, "pending-token")));
        assertTrue(billing.snapshot().pending);
        assertFalse(billing.snapshot().owned);
        assertFalse(billing.snapshot().canBuy);
        assertEquals(0, gateway.acknowledgeCalls);
    }

    @Test public void paidReceiptUnlocksOnlyAfterSuccessfulAcknowledgementAndNotifiesRenderer() throws Exception {
        Purchase paid = receipt(false, false, "paid-token");
        billing.onPurchasesUpdated(ok(), Collections.singletonList(paid));
        assertFalse(billing.snapshot().owned);
        assertTrue(billing.snapshot().busy);
        assertEquals(1, gateway.acknowledgeCalls);
        gateway.acknowledgeCallback.onAcknowledgePurchaseResponse(ok());
        assertTrue(billing.snapshot().owned);
        assertFalse(billing.snapshot().busy);
        assertEquals(1L, context.getSharedPreferences("snowglobe", 0).getLong("plusRevision", 0L));
        assertTrue(new File(context.getNoBackupFilesDir(), "holiday_plus_receipt.json").isFile());
        // A different key invalidates the memory lookup, forcing the original key to verify disk.
        assertFalse(HolidayPlusEntitlement.isOwned(context, ""));
        assertTrue(HolidayPlusEntitlement.isOwned(context, publicKey));
    }

    @Test public void duplicatePurchaseCallbacksDoNotDuplicateAcknowledgement() throws Exception {
        Purchase paid = receipt(false, false, "same-token");
        billing.onPurchasesUpdated(ok(), Collections.singletonList(paid));
        billing.onPurchasesUpdated(ok(), Collections.singletonList(paid));
        assertEquals(1, gateway.acknowledgeCalls);
        gateway.acknowledgeCallback.onAcknowledgePurchaseResponse(ok());
        assertTrue(billing.snapshot().owned);
    }

    @Test public void acknowledgementFailureDoesNotGrantAndRetriesAreBoundedByLifecycle() throws Exception {
        billing.onPurchasesUpdated(ok(), Collections.singletonList(receipt(false, false, "retry-token")));
        gateway.acknowledgeCallback.onAcknowledgePurchaseResponse(result(BillingClient.BillingResponseCode.NETWORK_ERROR));
        assertFalse(billing.snapshot().owned);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertEquals(2, gateway.acknowledgeCalls);
        gateway.acknowledgeCallback.onAcknowledgePurchaseResponse(result(BillingClient.BillingResponseCode.NETWORK_ERROR));
        billing.close();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10));
        assertEquals(2, gateway.acknowledgeCalls);
        assertFalse(billing.snapshot().owned);
        assertTrue(gateway.closed);
    }

    @Test public void successfulRestoreRecoversSignedOwnershipWithoutChargingOrConsuming() throws Exception {
        connect();
        gateway.purchaseCallback.onQueryPurchasesResponse(ok(), Collections.singletonList(receipt(true, false, "restored-token")));
        assertTrue(billing.snapshot().owned);
        assertEquals(0, gateway.acknowledgeCalls);
        assertEquals(0, gateway.launchCalls);
    }

    @Test public void unavailablePlayPreservesVerifiedOfflineOwnership() throws Exception {
        new HolidayPlusEntitlement(context, publicKey).saveAcknowledged(receipt(true, false, "offline-token"));
        connect();
        gateway.purchaseCallback.onQueryPurchasesResponse(result(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE), Collections.emptyList());
        assertTrue(billing.snapshot().owned);
    }

    @Test public void successfulEmptyQueryRevokesReceiptAndNotifiesRenderer() throws Exception {
        new HolidayPlusEntitlement(context, publicKey).saveAcknowledged(receipt(true, false, "refunded-token"));
        long before = context.getSharedPreferences("snowglobe", 0).getLong("plusRevision", 0L);
        connect();
        gateway.purchaseCallback.onQueryPurchasesResponse(ok(), Collections.emptyList());
        assertFalse(billing.snapshot().owned);
        assertFalse(new File(context.getNoBackupFilesDir(), "holiday_plus_receipt.json").exists());
        assertEquals(before + 1L, context.getSharedPreferences("snowglobe", 0).getLong("plusRevision", 0L));
    }

    @Test public void oldOwnershipQueryCannotEraseNewPurchase() throws Exception {
        connect();
        PurchasesResponseListener oldQuery = gateway.purchaseCallback;
        billing.onPurchasesUpdated(ok(), Collections.singletonList(receipt(true, false, "fresh-token")));
        assertTrue(billing.snapshot().owned);
        oldQuery.onQueryPurchasesResponse(ok(), Collections.emptyList());
        assertTrue(billing.snapshot().owned);
        assertEquals(2, gateway.purchaseQueries);
        gateway.purchaseCallback.onQueryPurchasesResponse(ok(), Collections.singletonList(receipt(true, false, "fresh-token")));
        assertTrue(billing.snapshot().owned);
    }

    @Test public void acknowledgementArrivingAfterRevocationCannotResurrectPurchase() throws Exception {
        billing.onPurchasesUpdated(ok(), Collections.singletonList(receipt(false, false, "revoked-during-ack")));
        AcknowledgePurchaseResponseListener lateAcknowledgement = gateway.acknowledgeCallback;
        connect();
        gateway.purchaseCallback.onQueryPurchasesResponse(ok(), Collections.emptyList());
        lateAcknowledgement.onAcknowledgePurchaseResponse(ok());
        assertFalse(billing.snapshot().owned);
    }

    @Test public void anotherActivitysOlderQueryCannotEraseNewPurchase() throws Exception {
        connect();
        PurchasesResponseListener olderQuery = gateway.purchaseCallback;
        FakeGateway otherGateway = new FakeGateway();
        HolidayPlusBilling other = new HolidayPlusBilling(context, publicKey, state -> {}, otherGateway);
        try {
            other.onPurchasesUpdated(ok(), Collections.singletonList(receipt(true, false, "other-activity-purchase")));
            assertTrue(other.snapshot().owned);
            olderQuery.onQueryPurchasesResponse(ok(), Collections.emptyList());
            assertTrue(billing.snapshot().owned);
            assertEquals(2, gateway.purchaseQueries);
        } finally { other.close(); }
    }

    @Test public void anotherActivitysRevocationInvalidatesLateAcknowledgementEvenWhenNotYetOwned() throws Exception {
        billing.onPurchasesUpdated(ok(), Collections.singletonList(receipt(false, false, "cross-activity-ack")));
        AcknowledgePurchaseResponseListener lateAcknowledgement = gateway.acknowledgeCallback;
        FakeGateway otherGateway = new FakeGateway();
        HolidayPlusBilling other = new HolidayPlusBilling(context, publicKey, state -> {}, otherGateway);
        try {
            other.start();
            otherGateway.connectionListener.onBillingSetupFinished(ok());
            otherGateway.purchaseCallback.onQueryPurchasesResponse(ok(), Collections.emptyList());
            lateAcknowledgement.onAcknowledgePurchaseResponse(ok());
            assertFalse(billing.snapshot().owned);
            assertFalse(other.snapshot().owned);
            assertEquals(1, gateway.purchaseQueries);
        } finally { other.close(); }
    }

    @Test public void spoofedReceiptAndDifferentPackageOrProductNeverUnlock() throws Exception {
        Purchase good = receipt(true, false, "legitimate-token");
        Purchase altered = new Purchase(good.getOriginalJson().replace("legitimate-token", "forged-token"), good.getSignature());
        billing.onPurchasesUpdated(ok(), Collections.singletonList(altered));
        assertFalse(billing.snapshot().owned);
        billing.onPurchasesUpdated(ok(), Collections.singletonList(signedReceipt("other.package", PremiumPolicy.PRODUCT_ID, true, false, "other-package")));
        assertFalse(billing.snapshot().owned);
        billing.onPurchasesUpdated(ok(), Collections.singletonList(signedReceipt(context.getPackageName(), "some_other_item", true, false, "other-product")));
        assertFalse(billing.snapshot().owned);
        assertEquals(0, gateway.acknowledgeCalls);
    }

    @Test public void localPremiumPreferenceCannotGrantOwnership() {
        context.getSharedPreferences("snowglobe", 0).edit().putBoolean("premium", true)
                .putBoolean("entitlement", true).putLong("plusRevision", 999L).commit();
        assertFalse(HolidayPlusEntitlement.isOwned(context, publicKey));
        assertFalse(billing.snapshot().owned);
    }

    @Test public void signedCanceledOrMissingRawStateCannotBecomePurchasedThroughLibraryDefaults() throws Exception {
        Purchase valid = receipt(true, false, "state-token");
        for (int state : new int[]{1, 2, 3, 5, -1}) {
            JSONObject json = new JSONObject(valid.getOriginalJson());
            if (state == -1) json.remove("purchaseState");
            else json.put("purchaseState", state);
            billing.onPurchasesUpdated(ok(), Collections.singletonList(signPayload(json.toString())));
            assertFalse(billing.snapshot().owned);
        }
        assertEquals(0, gateway.acknowledgeCalls);
    }

    @Test public void checkoutUsesLocalizedPlayOfferAndRequeriesBeforeLaunch() throws Exception {
        connect();
        gateway.productCallback.onResult(ok(), Collections.singletonList(product("€2,99", "offer-eur", false)));
        assertFalse(billing.snapshot().canBuy); // Ownership must be checked before sale.
        gateway.purchaseCallback.onQueryPurchasesResponse(ok(), Collections.emptyList());
        assertTrue(billing.snapshot().canBuy);
        assertEquals("€2,99", billing.snapshot().formattedPrice);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        try {
            billing.buy(activity);
            assertEquals(0, gateway.launchCalls);
            assertEquals(2, gateway.productQueries);
            gateway.productCallback.onResult(ok(), Collections.singletonList(product("€2,49", "fresh-offer", false)));
            assertEquals(1, gateway.launchCalls);
            assertEquals("€2,49", gateway.launchedOffer.getFormattedPrice());
            assertEquals("fresh-offer", gateway.launchedOffer.getOfferToken());
        } finally { activity.finish(); }
    }

    @Test public void unavailableOrRentalOfferCannotBeSoldAsLifetimePlus() throws Exception {
        connect();
        gateway.purchaseCallback.onQueryPurchasesResponse(ok(), Collections.emptyList());
        gateway.productCallback.onResult(ok(), Collections.emptyList());
        assertFalse(billing.snapshot().canBuy);
        billing.refresh();
        gateway.purchaseCallback.onQueryPurchasesResponse(ok(), Collections.emptyList());
        gateway.productCallback.onResult(ok(), Collections.singletonList(product("$2.99", "rental", true)));
        assertFalse(billing.snapshot().canBuy);
    }

    @Test public void cancelledCheckoutNeverRevokesAnExistingVerifiedPurchase() throws Exception {
        new HolidayPlusEntitlement(context, publicKey).saveAcknowledged(receipt(true, false, "owned-token"));
        billing.onPurchasesUpdated(result(BillingClient.BillingResponseCode.USER_CANCELED), null);
        assertTrue(billing.snapshot().owned);
        assertEquals(0, gateway.acknowledgeCalls);
    }

    @Test public void closeDiscardsLateCallbacksAndMakesCheckoutUnavailable() throws Exception {
        connect();
        int previousUpdates = updates.size();
        billing.close();
        gateway.purchaseCallback.onQueryPurchasesResponse(ok(), Collections.singletonList(receipt(true, false, "late-token")));
        assertFalse(billing.snapshot().owned);
        assertFalse(billing.snapshot().canBuy);
        assertEquals(previousUpdates, updates.size());
    }

    private void connect() {
        billing.start();
        gateway.connectionListener.onBillingSetupFinished(ok());
    }

    private Purchase receipt(boolean acknowledged, boolean pending, String token) throws Exception {
        return signedReceipt(context.getPackageName(), PremiumPolicy.PRODUCT_ID, acknowledged, pending, token);
    }

    private static Purchase signedReceipt(String packageName, String product, boolean acknowledged,
            boolean pending, String token) throws Exception {
        JSONObject json = new JSONObject();
        json.put("orderId", "GPA.test-only");
        json.put("packageName", packageName);
        json.put("productId", product);
        json.put("purchaseTime", 1794000000000L);
        json.put("purchaseState", pending ? 4 : 0);
        json.put("purchaseToken", token);
        json.put("quantity", 1);
        json.put("acknowledged", acknowledged);
        return signPayload(json.toString());
    }

    private static Purchase signPayload(String payload) throws Exception {
        Signature signature = Signature.getInstance("SHA1withRSA");
        signature.initSign(signingKey.getPrivate());
        signature.update(payload.getBytes(StandardCharsets.UTF_8));
        return new Purchase(payload, Base64.getEncoder().encodeToString(signature.sign()));
    }

    private static ProductDetails product(String price, String token, boolean rental) throws Exception {
        JSONObject offer = new JSONObject();
        offer.put("formattedPrice", price);
        offer.put("priceAmountMicros", 2990000L);
        offer.put("priceCurrencyCode", "EUR");
        offer.put("offerIdToken", token);
        offer.put("purchaseOptionId", "lifetime");
        if (rental) offer.put("rentalDetails", new JSONObject().put("rentalPeriod", "P1D")
                .put("rentalExpirationPeriod", "P1D"));
        JSONObject json = new JSONObject();
        json.put("productId", PremiumPolicy.PRODUCT_ID);
        json.put("type", "inapp");
        json.put("title", "Holiday Plus");
        json.put("name", "Holiday Plus");
        json.put("description", "One-time holiday effects");
        json.put("oneTimePurchaseOfferDetailsList", new JSONArray().put(offer));
        Constructor<ProductDetails> constructor = ProductDetails.class.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(json.toString());
    }

    private static BillingResult ok() { return result(BillingClient.BillingResponseCode.OK); }
    private static BillingResult result(int code) { return BillingResult.newBuilder().setResponseCode(code).build(); }

    private static final class FakeGateway implements HolidayPlusBilling.Gateway {
        int connectCalls;
        int purchaseQueries;
        int productQueries;
        int launchCalls;
        int acknowledgeCalls;
        boolean closed;
        BillingClientStateListener connectionListener;
        PurchasesResponseListener purchaseCallback;
        HolidayPlusBilling.ProductCallback productCallback;
        AcknowledgePurchaseResponseListener acknowledgeCallback;
        ProductDetails.OneTimePurchaseOfferDetails launchedOffer;

        @Override public void connect(BillingClientStateListener listener) { connectCalls++; connectionListener = listener; }
        @Override public void queryProduct(HolidayPlusBilling.ProductCallback callback) { productQueries++; productCallback = callback; }
        @Override public void queryPurchases(PurchasesResponseListener listener) { purchaseQueries++; purchaseCallback = listener; }
        @Override public BillingResult launch(Activity activity, ProductDetails product, ProductDetails.OneTimePurchaseOfferDetails offer) {
            launchCalls++; launchedOffer = offer; return ok();
        }
        @Override public void acknowledge(String token, AcknowledgePurchaseResponseListener listener) {
            acknowledgeCalls++; acknowledgeCallback = listener;
        }
        @Override public void close() { closed = true; }
    }
}
