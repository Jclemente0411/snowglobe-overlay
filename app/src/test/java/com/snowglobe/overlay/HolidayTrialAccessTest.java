package com.snowglobe.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.util.AtomicFile;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Entirely synthetic campaigns and clocks; production access codes never enter test source. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class HolidayTrialAccessTest {
    private static final long DAY = 86_400_000L;
    private static final long START = Instant.parse("2026-11-07T00:00:00Z").toEpochMilli();
    private static final long END = Instant.parse("2027-03-01T00:00:00Z").toEpochMilli();
    private static final String SHARE = "TEST-ONLY-ACCESS";
    private static final String OWNER = "TEST-OWNER-ACCESS";
    private Context context;
    private File file;
    private FakeClock clock;
    private HolidayTrialAccess.Campaign[] campaigns;
    private HolidayTrialAccess.Controller controller;

    @Before public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication();
        clearStaticController();
        file = new File(context.getNoBackupFilesDir(), "holiday_trial_access.json");
        new AtomicFile(file).delete();
        context.getSharedPreferences("snowglobe", 0).edit().clear().commit();
        clock = new FakeClock(START + DAY, 10_000L);
        campaigns = new HolidayTrialAccess.Campaign[] {
            new HolidayTrialAccess.Campaign("test_share", hash("TESTONLYACCESS"), START, END, 7L * DAY),
            new HolidayTrialAccess.Campaign("test_owner", hash("TESTOWNERACCESS"), START, END, 90L * DAY)
        };
        controller = new HolidayTrialAccess.Controller(context, campaigns, clock);
    }

    @After public void cleanup() throws Exception {
        controller.close();
        clearStaticController();
        new AtomicFile(file).delete();
    }

    @Test public void noBillingKeyIsNeededAndTrialNeverFabricatesPurchaseOwnership() throws Exception {
        install(controller);
        assertFalse(HolidayFeatureAccess.hasAccess(context));
        assertTrue(HolidayTrialAccess.redeem(context, SHARE).success);
        assertTrue(HolidayFeatureAccess.hasAccess(context));
        assertFalse(HolidayPlusEntitlement.isOwned(context, ""));
        assertFalse(new File(context.getNoBackupFilesDir(), "holiday_plus_receipt.json").exists());
        new HolidayPlusEntitlement(context, "").revoke();
        assertTrue(HolidayFeatureAccess.hasAccess(context));
    }

    @Test public void caseAndAsciiSpacingDoNotChangeTheCode() {
        HolidayTrialAccess.Result result = controller.redeem("  test - only\t- access\n");
        assertTrue(result.success);
        assertTrue(result.snapshot.active);
        assertEquals(clock.wall + 7L * DAY, result.snapshot.expiresAtMillis);
        assertEquals(1L, revision());
    }

    @Test public void invalidOversizedUnicodeOrTrailingJunkCodesNeverUnlock() {
        for (String code : new String[] {null, "", "short", SHARE + "X", SHARE + "!",
                "ＴＥＳＴ-ONLY-ACCESS", " ".repeat(100) + SHARE, SHARE + "X".repeat(100)}) {
            assertFalse(controller.redeem(code).success);
            assertFalse(controller.snapshot().active);
        }
        assertFalse(file.exists());
        assertEquals(0L, revision());
    }

    @Test public void durationAndCampaignEndBothConstrainAccess() throws Exception {
        long activated = clock.wall;
        assertEquals(activated + 90L * DAY, controller.redeem(OWNER).snapshot.expiresAtMillis);
        controller.close();
        new AtomicFile(file).delete();
        clock.wall = END - 2L * DAY;
        controller = new HolidayTrialAccess.Controller(context, campaigns, clock);
        assertEquals(END, controller.redeem(SHARE).snapshot.expiresAtMillis);
        clock.advance(2L * DAY);
        assertFalse(controller.snapshot().active);
        assertFalse(controller.redeem(OWNER).success);
    }

    @Test public void repeatRedemptionCannotExtendEvenAfterRestartOrExpiry() {
        long expiry = controller.redeem(SHARE).snapshot.expiresAtMillis;
        clock.advance(6L * DAY);
        assertEquals(expiry, controller.redeem(SHARE).snapshot.expiresAtMillis);
        restart();
        assertEquals(expiry, controller.redeem(SHARE).snapshot.expiresAtMillis);
        clock.advance(DAY);
        HolidayTrialAccess.Result expired = controller.redeem(SHARE);
        assertFalse(expired.success);
        assertFalse(expired.snapshot.active);
        assertEquals(expiry, expired.snapshot.expiresAtMillis);
        restart();
        assertFalse(controller.redeem(SHARE).success);
    }

    @Test public void switchingCampaignsKeepsBothOriginalRedemptionDates() throws Exception {
        long shareStart = clock.wall;
        controller.redeem(SHARE);
        clock.advance(DAY);
        long ownerExpiry = controller.redeem(OWNER).snapshot.expiresAtMillis;
        clock.advance(8L * DAY);
        assertFalse(controller.redeem(SHARE).success);
        assertTrue(controller.snapshot().active);
        assertEquals(ownerExpiry, controller.snapshot().expiresAtMillis);
        JSONObject saved = readJson();
        assertEquals(2, saved.getJSONArray("redemptions").length());
        assertEquals(shareStart, saved.getJSONArray("redemptions").getJSONObject(0).getLong("startedAtMillis"));
        restart();
        assertEquals(ownerExpiry, controller.redeem(OWNER).snapshot.expiresAtMillis);
    }

    @Test public void activeTimerExpiresWithoutAnActivityOrSettingsAction() {
        campaigns = new HolidayTrialAccess.Campaign[] {
            new HolidayTrialAccess.Campaign("test_short", hash("TESTONLYACCESS"), START, END, 45_000L)
        };
        controller.close();
        controller = new HolidayTrialAccess.Controller(context, campaigns, clock);
        controller.redeem(SHARE);
        assertEquals(1L, revision());
        clock.advance(45_000L);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(45));
        // Read the transition signal first: no snapshot call should be needed to cause expiry.
        assertEquals(2L, revision());
        assertFalse(controller.snapshot().active);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(3));
        assertEquals(2L, revision());
    }

    @Test public void synchronousPreferenceListenerDoesNotRecurseOrDuplicateTransitions() {
        SharedPreferences preferences = context.getSharedPreferences("snowglobe", 0);
        int[] updates = {0};
        SharedPreferences.OnSharedPreferenceChangeListener listener = (p, key) -> {
            if ("plusRevision".equals(key)) {
                updates[0]++;
                controller.snapshot();
            }
        };
        preferences.registerOnSharedPreferenceChangeListener(listener);
        try {
            controller.redeem(SHARE);
            shadowOf(Looper.getMainLooper()).idle();
            for (int i = 0; i < 100; i++) controller.snapshot();
            assertEquals(1L, revision());
            assertEquals(1, updates[0]);
            clock.advance(7L * DAY);
            controller.snapshot();
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(2L, revision());
            assertEquals(2, updates[0]);
        } finally {
            preferences.unregisterOnSharedPreferenceChangeListener(listener);
        }
    }

    @Test public void privateRecordSurvivesProcessRestartAndContainsNoRawCode() throws Exception {
        long expiry = controller.redeem(SHARE).snapshot.expiresAtMillis;
        String saved = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertFalse(saved.contains(SHARE));
        assertFalse(saved.contains("TESTONLYACCESS"));
        assertTrue(file.getParentFile().equals(context.getNoBackupFilesDir()));
        restart();
        assertTrue(controller.snapshot().active);
        assertEquals(expiry, controller.snapshot().expiresAtMillis);
    }

    @Test public void ordinaryPreferencesCannotGrantAccess() throws Exception {
        context.getSharedPreferences("snowglobe", 0).edit()
                .putBoolean("plusOwned", true).putBoolean("trialActive", true)
                .putLong("trialExpiresAtMillis", Long.MAX_VALUE).putLong("plusRevision", 9999L).commit();
        install(controller);
        assertFalse(HolidayFeatureAccess.hasAccess(context));
    }

    @Test public void corruptionCannotBecomeAnUnlockOrResetAnExpiredGrant() throws Exception {
        controller.redeem(SHARE);
        String original = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        JSONObject wrongDigest = new JSONObject(original);
        wrongDigest.getJSONArray("redemptions").getJSONObject(0).put("digest", "0".repeat(64));
        JSONObject coercedTimestamp = new JSONObject(original);
        coercedTimestamp.getJSONArray("redemptions").getJSONObject(0).put("startedAtMillis", "0");
        JSONObject duplicate = new JSONObject(original);
        duplicate.getJSONArray("redemptions").put(duplicate.getJSONArray("redemptions").getJSONObject(0));
        JSONObject unknown = new JSONObject(original);
        unknown.getJSONArray("redemptions").getJSONObject(0).put("campaign", "unknown");
        JSONObject impossibleClock = new JSONObject(original).put("lastObservedAtMillis", 1);
        for (String invalid : new String[] {"{broken", "x".repeat(4097), "{\"active\":true}",
                wrongDigest.toString(), coercedTimestamp.toString(), duplicate.toString(),
                unknown.toString(), impossibleClock.toString()}) {
            controller.close();
            Files.write(file.toPath(), invalid.getBytes(StandardCharsets.UTF_8));
            controller = new HolidayTrialAccess.Controller(context, campaigns, clock);
            assertFalse(controller.snapshot().active);
            assertFalse(controller.redeem(SHARE).success);
        }
    }

    @Test public void settingsSnapshotsUseMemoryRatherThanRepeatedDiskReads() throws Exception {
        controller.redeem(SHARE);
        Files.write(file.toPath(), "corrupt after initial load".getBytes(StandardCharsets.UTF_8));
        assertTrue(controller.snapshot().active);
        restart();
        assertFalse(controller.snapshot().active);
    }

    @Test public void rollingClockBackDisablesTrialAndCannotExtendItsEnd() {
        long expiry = controller.redeem(SHARE).snapshot.expiresAtMillis;
        clock.advance(DAY);
        controller.snapshot();
        clock.wall -= DAY;
        assertFalse(controller.snapshot().active);
        assertFalse(controller.redeem(SHARE).success);
        clock.wall += DAY;
        assertTrue(controller.snapshot().active);
        assertEquals(expiry, controller.snapshot().expiresAtMillis);
    }

    @Test public void smallClockCorrectionDoesNotExtendTheTrial() {
        campaigns = new HolidayTrialAccess.Campaign[] {
            new HolidayTrialAccess.Campaign("test_short", hash("TESTONLYACCESS"), START, END, 120_000L)
        };
        controller.close();
        controller = new HolidayTrialAccess.Controller(context, campaigns, clock);
        controller.redeem(SHARE);
        clock.advance(60_000L);
        clock.wall -= 60_000L;
        assertTrue(controller.snapshot().active);
        clock.advance(60_000L);
        assertFalse(controller.snapshot().active);
    }

    @Test public void rebootAndPersistedClockHighWaterDoNotResetDuration() {
        long expiry = controller.redeem(SHARE).snapshot.expiresAtMillis;
        clock.advance(DAY);
        controller.snapshot();
        clock.wall += DAY;
        clock.elapsed = 500L;
        restart();
        assertTrue(controller.snapshot().active);
        assertEquals(expiry, controller.snapshot().expiresAtMillis);
        clock.wall = START + DAY;
        restart();
        assertFalse(controller.snapshot().active);
    }

    @Test public void storageFailureDoesNotReportAnUnpersistedGrant() throws Exception {
        controller.snapshot();
        File blockedWrite = new File(file.getPath() + ".new");
        assertTrue(blockedWrite.mkdir());
        try {
            HolidayTrialAccess.Result result = controller.redeem(SHARE);
            assertFalse(result.success);
            assertFalse(result.snapshot.active);
            assertEquals(0L, revision());
        } finally {
            assertTrue(blockedWrite.delete());
        }
    }

    @Test public void invalidCodeDoesNotDisturbAnExistingTrial() {
        long expiry = controller.redeem(SHARE).snapshot.expiresAtMillis;
        HolidayTrialAccess.Result invalid = controller.redeem("INVALID-CODE");
        assertFalse(invalid.success);
        assertTrue(invalid.snapshot.active);
        assertEquals(expiry, invalid.snapshot.expiresAtMillis);
        assertEquals(1L, revision());
    }

    @Test public void unopenedAndExpiredCampaignsCannotActivate() {
        clock.wall = START - DAY;
        assertFalse(controller.redeem(SHARE).success);
        clock.wall = END;
        assertFalse(controller.redeem(SHARE).success);
        assertFalse(file.exists());
    }

    @Test public void productionEntryRejectsFixtureCodeWithoutAnInstalledTestController() throws Exception {
        clearStaticController();
        assertFalse(HolidayTrialAccess.redeem(context, SHARE).success);
        assertFalse(HolidayTrialAccess.snapshot(context).active);
    }

    private void restart() {
        controller.close();
        controller = new HolidayTrialAccess.Controller(context, campaigns, clock);
    }

    private long revision() {
        return context.getSharedPreferences("snowglobe", 0).getLong("plusRevision", 0L);
    }

    private JSONObject readJson() throws Exception {
        return new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }

    private static String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (Exception unavailable) {
            throw new AssertionError(unavailable);
        }
    }

    private static void install(HolidayTrialAccess.Controller instance) throws Exception {
        Field field = HolidayTrialAccess.class.getDeclaredField("cachedController");
        field.setAccessible(true);
        field.set(null, instance);
    }

    private static void clearStaticController() throws Exception {
        Field field = HolidayTrialAccess.class.getDeclaredField("cachedController");
        field.setAccessible(true);
        HolidayTrialAccess.Controller existing = (HolidayTrialAccess.Controller) field.get(null);
        if (existing != null) existing.close();
        field.set(null, null);
    }

    private static final class FakeClock implements HolidayTrialAccess.Clock {
        long wall;
        long elapsed;
        FakeClock(long wall, long elapsed) { this.wall = wall; this.elapsed = elapsed; }
        @Override public long wallTimeMillis() { return wall; }
        @Override public long elapsedRealtimeMillis() { return elapsed; }
        void advance(long millis) { wall += millis; elapsed += millis; }
    }
}
