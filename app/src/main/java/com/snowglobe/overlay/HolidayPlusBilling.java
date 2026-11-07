package com.snowglobe.overlay;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;

/**
 * One foreground BillingClient for the one-time Holiday Plus purchase.
 * UI callbacks run on the main thread. Call refresh on resume and close on destruction.
 * Only Google's current localized offer is used for checkout. No purchase is consumed.
 */
public final class HolidayPlusBilling implements PurchasesUpdatedListener, AutoCloseable {
    public interface Listener { void onChanged(Snapshot state); }

    public static final class Snapshot {
        public final boolean owned;
        public final boolean pending;
        public final boolean busy;
        public final boolean canBuy;
        public final boolean verificationConfigured;
        public final String formattedPrice;
        public final String message;

        private Snapshot(boolean owned, boolean pending, boolean busy, boolean canBuy,
                boolean verificationConfigured, String formattedPrice, String message) {
            this.owned = owned;
            this.pending = pending;
            this.busy = busy;
            this.canBuy = canBuy;
            this.verificationConfigured = verificationConfigured;
            this.formattedPrice = formattedPrice;
            this.message = message;
        }
    }

    interface ProductCallback { void onResult(BillingResult result, List<ProductDetails> products); }

    /** Small injection boundary so pending, restore, revocation and callback races can be tested. */
    interface Gateway {
        void connect(BillingClientStateListener listener);
        void queryProduct(ProductCallback callback);
        void queryPurchases(PurchasesResponseListener listener);
        BillingResult launch(Activity activity, ProductDetails product,
                ProductDetails.OneTimePurchaseOfferDetails offer);
        void acknowledge(String token, AcknowledgePurchaseResponseListener listener);
        void close();
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final HolidayPlusEntitlement entitlement;
    private final Gateway gateway;
    private Listener listener;
    private volatile boolean closed;
    private boolean connectedOnce;
    private boolean connecting;
    private boolean queryingPurchases;
    private boolean queryingProduct;
    private boolean launchingPurchase;
    private boolean ownershipChecked;
    private boolean restoreRequested;
    private boolean pending;
    private String acknowledgingToken;
    private long purchaseRevision;
    private long productRevision;
    private long acknowledgementRevision;
    private ProductDetails product;
    private ProductDetails.OneTimePurchaseOfferDetails offer;
    private String formattedPrice = "";
    private String message = "Checking Holiday Plus…";

    public HolidayPlusBilling(Context context, String playBillingPublicKey, Listener listener) {
        this(context, playBillingPublicKey, listener, null);
    }

    HolidayPlusBilling(Context context, String playBillingPublicKey, Listener listener, Gateway testGateway) {
        entitlement = new HolidayPlusEntitlement(context, playBillingPublicKey);
        this.listener = listener;
        gateway = testGateway != null ? testGateway
                : (entitlement.isConfigured() ? new PlayGateway(context.getApplicationContext(), this) : null);
        if (!entitlement.isConfigured()) message = unavailableMessage();
        else if (entitlement.isOwned()) message = "Holiday Plus is unlocked.";
    }

    public Snapshot snapshot() {
        boolean owned = entitlement.isOwned();
        boolean busy = !closed && (connecting || queryingPurchases || queryingProduct
                || launchingPurchase || acknowledgingToken != null);
        boolean available = product != null && offer != null && !formattedPrice.isEmpty();
        boolean canBuy = !closed && PremiumPolicy.mayLaunchCheckout(entitlement.isConfigured(),
                available, ownershipChecked, owned, pending, busy);
        return new Snapshot(owned, pending, busy, canBuy, entitlement.isConfigured(), formattedPrice, message);
    }

    public void start() { refresh(); }

    public void refresh() { onMain(() -> refreshInternal(false)); }

    public void restore() { onMain(() -> refreshInternal(true)); }

    public void buy(Activity activity) {
        WeakReference<Activity> owner = new WeakReference<>(activity);
        onMain(() -> {
            if (!snapshot().canBuy) {
                if (!entitlement.isConfigured()) message = unavailableMessage();
                else if (entitlement.isOwned()) message = "Holiday Plus is already unlocked.";
                else if (pending) message = pendingMessage();
                else message = "Check your Google Play connection and try again.";
                emit();
                return;
            }
            // Fetch immediately before launch instead of reusing a long-lived ProductDetails object.
            queryProduct(owner);
        });
    }

