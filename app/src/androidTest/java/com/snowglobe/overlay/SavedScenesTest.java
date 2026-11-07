package com.snowglobe.overlay;
import android.content.*;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
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
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class SavedScenesTest {
    @Test public void saveLoadAndUndoDoNotResumeOrChangePowerChoices(){
        Context context=ApplicationProvider.getApplicationContext();SharedPreferences prefs=context.getSharedPreferences("snowglobe",0);
        SavedScenesStore store=new SavedScenesStore(context);
        try{
            prefs.edit().putBoolean("effectsEnabled",false).putInt("qualityMode",0).putBoolean("reduceMotion",true).putInt("speed",22).commit();
            SceneSnapshot previous=store.capture();store.save(0,"My winter");
            prefs.edit().putInt("speed",88).commit();store.apply(store.read(0),store.name(0));
            assertEquals(22,prefs.getInt("speed",0));assertFalse(prefs.getBoolean("effectsEnabled",true));assertEquals(0,prefs.getInt("qualityMode",9));assertTrue(prefs.getBoolean("reduceMotion",false));
            store.apply(previous,"Before");assertEquals(22,prefs.getInt("speed",0));store.delete(0);assertFalse(store.exists(0));
        }finally{store.delete(0);prefs.edit().putBoolean("reduceMotion",false).putInt("qualityMode",1).putBoolean("effectsEnabled",false).commit();}
    }
    @Test public void holidayPlusUsesPlayBillingAndFailsClosedWithoutAVerificationKey() throws Exception {
        Context c=ApplicationProvider.getApplicationContext();
        assertEquals("holiday_plus_lifetime",PremiumPolicy.PRODUCT_ID);
        assertEquals(299,PremiumPolicy.PROPOSED_USD_PRICE_CENTS);
        assertTrue("The upgrade and Restore entry points are part of this release",PremiumPolicy.PAYWALL_VISIBLE);
        assertFalse("No beta entitlement bypass may remain",PremiumPolicy.allFeaturesAvailable());
        assertFalse(HolidayPlusEntitlement.isVerificationConfigured(""));
        assertFalse(HolidayPlusEntitlement.isVerificationConfigured("not-a-Play-RSA-key"));
        assertFalse(HolidayPlusEntitlement.isOwned(c,""));
        assertFalse("A product response alone cannot enable checkout without verification",
            PremiumPolicy.mayLaunchCheckout(false,true,true,false,false,false));
        String[] permissions=c.getPackageManager().getPackageInfo(c.getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions;
        assertNotNull(permissions);boolean billing=false;
        for(String p:permissions){
            if("com.android.vending.BILLING".equals(p))billing=true;
            assertNotEquals("android.permission.INTERNET",p);
            assertNotEquals("android.permission.ACCESS_NETWORK_STATE",p);
        }
        assertTrue("The paid upgrade must use Google's billing permission",billing);
    }

    @Test public void actualBillingSdkReportsServiceStateWithoutTelemetry() throws Exception {
        Context context=ApplicationProvider.getApplicationContext();
        for(String name:new String[]{"com.google.android.datatransport.runtime.TransportRuntime",
                "com.google.android.datatransport.cct.CCTDestination"}){
            assertNotNull("Keep the SDK's runtime types intact",
                Class.forName(name,false,context.getClassLoader()));
        }
        android.content.pm.PackageManager pm=context.getPackageManager();
        android.content.pm.PackageInfo info=pm.getPackageInfo(context.getPackageName(),
            android.content.pm.PackageManager.GET_PERMISSIONS|android.content.pm.PackageManager.GET_SERVICES
            |android.content.pm.PackageManager.GET_RECEIVERS|android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS);
        assertNotNull(info.requestedPermissions);
        for(String permission:info.requestedPermissions){
            assertNotEquals("android.permission.INTERNET",permission);
            assertNotEquals("android.permission.ACCESS_NETWORK_STATE",permission);
        }
        if(info.services!=null)for(android.content.pm.ServiceInfo service:info.services){
            assertFalse("No telemetry discovery or upload service may be registered",
                service.name.startsWith("com.google.android.datatransport."));
        }
        if(info.receivers!=null)for(android.content.pm.ActivityInfo receiver:info.receivers){
            assertFalse("No telemetry alarm receiver may be registered",
                receiver.name.startsWith("com.google.android.datatransport."));
        }
        assertEquals("9.1.0",pm.getApplicationInfo(context.getPackageName(),
            android.content.pm.PackageManager.GET_META_DATA).metaData
            .getString("com.google.android.play.billingclient.version"));
        for(String activity:new String[]{"ProxyBillingActivity","ProxyBillingActivityV2"}){
            assertFalse("Play checkout proxy activities must remain private",
                pm.getActivityInfo(new ComponentName(context,"com.android.billingclient.api."+activity),0).exported);
        }
        boolean hasPlayService=!pm.queryIntentServices(new Intent(
            "com.android.vending.billing.InAppBillingService.BIND").setPackage("com.android.vending"),0).isEmpty();
        AtomicReference<BillingClient> client=new AtomicReference<>();
        AtomicReference<BillingResult> setup=new AtomicReference<>();
        AtomicReference<BillingResult> product=new AtomicReference<>();
        AtomicReference<BillingResult> purchases=new AtomicReference<>();
        CountDownLatch setupFinished=new CountDownLatch(1),queriesFinished=new CountDownLatch(2);
        try{
            runSdkOnMain(()->client.set(BillingClient.newBuilder(context)
                .setListener((result,items)->{ /* Read-only smoke: never grant or log a receipt. */ })
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection().build()));
            assertNotNull("The actual SDK must construct on ART with telemetry unregistered",client.get());
            runSdkOnMain(()->client.get().startConnection(new BillingClientStateListener(){
                @Override public void onBillingSetupFinished(BillingResult result){setup.set(result);setupFinished.countDown();}
                @Override public void onBillingServiceDisconnected(){ }
            }));
            assertTrue("The actual SDK must report its service state",setupFinished.await(45,TimeUnit.SECONDS));
            assertNotNull(setup.get());
            runSdkOnMain(()->{
                QueryProductDetailsParams.Product item=QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PremiumPolicy.PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build();
                client.get().queryProductDetailsAsync(QueryProductDetailsParams.newBuilder()
                    .setProductList(Collections.singletonList(item)).build(),
                    (result,details)->{product.set(result);queriesFinished.countDown();});
                client.get().queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP).build(),
                    (result,items)->{purchases.set(result);queriesFinished.countDown();});
            });
            assertTrue("Both read-only SDK queries must return bounded callbacks",queriesFinished.await(45,TimeUnit.SECONDS));
            assertNotNull(product.get());assertNotNull(purchases.get());
            if(!hasPlayService){
                assertNotEquals("A missing Play service cannot report successful setup",BillingClient.BillingResponseCode.OK,setup.get().getResponseCode());
                assertNotEquals(BillingClient.BillingResponseCode.OK,product.get().getResponseCode());
                assertNotEquals(BillingClient.BillingResponseCode.OK,purchases.get().getResponseCode());
            }
            assertFalse("SDK queries cannot create a local entitlement",HolidayPlusEntitlement.isOwned(context,""));
            // Exercise the real transport boundary with a synthetic byte, never purchase data.
            // A missing backend must reject the event before persistence or job scheduling.
            TransportRuntime.initialize(context);
            Transport<byte[]> transport=TransportRuntime.getInstance().newFactory(CCTDestination.INSTANCE)
                .getTransport("SnowGlobePrivacySmoke",byte[].class,Encoding.of("proto"),bytes->bytes);
            AtomicReference<Exception> rejected=new AtomicReference<>();
            CountDownLatch transportFinished=new CountDownLatch(1);
            transport.schedule(Event.ofData(new byte[]{1}),error->{rejected.set(error);transportFinished.countDown();});
            assertTrue("The isolated backend must promptly reject telemetry",transportFinished.await(20,TimeUnit.SECONDS));
            assertTrue("The missing backend must reject the event before network work",
                rejected.get() instanceof IllegalArgumentException);
            assertTrue(rejected.get().getMessage().contains("not registered"));
            android.app.job.JobScheduler jobs=context.getSystemService(android.app.job.JobScheduler.class);
            assertNotNull(jobs);
            for(android.app.job.JobInfo job:jobs.getAllPendingJobs()){
                assertFalse("Telemetry must not leave an upload job",
                    job.getService().getClassName().startsWith("com.google.android.datatransport."));
            }
        }finally{
            if(client.get()!=null){
                runSdkOnMain(()->client.get().endConnection());
                assertFalse("The SDK must release its connection",client.get().isReady());
            }
        }
    }

    private static void runSdkOnMain(Runnable action){
        AtomicReference<Throwable> failure=new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try{action.run();}catch(Throwable error){failure.set(error);}
        });
        if(failure.get()!=null)throw new AssertionError("Actual Play Billing SDK call failed",failure.get());
    }
    @Test public void savedVisualSettingsCannotGrantPaidEffects(){
        Context c=ApplicationProvider.getApplicationContext();
        SharedPreferences prefs=c.getSharedPreferences("scene-entitlement-test",0);
        prefs.edit().clear().putBoolean("candy",true).putBoolean("gifts",true)
            .putBoolean("screenSparkles",true).putInt("particleDirection",7).commit();
        try{
            SceneSnapshot restored=SceneSnapshot.decode(SceneSnapshot.capture(prefs.getAll()).encode());
            assertEquals(Boolean.TRUE,restored.values().get("candy"));
            SceneSettings free=new SceneSettings(prefs,false);
            assertEquals(0,free.decorations.length);assertFalse(free.sparks);assertEquals(0,free.direction);
            // Only this renderer fixture supplies verified ownership; no preference toggles it.
            SceneSettings paid=new SceneSettings(prefs,true);
            assertEquals(2,paid.decorations.length);assertTrue(paid.sparks);assertEquals(7,paid.direction);
            assertFalse(HolidayPlusEntitlement.isOwned(c,""));
        }finally{prefs.edit().clear().commit();}
    }
}
