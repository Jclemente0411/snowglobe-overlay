package com.snowglobe.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercises actual code entry and feature gates with a campaign that exists only in this test APK. */
@RunWith(AndroidJUnit4.class)
public final class HolidayTrialAccessUiTest {
    private static final long DAY=24L*60*60*1000;
    private static final long START=Instant.parse("2026-11-08T12:00:00Z").toEpochMilli();
    private static final String FIXTURE_CODE="UI-ONLY-FREE-PASS";
    @Rule public final DeviceEvidence evidence=new DeviceEvidence();
    private Context context;
    private SharedPreferences prefs;
    private UiDevice device;
    private File screenshots;
    private Field cachedController;
    private FixtureClock clock;
    private Map<String,?> previousPreferences;
    private final Map<File,byte[]> previousFiles=new HashMap<>();

    @Before public void setup() throws Exception {
        context=ApplicationProvider.getApplicationContext();
        prefs=context.getSharedPreferences("snowglobe",0);
        device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.setOrientationNatural();
        screenshots=new File(context.getExternalFilesDir(null),"screenshots");
        assertTrue(screenshots.mkdirs()||screenshots.isDirectory());
        cachedController=HolidayTrialAccess.class.getDeclaredField("cachedController");
        cachedController.setAccessible(true);
        runMain(()->{
            EffectsService.pauseNow(context);
            closeController();
        });
        previousPreferences=new HashMap<>(prefs.getAll());
        for(String suffix:new String[]{"",".bak",".new"}){
            File file=new File(context.getNoBackupFilesDir(),"holiday_trial_access.json"+suffix);
            previousFiles.put(file,file.exists()?Files.readAllBytes(file.toPath()):null);
            if(file.exists())assertTrue(file.delete());
        }
        prefs.edit().clear().putBoolean("effectsEnabled",false).putBoolean("reduceMotion",false)
            .putInt("qualityMode",0).putInt("_settingsSchema",14).putString("selectedScene","Custom").commit();
        clock=new FixtureClock();
        replaceController();
        assertFalse(HolidayFeatureAccess.hasAccess(context));
        assertFalse(HolidayPlusEntitlement.isOwned(context,BuildConfig.PLAY_BILLING_PUBLIC_KEY));
    }