    private void refreshInternal(boolean explicitRestore) {
        if (!entitlement.isConfigured()) {
            message = unavailableMessage();
            emit();
            return;
        }
        restoreRequested |= explicitRestore;
        if (connectedOnce) {
            queryPurchases();
            if (!queryingProduct && !launchingPurchase) queryProduct(null);
            return;
        }
        if (connecting) return;
        connecting = true;
        message = explicitRestore ? "Restoring purchases from Google Play…" : "Connecting to Google Play…";
        emit();
        try {
            gateway.connect(new BillingClientStateListener() {
                @Override public void onBillingSetupFinished(BillingResult result) {
                    onMain(() -> {
                        connecting = false;
                        if (isOk(result)) {
                            connectedOnce = true;
                            queryPurchases();
                            queryProduct(null);
                        } else {
                            message = errorMessage(result);
                            emit();
                        }
                    });
                }

                @Override public void onBillingServiceDisconnected() {
                    onMain(() -> {
                        // Auto-reconnection performs the next API call; no manual reconnect loop.
                        product = null;
                        offer = null;
                        message = "Google Play is disconnected. Tap Restore purchases to reconnect.";
                        emit();
                    });
                }
            });
        } catch (RuntimeException ignored) {
            connecting = false;
            message = "Google Play is unavailable. Your free snow and lights still work.";
            emit();
        }
    }

    private void queryProduct(WeakReference<Activity> purchaseOwner) {
        final long revision = ++productRevision;
        queryingProduct = true;
        product = null;
        offer = null;
        if (purchaseOwner != null) message = "Checking your Google Play price…";
        emit();
        try {
            gateway.queryProduct((result, products) -> onMain(() -> {
                if (revision != productRevision) return;
                queryingProduct = false;
                if (isOk(result) && products != null) {
                    for (ProductDetails candidate : products) {
                        if (!PremiumPolicy.PRODUCT_ID.equals(candidate.getProductId())
                                || !BillingClient.ProductType.INAPP.equals(candidate.getProductType())) continue;
                        ProductDetails.OneTimePurchaseOfferDetails candidateOffer = selectLifetimeOffer(candidate);
                        if (candidateOffer != null) {
                            product = candidate;
                            offer = candidateOffer;
                            formattedPrice = candidateOffer.getFormattedPrice();
                            break;
                        }
                    }
                }
                if (product == null) {
                    formattedPrice = "";
                    if (!entitlement.isOwned() && !pending && acknowledgingToken == null)
                        message = isOk(result) ? unavailableMessage() : errorMessage(result);
                } else if (!pending && acknowledgingToken == null && !launchingPurchase) {
                    message = entitlement.isOwned() ? "Holiday Plus is unlocked."
                            : "One-time purchase. No subscription.";
                }
                if (purchaseOwner != null && product != null) launch(purchaseOwner);
                else emit();
            }));
        } catch (RuntimeException ignored) {
            queryingProduct = false;
            message = unavailableMessage();
            emit();
        }
    }

    private void launch(WeakReference<Activity> owner) {
        Activity activity = owner.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || !snapshot().canBuy) {
            emit();
            return;
        }
        launchingPurchase = true;
        message = "Complete your purchase in Google Play.";
        emit();
        try {
            BillingResult result = gateway.launch(activity, product, offer);
            if (!isOk(result)) handlePurchaseResult(result, null);
        } catch (RuntimeException ignored) {
            launchingPurchase = false;
            message = "Google Play could not open checkout. Please try again.";
            emit();
        }
    }

    private void queryPurchases() {
        if (queryingPurchases) return;
        queryingPurchases = true;
        final long revision = purchaseRevision;
        final long sharedObservation = entitlement.observationGeneration();
        emit();
        try {
            gateway.queryPurchases((result, purchases) -> onMain(() -> {
                queryingPurchases = false;
                if (revision != purchaseRevision || sharedObservation != entitlement.observationGeneration()) {
                    // An older empty query must never erase a newly completed purchase.
                    queryPurchases();
                    return;
                }
                launchingPurchase = false;
                if (!isOk(result)) {
                    // Network/service errors do not revoke a previously verified paid receipt.
                    message = errorMessage(result);
                    emit();
                    return;
                }
                ownershipChecked = true;
                entitlement.observePlayResult();
                processPurchases(purchases, true);
            }));
        } catch (RuntimeException ignored) {
            queryingPurchases = false;
            message = "Purchases could not be checked. Please try Restore purchases again.";
            emit();
        }
    }

