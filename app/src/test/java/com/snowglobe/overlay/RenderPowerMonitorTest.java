package com.snowglobe.overlay;

import android.os.PowerManager;
import java.util.concurrent.Executor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowPowerManager;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,shadows=RenderPowerMonitorTest.UnavailableThermalService.class)
public class RenderPowerMonitorTest {
    @Implements(PowerManager.class)
    public static class UnavailableThermalService extends ShadowPowerManager {
        static boolean rejectRegistration;
        static int registrations,removals;
        @Implementation(minSdk=29)
        protected void addThermalStatusListener(Executor executor,PowerManager.OnThermalStatusChangedListener listener){
            registrations++;
            if(rejectRegistration)throw new IllegalStateException("Thermal listener unavailable");
        }
        @Implementation(minSdk=29)
        protected void removeThermalStatusListener(PowerManager.OnThermalStatusChangedListener listener){
            removals++;throw new IllegalStateException("Thermal service stopped");
        }
    }
    @Before public void reset(){UnavailableThermalService.rejectRegistration=true;UnavailableThermalService.registrations=0;UnavailableThermalService.removals=0;}
    @Test public void unavailableThermalEventsDoNotPreventStartAndUseConservativeDecoration(){
        RenderPowerMonitor monitor=new RenderPowerMonitor(RuntimeEnvironment.getApplication(),()->{});
        monitor.attach();monitor.attach();
        assertEquals(1,UnavailableThermalService.registrations);assertTrue(monitor.thermal>=2);
        monitor.read();assertTrue(monitor.thermal>=2);
        monitor.close();monitor.close();assertEquals(0,UnavailableThermalService.removals);
    }
    @Test public void disappearingThermalServiceDoesNotCrashRepeatedCleanup(){
        UnavailableThermalService.rejectRegistration=false;
        RenderPowerMonitor monitor=new RenderPowerMonitor(RuntimeEnvironment.getApplication(),()->{});
        monitor.attach();monitor.close();monitor.close();
        assertEquals(1,UnavailableThermalService.registrations);assertEquals(1,UnavailableThermalService.removals);
    }
}
