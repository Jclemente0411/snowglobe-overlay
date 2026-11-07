package com.snowglobe.overlay;

import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
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
import java.io.FileWriter;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real UI discovery and safe Restore. Never starts a payment or exports to user storage. */
@RunWith(AndroidJUnit4.class)
public final class HolidayPlusUiTest {
    @Rule public final DeviceEvidence evidence=new DeviceEvidence();
    private Context context;
    private UiDevice device;
    private File screenshots;

    @Before public void setup() throws Exception {
        context=ApplicationProvider.getApplicationContext();
        device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.setOrientationNatural();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->EffectsService.pauseNow(context));
        context.getSharedPreferences("snowglobe",0).edit()
            .putBoolean("effectsEnabled",false).putString("selectedScene","Custom").commit();
        screenshots=new File(context.getExternalFilesDir(null),"screenshots");
        assertTrue(screenshots.mkdirs()||screenshots.isDirectory());
    }

    @After public void cleanup() throws Exception {
        if(context==null)return;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->EffectsService.pauseNow(context));
        device.pressBack();device.setOrientationNatural();device.unfreezeRotation();
    }

    private UiObject2 longestList(){
        UiObject2 result=null;int height=0;
        for(UiObject2 candidate:device.findObjects(By.pkg(context.getPackageName()).scrollable(true))){
            Rect bounds=candidate.getVisibleBounds();
            if(bounds.height()>height){result=candidate;height=bounds.height();}
        }
        return result;
    }

    private UiObject2 reveal(BySelector target,String name) throws Exception {
        for(int attempt=0;attempt<=18;attempt++){
            UiObject2 found=device.wait(Until.findObject(target),750);
            if(found!=null&&!found.getVisibleBounds().isEmpty())return found;
            if(attempt==18)break;
            try{
                UiObject2 list=longestList();
                if(list==null)break;
                list.setGestureMarginPercentage(.12f);
                // Compose can omit the scroll event; target visibility is the completion signal.
                list.scroll(Direction.DOWN,.40f,900);
            }catch(StaleObjectException changed){
                // A fresh snapshot on the next bounded iteration handles recomposition.
            }
        }
        String stem="FAILED-product-discovery-"+name.replaceAll("[^A-Za-z0-9]+","-");
        device.takeScreenshot(new File(screenshots,stem+".png"));
        device.dumpWindowHierarchy(new File(screenshots,stem+".xml"));
        fail("Expected product control was not reachable: "+name);
        return null;
    }

    private UiObject2 text(String value) throws Exception {return reveal(By.text(value),value);}

    private UiObject2 button(String value) throws Exception {
        // Compose exposes the label as an enabled TextView even when its Button is disabled.
        // Check the control's role, never the label's inherited/default enabled state.
        for(UiObject2 node=text(value);node!=null;node=node.getParent()){
            if("android.widget.Button".equals(node.getClassName()))return node;
        }
        fail("Expected a Button semantics ancestor for: "+value);
        return null;
    }

    private UiObject2 enabledButton(String value) throws Exception {
        UiObject2 result=button(value);
        long deadline=SystemClock.uptimeMillis()+15000;
        while(!result.isEnabled()&&SystemClock.uptimeMillis()<deadline){
            SystemClock.sleep(100);
            result=button(value);
        }
        assertTrue(value+" must become enabled",result.isEnabled());
        return result;
    }

    private void capture(String name){assertTrue(device.takeScreenshot(new File(screenshots,name)));}

    private void captureLiveFailure(String name,Throwable failure){
        try{
            String stem="FAILED-live-"+name;
            device.takeScreenshot(new File(screenshots,stem+".png"));
            device.dumpWindowHierarchy(new File(screenshots,stem+".xml"));
            try(FileWriter out=new FileWriter(new File(screenshots,stem+".txt"))){
                out.write(failure.toString());
            }
        }catch(Exception capture){failure.addSuppressed(capture);}
    }

    @Test public void holidayPlusAndRestoreAreDiscoverableWithoutCharging() throws Exception {
        try(ActivityScenario<StudioActivity> studio=ActivityScenario.launch(StudioActivity.class)){
          try{
            assertTrue(device.wait(Until.hasObject(By.text("Christmas Studio")),15000));
            text("Explore Holiday Plus").click();
            assertTrue(device.wait(Until.hasObject(By.text("SnowGlobe Holiday Plus")),10000));
            if(!HolidayPlusEntitlement.isVerificationConfigured(BuildConfig.PLAY_BILLING_PUBLIC_KEY)){
                assertFalse(HolidayPlusEntitlement.isOwned(context,BuildConfig.PLAY_BILLING_PUBLIC_KEY));
                UiObject2 buy=button("Available through Google Play");
                assertFalse("An unconfigured build must not offer an active checkout",buy.isEnabled());
                // This is only a no-key build and the actual control has been verified disabled.
                // Exercise its touch target to prove the disabled state also prevents navigation.
                buy.click();
                assertFalse("A disabled upgrade control must not open Google Play checkout",
                    device.wait(Until.hasObject(By.pkg("com.android.vending")),1500));
                assertEquals("The disabled upgrade control must leave the user in SnowGlobe",
                    context.getPackageName(),device.getCurrentPackageName());
                assertFalse("The visible upgrade control must remain disabled after a tap",
                    button("Available through Google Play").isEnabled());
                assertFalse(HolidayPlusEntitlement.isOwned(context,BuildConfig.PLAY_BILLING_PUBLIC_KEY));
            }
            enabledButton("Restore purchases").click();
            enabledButton("Restore purchases");
            assertFalse("Restore cannot grant ownership without a valid verification key",
                HolidayPlusEntitlement.isOwned(context,""));
            assertFalse("Restore must not resume effects over a payment surface",EffectsService.isActive());
            capture("06-holiday-plus.png");
            text("Continue with free snow").click();
            assertTrue("The free exit must dismiss the upgrade sheet",
                device.wait(Until.gone(By.text("Restore purchases")),5000));
            UiObject2 snow=device.wait(Until.findObject(By.text("Snow")),5000);
            assertNotNull(snow);snow.click();
            assertTrue("Free snowfall remains available after visiting the upgrade",
                device.wait(Until.hasObject(By.text("Snowfall")),10000));
            assertFalse(EffectsService.isActive());
          }catch(Exception|AssertionError failure){
            // Capture the sheet before ActivityScenario closes it; the outer rule is too late.
            captureLiveFailure("holiday-plus",failure);
            throw failure;
          }
        }
    }

    @Test public void wallpaperCollectionShowsTwoFreeAndTwoPlusDesigns() throws Exception {
        try(ActivityScenario<StudioActivity> studio=ActivityScenario.launch(StudioActivity.class)){
            assertTrue(device.wait(Until.hasObject(By.text("Christmas Studio")),15000));
            text("4K wallpaper collection").click();
            assertTrue(device.wait(Until.hasObject(By.text("Winter wallpapers")),10000));
            String[] free={"Winter Lodge","Moonlit Pines"};
            String[] plus={"Candlelight Village","Aurora Night"};
            for(int index=0;index<free.length;index++){
                text(free[index]);
                UiObject2 save=reveal(By.desc("Save "+free[index]+" 4K wallpaper"),"Save "+free[index]);
                assertTrue("Free wallpaper must be saveable without a purchase",save.isEnabled());
                assertTrue(device.hasObject(By.text("Save 4K wallpaper")));
                if(index==0)capture("07-wallpaper-collection.png");
            }
            for(String name:plus){
                text(name);
                UiObject2 unlock=reveal(By.desc("Unlock "+name+" with Holiday Plus"),"Unlock "+name);
                assertTrue("Paid artwork must have a reachable upgrade action",unlock.isEnabled());
                assertTrue(device.hasObject(By.text("Unlock with Holiday Plus")));
            }
            assertFalse("Browsing artwork must not start effects",EffectsService.isActive());
        }
    }
}