    @Override public void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
        onMain(() -> handlePurchaseResult(result, purchases));
    }

    private void handlePurchaseResult(BillingResult result, List<Purchase> purchases) {
        launchingPurchase = false;
        if (isOk(result)) {
            ++purchaseRevision;
            if (!repeatsAwaitedPurchase(purchases)) entitlement.observePlayResult();
            processPurchases(purchases, false);
        } else if (result != null && result.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            restoreRequested = true;
            queryPurchases();
        } else {
            message = result != null && result.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED
                    ? "Purchase canceled. You can keep using free snow and lights." : errorMessage(result);
            emit();
        }
    }

    private boolean repeatsAwaitedPurchase(List<Purchase> purchases) {
        if (acknowledgingToken == null || purchases == null) return false;
        for (Purchase candidate : purchases) {
            if (candidate != null && acknowledgingToken.equals(candidate.getPurchaseToken())
                    && !candidate.isAcknowledged() && entitlement.isVerifiedPurchase(candidate)) return true;
        }
        return false;
    }

    private void processPurchases(List<Purchase> purchases, boolean completeQuery) {
        Purchase paid = null;
        boolean foundPending = false;
        boolean rejected = false;
        if (purchases != null) {
            for (Purchase candidate : purchases) {
                if (!entitlement.isTarget(candidate)) continue;
                if (!entitlement.isAuthentic(candidate)) {
                    rejected = true;
                } else if (entitlement.isVerifiedPurchase(candidate)) {
                    if (paid == null || candidate.isAcknowledged()) paid = candidate;
                } else if (candidate.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                    foundPending = true;
                } else {
                    rejected = true;
                }
            }
        }
        if (paid != null) {
            pending = false;
            finishPaidPurchase(paid);
            return;
        }
        if (completeQuery) {
            ++acknowledgementRevision;
            acknowledgingToken = null;
            entitlement.revoke();
            pending = foundPending;
        } else if (foundPending && !entitlement.isOwned()) {
            pending = true;
        }
        if (pending) message = pendingMessage();
        else if (rejected) message = "This purchase could not be verified. Try Restore purchases with your Google Play account.";
        else if (restoreRequested && completeQuery) message = "No Holiday Plus purchase was found for this Google Play account.";
        else if (entitlement.isOwned()) message = "Holiday Plus is unlocked.";
        else if (product != null) message = "One-time purchase. No subscription.";
        else message = unavailableMessage();
        if (completeQuery) restoreRequested = false;
        emit();
    }

    private void finishPaidPurchase(Purchase purchase) {
        if (purchase.isAcknowledged()) {
            ++acknowledgementRevision;
            acknowledgingToken = null;
            persistEntitlement(purchase);
            return;
        }
        if (purchase.getPurchaseToken().equals(acknowledgingToken)) return;
        final long revision = ++acknowledgementRevision;
        final long sharedObservation = entitlement.observationGeneration();
        acknowledgingToken = purchase.getPurchaseToken();
        message = "Confirming your purchase with Google Play…";
        emit();
        acknowledge(purchase, revision, sharedObservation, 0);
    }

    private void acknowledge(Purchase purchase, long revision, long sharedObservation, int attempt) {
        if (closed || revision != acknowledgementRevision) return;
        if (sharedObservation != entitlement.observationGeneration()) {
            acknowledgingToken = null;
            queryPurchases();
            return;
        }
        try {
            gateway.acknowledge(purchase.getPurchaseToken(), result -> onMain(() -> {
                if (revision != acknowledgementRevision) return;
                if (sharedObservation != entitlement.observationGeneration()) {
                    acknowledgingToken = null;
                    queryPurchases();
                    return;
                }
                if (isOk(result)) {
                    acknowledgingToken = null;
                    persistEntitlement(purchase);
                } else if (isTransient(result) && attempt < 2) {
                    main.postDelayed(() -> acknowledge(purchase, revision, sharedObservation, attempt + 1), 2000L << attempt);
                } else {
                    acknowledgingToken = null;
                    message = "Your purchase is still being confirmed. Tap Restore purchases to try again.";
                    emit();
                }
            }));
        } catch (RuntimeException ignored) {
            acknowledgingToken = null;
            message = "Your purchase is still being confirmed. Tap Restore purchases to try again.";
            emit();
        }
    }

    private void persistEntitlement(Purchase purchase) {
        boolean persisted = entitlement.saveAcknowledged(purchase);
        restoreRequested = false;
        if (!entitlement.isOwned()) {
            message = "Your purchase could not be saved. Please try Restore purchases.";
        } else {
            message = persisted ? "Holiday Plus is unlocked. Enjoy your extra holiday effects."
                    : "Holiday Plus is unlocked. Use Restore purchases if access is missing after reopening.";
        }
        emit();
    }

    private static ProductDetails.OneTimePurchaseOfferDetails selectLifetimeOffer(ProductDetails product) {
        List<ProductDetails.OneTimePurchaseOfferDetails> offers = product.getOneTimePurchaseOfferDetailsList();
        ProductDetails.OneTimePurchaseOfferDetails selected = null;
        if (offers == null) return null;
        for (ProductDetails.OneTimePurchaseOfferDetails candidate : offers) {
            // Never sell a rental or preorder as a lifetime entitlement.
            if (candidate.getRentalDetails() != null || candidate.getPreorderDetails() != null
                    || candidate.getFormattedPrice().isEmpty() || candidate.getOfferToken().isEmpty()) continue;
            if (selected == null || candidate.getPriceAmountMicros() < selected.getPriceAmountMicros()) selected = candidate;
        }
        return selected;
    }

    private void emit() { if (!closed && listener != null) listener.onChanged(snapshot()); }

    private void onMain(Runnable action) {
        if (closed) return;
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else main.post(() -> { if (!closed) action.run(); });
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        listener = null;
        main.removeCallbacksAndMessages(null);
        if (gateway != null) gateway.close();
    }

    private static boolean isOk(BillingResult result) {
        return result != null && result.getResponseCode() == BillingClient.BillingResponseCode.OK;
    }

    private static boolean isTransient(BillingResult result) {
        if (result == null) return true;
        int code = result.getResponseCode();
        return code == BillingClient.BillingResponseCode.SERVICE_DISCONNECTED
                || code == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE
                || code == BillingClient.BillingResponseCode.NETWORK_ERROR
                || code == BillingClient.BillingResponseCode.ERROR;
    }

    private static String unavailableMessage() {
        return "Holiday Plus is not available right now. Snow and lights remain free.";
    }

    private static String pendingMessage() {
        return "Payment is pending in Google Play. Holiday Plus unlocks after payment is completed.";
    }

    private static String errorMessage(BillingResult result) {
        if (result != null && result.getResponseCode() == BillingClient.BillingResponseCode.ITEM_UNAVAILABLE)
            return unavailableMessage();
        if (result != null && result.getResponseCode() == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE)
            return "Google Play purchases are unavailable on this device or account. Snow and lights remain free.";
        return "Google Play could not be reached. Check your connection and try again.";
    }

    private static final class PlayGateway implements Gateway {
        private final BillingClient client;

        PlayGateway(Context context, PurchasesUpdatedListener listener) {
            client = BillingClient.newBuilder(context)
                    .setListener(listener)
                    .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                    .enableAutoServiceReconnection()
                    .build();
        }

        @Override public void connect(BillingClientStateListener listener) { client.startConnection(listener); }

        @Override public void queryProduct(ProductCallback callback) {
            QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PremiumPolicy.PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build();
            client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder()
                    .setProductList(Collections.singletonList(product)).build(),
                    (result, details) -> callback.onResult(result, details.getProductDetailsList()));
        }

        @Override public void queryPurchases(PurchasesResponseListener listener) {
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP).build(), listener);
        }

        @Override public BillingResult launch(Activity activity, ProductDetails product,
                ProductDetails.OneTimePurchaseOfferDetails offer) {
            BillingFlowParams.ProductDetailsParams selected = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(product).setOfferToken(offer.getOfferToken()).build();
            return client.launchBillingFlow(activity, BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(Collections.singletonList(selected)).build());
        }

        @Override public void acknowledge(String token, AcknowledgePurchaseResponseListener listener) {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build(), listener);
        }

        @Override public void close() { client.endConnection(); }
    }
}
