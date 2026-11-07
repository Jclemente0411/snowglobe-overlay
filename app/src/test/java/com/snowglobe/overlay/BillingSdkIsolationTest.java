package com.snowglobe.overlay;

import android.app.job.JobScheduler;
import android.content.Context;
import android.os.Looper;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.cct.CCTDestination;
import com.google.android.datatransport.runtime.TransportRuntime;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.util.Collections;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Exercises the real SDK with its telemetry backend unregistered, without a fake gateway or payment. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class BillingSdkIsolationTest {
    @Test public void actualSdkStartsQueriesAndClosesWithoutRegisteredTelemetry() throws Exception {
        for (String type : new String[]{
                "com.google.android.datatransport.Transport",
                "com.google.android.datatransport.runtime.TransportRuntime",
                "com.google.android.datatransport.cct.CCTDestination"}) {
            assertNotNull("Retain SDK linkage dependencies", Class.forName(type, false,
                    BillingClient.class.getClassLoader()));
        }

        Context context = RuntimeEnvironment.getApplication();
        BillingClient client = BillingClient.newBuilder(context)
                .setListener((result, purchases) -> {})
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build();
        AtomicReference<BillingResult> setup = new AtomicReference<>();
        AtomicReference<BillingResult> products = new AtomicReference<>();
        AtomicReference<BillingResult> purchases = new AtomicReference<>();
        try {
            client.startConnection(new BillingClientStateListener() {
                @Override public void onBillingSetupFinished(BillingResult result) { setup.set(result); }
                @Override public void onBillingServiceDisconnected() {}
            });
            awaitResult(setup, "SDK must report that this JVM fixture has no Play Store");
            assertNotEquals(BillingClient.BillingResponseCode.OK, setup.get().getResponseCode());

            QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PremiumPolicy.PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build();
            client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder()
                    .setProductList(Collections.singletonList(product)).build(),
                    (result, details) -> products.set(result));
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP).build(),
                    (result, items) -> purchases.set(result));
            awaitResult(products, "Product lookup must return a safe unavailable result");
            awaitResult(purchases, "Restore lookup must return a safe unavailable result");
            assertNotEquals(BillingClient.BillingResponseCode.OK, products.get().getResponseCode());
            assertNotEquals(BillingClient.BillingResponseCode.OK, purchases.get().getResponseCode());

            // Exercise the actual asynchronous telemetry scheduler. A missing registration
            // must reject the event before persistence or an upload job can be scheduled.
            AtomicReference<Exception> transportError = new AtomicReference<>();
            CountDownLatch transportFinished = new CountDownLatch(1);
            Transport<byte[]> transport = TransportRuntime.getInstance().newFactory(CCTDestination.INSTANCE)
                    .getTransport("SnowGlobeSdkIsolation", byte[].class, Encoding.of("proto"), bytes -> bytes);
            transport.schedule(Event.ofData(new byte[]{0}), error -> {
                transportError.set(error);
                transportFinished.countDown();
            });
            assertTrue("Telemetry must finish without a network operation",
                    transportFinished.await(10, TimeUnit.SECONDS));
            assertTrue(transportError.get() instanceof IllegalArgumentException);
            assertEquals("Transport backend 'cct' is not registered", transportError.get().getMessage());
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            assertNotNull(scheduler);
            assertFalse(scheduler.getAllPendingJobs().stream().anyMatch(job ->
                    job.getService().getClassName().startsWith("com.google.android.datatransport.")));
        } finally {
            client.endConnection();
        }
        assertEquals(BillingClient.ConnectionState.CLOSED, client.getConnectionState());
    }

    private static void awaitResult(AtomicReference<BillingResult> result, String message) throws Exception {
        // The real SDK uses worker threads before posting to the paused Android looper.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (result.get() == null && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));
            Thread.sleep(10);
        }
        assertNotNull(message, result.get());
    }
}
