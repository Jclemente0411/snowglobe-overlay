package com.snowglobe.overlay;

import org.junit.Test;
import static org.junit.Assert.*;

public class UnlockResumePolicyTest {
    @Test public void retriesRemainBoundedAndOnlyWhenRequested(){
        for(int i=0;i<UnlockResumePolicy.MAX_RETRIES;i++)
            assertTrue(UnlockResumePolicy.shouldRetry(i,true,true,true,false,false));
        assertFalse(UnlockResumePolicy.shouldRetry(UnlockResumePolicy.MAX_RETRIES,true,true,true,false,false));
        assertFalse(UnlockResumePolicy.shouldRetry(-1,true,true,true,false,false));
    }
    @Test public void neverResumesWhenPausedUnpermittedAsleepOrEditing(){
        assertFalse(UnlockResumePolicy.shouldRetry(0,false,true,true,false,false));
        assertFalse(UnlockResumePolicy.shouldRetry(0,true,false,true,false,false));
        assertFalse(UnlockResumePolicy.shouldRetry(0,true,true,false,false,false));
        assertFalse(UnlockResumePolicy.shouldRetry(0,true,true,true,true,false));
        assertFalse(UnlockResumePolicy.shouldRetry(0,true,true,true,false,true));
    }
    @Test public void bootCanOnlyRestoreOptedInEffects(){
        assertTrue(UnlockResumePolicy.shouldStartAfterBoot(true,true,false));
        assertFalse(UnlockResumePolicy.shouldStartAfterBoot(false,true,false));
        assertFalse(UnlockResumePolicy.shouldStartAfterBoot(true,false,false));
        assertFalse(UnlockResumePolicy.shouldStartAfterBoot(true,true,true));
    }
}