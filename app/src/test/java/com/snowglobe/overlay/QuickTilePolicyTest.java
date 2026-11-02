package com.snowglobe.overlay;

import org.junit.Test;
import static org.junit.Assert.*;

public class QuickTilePolicyTest {
    @Test public void stoppedAndPermittedStarts() {
        assertEquals(QuickTilePolicy.Action.START, QuickTilePolicy.action(true, false));
    }
    @Test public void activeStopsEvenIfPermissionWasRevoked() {
        assertEquals(QuickTilePolicy.Action.STOP, QuickTilePolicy.action(true, true));
        assertEquals(QuickTilePolicy.Action.STOP, QuickTilePolicy.action(false, true));
    }
    @Test public void missingPermissionOpensSetupInsteadOfPretendingToStart() {
        assertEquals(QuickTilePolicy.Action.OPEN_SETUP, QuickTilePolicy.action(false, false));
    }
    @Test public void staleRunningPreferenceDoesNotMeanServiceIsActive() {
        assertEquals("Off", QuickTilePolicy.status(true, false, "running"));
    }
    @Test public void actualActiveServiceWinsOverStaleState() {
        assertEquals("On", QuickTilePolicy.status(true, true, "stopped"));
    }
    @Test public void setupAndFailureStatesAreExplicit() {
        assertEquals("Finish setup", QuickTilePolicy.status(false, false, "error"));
        assertEquals("Open app to retry", QuickTilePolicy.status(true, false, "error"));
        assertEquals("Starting", QuickTilePolicy.status(true, false, "starting"));
        assertEquals("Off", QuickTilePolicy.status(true, false, null));
    }
}