    @After public void cleanup() throws Exception {
        if(context==null)return;
        runMain(()->{
            EffectsService.pauseNow(context);
            if(cachedController!=null)closeController();
        });
        for(Map.Entry<File,byte[]> item:previousFiles.entrySet()){
            if(item.getValue()==null){if(item.getKey().exists())assertTrue(item.getKey().delete());}
            else Files.write(item.getKey().toPath(),item.getValue());
        }
        if(previousPreferences!=null){
            SharedPreferences.Editor edit=prefs.edit().clear();
            for(Map.Entry<String,?> item:previousPreferences.entrySet()){
                Object value=item.getValue();String key=item.getKey();
                if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);
                else if(value instanceof Integer)edit.putInt(key,(Integer)value);
                else if(value instanceof Long)edit.putLong(key,(Long)value);
                else if(value instanceof Float)edit.putFloat(key,(Float)value);
                else if(value instanceof String)edit.putString(key,(String)value);
                else if(value instanceof Set){
                    Set<String> strings=new HashSet<>();
                    for(Object entry:(Set<?>)value)strings.add((String)entry);
                    edit.putStringSet(key,strings);
                }
            }
            edit.putBoolean("effectsEnabled",false).commit();
        }
        device.setOrientationNatural();device.unfreezeRotation();
    }

    @Test public void invalidCodeAndPreferenceFlagsCannotGrantTrialOrPurchase() throws Exception {
        prefs.edit().putBoolean("holidayPlusOwned",true).putBoolean("trialActive",true)
            .putLong("trialExpiresAt",Long.MAX_VALUE).putLong("plusRevision",99).commit();
        try(ActivityScenario<StudioActivity> studio=ActivityScenario.launch(StudioActivity.class)){
            openPlus();
            enterCode("NOT-A-VALID-CODE");
            reveal(By.text("That code is not valid. Check the code and try again."),
                "Invalid-code feedback",Direction.DOWN);
            dismissVisibleKeyboard();
            assertFalse(HolidayTrialAccess.snapshot(context).active);
            assertFalse(HolidayFeatureAccess.hasAccess(context));
            assertFalse(HolidayPlusEntitlement.isOwned(context,BuildConfig.PLAY_BILLING_PUBLIC_KEY));
            assertFalse("An access-code attempt must not launch checkout",
                device.hasObject(By.pkg("com.android.vending")));
            assertEquals(context.getPackageName(),device.getCurrentPackageName());
            clickButton("Continue with free snow",Direction.DOWN);
            tab("Decor");
            clickButton("Candy canes",Direction.DOWN);
            assertTrue("Premium controls must still request access after an invalid code",
                device.wait(Until.hasObject(By.text("SnowGlobe Holiday Plus")),10000));
            assertFalse(prefs.getBoolean("candy",false));
        }
    }

    @Test public void enteredCodeUnlocksFeaturesAndSurvivesActivityAndStoreRecreation() throws Exception {
        try(ActivityScenario<StudioActivity> studio=ActivityScenario.launch(StudioActivity.class)){
            openPlus();
            // Case and formatting are deliberately different from the test campaign's digest input.
            enterCode(" ui-only free-pass ");
            await("Entered code must activate the full trial",()->HolidayTrialAccess.snapshot(context).active);
            reveal(By.text("Holiday Plus trial active"),"Active trial status",Direction.UP);
            assertTrue(HolidayFeatureAccess.hasAccess(context));
            long expires=HolidayTrialAccess.snapshot(context).expiresAtMillis;
            assertTrue(expires>clock.wallTimeMillis());
            assertFalse("Complimentary access is never a fabricated purchase",
                HolidayPlusEntitlement.isOwned(context,BuildConfig.PLAY_BILLING_PUBLIC_KEY));
            assertTrue(device.takeScreenshot(new File(screenshots,"31-holiday-trial-active.png")));
            clickButton("Restore purchases",Direction.DOWN);
            assertEquals("Restore cannot erase or extend a complimentary trial",expires,
                HolidayTrialAccess.snapshot(context).expiresAtMillis);
            assertTrue(HolidayFeatureAccess.hasAccess(context));
            clickButton("Return to my winter",Direction.DOWN);
            assertTrue(device.wait(Until.gone(By.text("Restore purchases")),5000));

            // A real premium scene action exercises Activity.requirePlus and all seven decorations.
            reveal(By.text("Christmas magic"),"Christmas magic",Direction.UP);
            revealScene("North Pole Party").click();
            await("A trial must permit applying premium scenes",
                ()->"North Pole Party".equals(prefs.getString("selectedScene","")));
            SceneSettings unlocked=new SceneSettings(prefs,HolidayFeatureAccess.hasAccess(context));
            assertEquals(7,unlocked.decorations.length);
            assertTrue(unlocked.sparks);
            tab("Decor");
            clickButton("Candy canes",Direction.DOWN);
            await("The premium switch must change its stored value",()->!prefs.getBoolean("candy",true));
            clickButton("Candy canes",Direction.DOWN);
            await("The premium switch must remain usable",()->prefs.getBoolean("candy",false));

            tab("Snow");
            clickButton("Down",Direction.DOWN);
            UiObject2 direction=device.wait(Until.findObject(By.text("Down-right")),5000);
            assertNotNull("Direction menu must be unlocked by the code",direction);direction.click();
            await("Premium direction must reach the settings",()->prefs.getInt("particleDirection",0)==1);
            assertTrue("Premium rotation must be enabled",
                reveal(By.desc("Rotation"),"Rotation",Direction.DOWN).isEnabled());

            // Drop the process cache before recreation, so restored access must come from its file.
            replaceController();
            studio.recreate();
            assertTrue(HolidayFeatureAccess.hasAccess(context));
            assertEquals(expires,HolidayTrialAccess.snapshot(context).expiresAtMillis);
            assertEquals(1,new SceneSettings(prefs,HolidayFeatureAccess.hasAccess(context)).direction);
            assertFalse(HolidayPlusEntitlement.isOwned(context,BuildConfig.PLAY_BILLING_PUBLIC_KEY));

            tab("Home");
            clickButton("4K wallpaper collection",Direction.UP);
            assertTrue(device.wait(Until.hasObject(By.text("Winter wallpapers")),10000));
            UiObject2 save=reveal(By.desc("Save Candlelight Village 4K wallpaper"),
                "Save Candlelight Village",Direction.DOWN);
            assertTrue("Plus artwork must become saveable with trial access",save.isEnabled());
            save.click();
            await("A premium Save action must open Android's document picker",()->{
                String current=device.getCurrentPackageName();
                return current!=null&&current.contains("documentsui");
            });
            device.pressBack();
            assertTrue("Cancelling Save must return to SnowGlobe",
                device.wait(Until.hasObject(By.pkg(context.getPackageName())),10000));
            assertFalse("Trial activation must not start effects or any payment",EffectsService.isActive());
            assertFalse(HolidayPlusEntitlement.isOwned(context,BuildConfig.PLAY_BILLING_PUBLIC_KEY));
        }
    }

    @Test public void expiryInvalidatesVisiblePreviewAndRelocksControlsWithoutErasingScenes() throws Exception {
        runMain(()->assertTrue(HolidayTrialAccess.redeem(context,FIXTURE_CODE).success));
        assertTrue(SceneCatalog.apply(prefs,"North Pole Party"));
        try(ActivityScenario<StudioActivity> studio=ActivityScenario.launch(StudioActivity.class)){
            assertTrue(device.wait(Until.hasObject(By.text("Christmas Studio")),15000));
            AtomicReference<SnowView> preview=new AtomicReference<>();
            await("The actual Studio preview must be attached",()->{
                studio.onActivity(activity->preview.set(findSnowView(activity.getWindow().getDecorView())));
                return preview.get()!=null&&preview.get().isAttachedToWindow();
            });
            AtomicReference<SceneSettings> before=new AtomicReference<>();
            studio.onActivity(activity->before.set(renderedSettings(preview.get())));
            assertEquals(7,before.get().decorations.length);
            assertTrue(before.get().sparks);
            long revision=prefs.getLong("plusRevision",0);
            clock.advance(8*DAY);
            runMain(()->assertFalse(HolidayTrialAccess.snapshot(context).active));
            assertTrue("Expiry must notify all visual surfaces",prefs.getLong("plusRevision",0)>revision);
            await("The attached preview must clamp expired Plus content without user input",()->{
                AtomicReference<SceneSettings> current=new AtomicReference<>();
                studio.onActivity(activity->current.set(renderedSettings(preview.get())));
                return current.get().decorations.length==0&&!current.get().sparks;
            });
            assertTrue("Expiry preserves saved visual choices for a later paid upgrade",prefs.getBoolean("candy",false));
            assertEquals("North Pole Party",prefs.getString("selectedScene",""));
            assertFalse(HolidayFeatureAccess.hasAccess(context));
            assertFalse(HolidayPlusEntitlement.isOwned(context,BuildConfig.PLAY_BILLING_PUBLIC_KEY));
            tab("Decor");
            clickButton("Candy canes",Direction.DOWN);
            assertTrue("An expired trial must route Plus controls back to access options",
                device.wait(Until.hasObject(By.text("SnowGlobe Holiday Plus")),10000));
            assertFalse("An expired code cannot be reused to extend the same trial",
                HolidayTrialAccess.redeem(context,FIXTURE_CODE).success);
        }
    }

    private void openPlus() throws Exception {
        assertTrue(device.wait(Until.hasObject(By.text("Christmas Studio")),15000));
        clickButton("Explore Holiday Plus",Direction.DOWN);
        assertTrue(device.wait(Until.hasObject(By.text("SnowGlobe Holiday Plus")),10000));
    }

    private void enterCode(String code) throws Exception {
        UiObject2 field=reveal(By.desc("Access code"),"Access code",Direction.DOWN);
        field.click();
        // An explicit content description may sit on the surrounding semantics node.
        // Target the actual Android editable role rather than sending SET_TEXT to a container.
        UiObject2 editable=device.wait(Until.findObject(By.pkg(context.getPackageName())
            .clazz("android.widget.EditText")),5000);
        assertNotNull("The access-code field must expose an editable accessibility role",editable);
        editable.setText(code);
        clickButton("Activate code",Direction.DOWN);
    }

    private void dismissVisibleKeyboard(){
        // Invalid entry deliberately retains focus. Only dismiss an actually visible IME,
        // so Back cannot accidentally close the Plus sheet when a hardware keyboard is used.
        for(AccessibilityWindowInfo window:InstrumentationRegistry.getInstrumentation().getUiAutomation().getWindows()){
            if(window.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD){device.pressBack();break;}
        }
    }

    private void tab(String name){
        UiObject2 tab=device.wait(Until.findObject(By.text(name)),10000);
        assertNotNull("Missing navigation destination: "+name,tab);tab.click();
    }

    private UiObject2 reveal(BySelector target,String label,Direction direction) throws Exception {
        for(int attempt=0;attempt<16;attempt++){
            try{
                UiObject2 found=device.wait(Until.findObject(target),400);
                if(found!=null&&!found.getVisibleBounds().isEmpty())return found;
                UiObject2 list=null;int height=0;
                for(UiObject2 candidate:device.findObjects(By.pkg(context.getPackageName()).scrollable(true))){
                    Rect bounds=candidate.getVisibleBounds();
                    if(bounds.height()>height){list=candidate;height=bounds.height();}
                }
                if(list==null)break;
                list.setGestureMarginPercentage(.12f);list.scroll(direction,.40f,900);
            }catch(StaleObjectException changed){
                // Bounds can change while the IME/Compose transition replaces the node tree.
                // Reacquire the target and list within the existing bounded discovery loop.
            }
        }
        device.takeScreenshot(new File(screenshots,"FAILED-trial-"+label.replaceAll("[^A-Za-z0-9]+","-")+".png"));
        device.dumpWindowHierarchy(new File(screenshots,"FAILED-trial-hierarchy.xml"));
        fail("Trial control was not reachable: "+label);return null;
    }

    private void clickButton(String label,Direction direction) throws Exception {
        for(int attempt=0;attempt<3;attempt++){
            try{
                UiObject2 button=null;
                for(UiObject2 node=reveal(By.text(label),label,direction);node!=null;node=node.getParent()){
                    if(node.isScrollable())break;
                    if(node.isClickable()){button=node;break;}
                }
                assertNotNull("Missing click target for "+label,button);
                button.click();
                return;
            }catch(StaleObjectException changed){
                // Android 17 replaced Activate code's ancestry immediately after text entry.
                // Keep lookup, parent traversal and click together; retry only this stale handle.
                // Assertions and test scenarios are never retried.
                if(attempt==2){
                    try{
                        String name="FAILED-trial-click-"+label.replaceAll("[^A-Za-z0-9]+","-");
                        device.takeScreenshot(new File(screenshots,name+".png"));
                        device.dumpWindowHierarchy(new File(screenshots,name+".xml"));
                    }catch(Exception capture){changed.addSuppressed(capture);}
                    throw changed;
                }
                device.waitForIdle(1000);
            }
        }
    }

    private UiObject2 revealScene(String name) throws Exception {
        BySelector selector=By.desc("Apply "+name+" scene");
        for(int attempt=0;attempt<8;attempt++){
            UiObject2 found=device.wait(Until.findObject(selector),400);
            if(found!=null&&!found.getVisibleBounds().isEmpty())return found;
            UiObject2 row=null;
            for(UiObject2 candidate:device.findObjects(By.pkg(context.getPackageName()).scrollable(true))){
                Rect bounds=candidate.getVisibleBounds();
                if(bounds.width()>bounds.height()&&bounds.height()>80){row=candidate;break;}
            }
            if(row==null){
                // The section heading can be visible while its carousel is just below the fold.
                UiObject2 vertical=null;int height=0;
                for(UiObject2 candidate:device.findObjects(By.pkg(context.getPackageName()).scrollable(true))){
                    if(candidate.getVisibleBounds().height()>height){vertical=candidate;height=candidate.getVisibleBounds().height();}
                }
                if(vertical==null)break;
                try{vertical.setGestureMarginPercentage(.12f);vertical.scroll(Direction.DOWN,.25f,900);}
                catch(StaleObjectException changed){ /* Reacquire the partially visible carousel. */ }
                continue;
            }
            try{row.setGestureMarginPercentage(.12f);row.scroll(Direction.RIGHT,.65f,900);}
            catch(StaleObjectException changed){ /* Reacquire the scene carousel. */ }
        }
        device.dumpWindowHierarchy(new File(screenshots,"FAILED-trial-scenes.xml"));
        fail("The premium scene was not reachable: "+name);return null;
    }

    private void replaceController() throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        StringBuilder digest=new StringBuilder();
        for(byte b:hash.digest("UIONLYFREEPASS".getBytes(StandardCharsets.US_ASCII)))
            digest.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        HolidayTrialAccess.Campaign campaign=new HolidayTrialAccess.Campaign("ui_trial_fixture",
            digest.toString(),START-DAY,START+30*DAY,7*DAY);
        runMain(()->{
            closeController();
            cachedController.set(null,new HolidayTrialAccess.Controller(context,
                new HolidayTrialAccess.Campaign[]{campaign},clock));
        });
    }

    private void closeController() throws Exception {
        HolidayTrialAccess.Controller current=(HolidayTrialAccess.Controller)cachedController.get(null);
        if(current!=null)current.close();
        cachedController.set(null,null);
    }

    private static SnowView findSnowView(View view){
        if(view instanceof SnowView)return (SnowView)view;
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){
                SnowView found=findSnowView(group.getChildAt(i));if(found!=null)return found;
            }
        }
        return null;
    }

    private static SceneSettings renderedSettings(SnowView view){
        try{
            Field field=SnowView.class.getDeclaredField("renderer");field.setAccessible(true);
            return ((SceneRenderer)field.get(view)).settings();
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }

    private static void await(String message,BooleanSupplier condition){
        long deadline=SystemClock.uptimeMillis()+10000;
        while(SystemClock.uptimeMillis()<deadline){if(condition.getAsBoolean())return;SystemClock.sleep(100);}
        assertTrue(message,condition.getAsBoolean());
    }

    private interface MainAction {void run() throws Exception;}
    private static void runMain(MainAction action){
        AtomicReference<Throwable> failure=new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try{action.run();}catch(Throwable error){failure.set(error);}
        });
        if(failure.get()!=null)throw new AssertionError("Trial fixture main-thread operation failed",failure.get());
    }

    private static final class FixtureClock implements HolidayTrialAccess.Clock {
        private final long realStart=SystemClock.elapsedRealtime();
        private volatile long extra;
        @Override public long wallTimeMillis(){return START+SystemClock.elapsedRealtime()-realStart+extra;}
        @Override public long elapsedRealtimeMillis(){return SystemClock.elapsedRealtime()+extra;}
        void advance(long millis){extra+=millis;}
    }
}
