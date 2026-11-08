package com.snowglobe.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Complimentary offline access, deliberately separate from Google Play purchase ownership.
 * Campaigns are shareable, not single-use codes. Device time and private local storage provide
 * best-effort expiry only: clearing data/reinstalling resets local history, and there is no
 * server-wide redemption count or remote revocation. Never sell these complimentary codes.
 */
public final class HolidayTrialAccess {
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;
    private static final long CHECK_INTERVAL_MILLIS = 60_000L;
    private static final long CLOCK_TOLERANCE_MILLIS = 5L * 60L * 1000L;
    private static final int MAX_RECORD_BYTES = 4096;
    private static final String FILE_NAME = "holiday_trial_access.json";
    private static final Campaign[] CAMPAIGNS = {
        new Campaign("owner_0222", "aff8595ef543f9782633679bc3d748927501132646a23ccea5042c64c7fd093b",
                Instant.parse("2026-11-07T00:00:00Z").toEpochMilli(),
                Instant.parse("2027-03-01T00:00:00Z").toEpochMilli(), 90L * DAY_MILLIS),
        new Campaign("share_0222", "cd07e4d71058d00b008600088b4240b4c647a2705a8dd6c9e2d96c44ad6eee73",
                Instant.parse("2026-11-07T00:00:00Z").toEpochMilli(),
                Instant.parse("2027-03-01T00:00:00Z").toEpochMilli(), 7L * DAY_MILLIS)
    };
    private static Controller cachedController;

    private HolidayTrialAccess() {}

    /** Reads the process cache; disk is loaded once, not once per animation frame. */
    public static Snapshot snapshot(Context context) {
        return controller(context).snapshot();
    }

    public static Result redeem(Context context, String code) {
        return controller(context).redeem(code);
    }

    private static synchronized Controller controller(Context context) {
        Context app = context.getApplicationContext();
        String path = new File(app.getNoBackupFilesDir(), FILE_NAME).getAbsolutePath();
        if (cachedController == null || !cachedController.path.equals(path)) {
            if (cachedController != null) cachedController.close();
            // Only fixed release campaigns reach the production entry point. Test fixtures use
            // an instance constructor and reflection in the test process, not a runtime bypass.
            cachedController = new Controller(app, CAMPAIGNS, new Clock() {
                @Override public long wallTimeMillis() { return System.currentTimeMillis(); }
                @Override public long elapsedRealtimeMillis() { return SystemClock.elapsedRealtime(); }
            });
        }
        return cachedController;
    }

    public static final class Snapshot {
        public final boolean active;
        public final long expiresAtMillis;
        public final String message;

        private Snapshot(boolean active, long expiresAtMillis, String message) {
            this.active = active;
            this.expiresAtMillis = expiresAtMillis;
            this.message = message;
        }
    }

    public static final class Result {
        public final boolean success;
        public final String message;
        public final Snapshot snapshot;

        private Result(boolean success, String message, Snapshot snapshot) {
            this.success = success;
            this.message = message;
            this.snapshot = snapshot;
        }
    }

    interface Clock {
        long wallTimeMillis();
        long elapsedRealtimeMillis();
    }

    static final class Campaign {
        final String id;
        final String digest;
        final long startsAtMillis;
        final long hardEndsAtMillis;
        final long durationMillis;

        Campaign(String id, String digest, long startsAtMillis, long hardEndsAtMillis, long durationMillis) {
            if (id == null || !id.matches("[a-z0-9_]{1,64}") || digest == null
                    || !digest.matches("[a-f0-9]{64}") || startsAtMillis < 0
                    || hardEndsAtMillis <= startsAtMillis || durationMillis <= 0
                    || durationMillis > 366L * DAY_MILLIS) {
                throw new IllegalArgumentException("Invalid complimentary campaign");
            }
            this.id = id;
            this.digest = digest;
            this.startsAtMillis = startsAtMillis;
            this.hardEndsAtMillis = hardEndsAtMillis;
            this.durationMillis = durationMillis;
        }

        long expiry(long startedAt) {
            return Math.min(saturatedAdd(startedAt, durationMillis), hardEndsAtMillis);
        }
    }

    /** Package-private instance boundary allows isolated clocks/campaigns in tests. */
    static final class Controller {
        private final String path;
        private final AtomicFile file;
        private final Campaign[] campaigns;
        private final Clock clock;
        private final SharedPreferences preferences;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable timer;
        private State state = new State();
        private boolean loaded;
        private boolean corrupt;
        private boolean closed;
        private boolean clockStarted;
        private boolean clockBehind;
        private boolean timerPosted;
        private long lastElapsed;
        private long effectiveNow;
        private long lastPersistElapsed;
        private long scheduledAtElapsed;
        private Snapshot lastSnapshot;

        Controller(Context context, Campaign[] campaigns, Clock clock) {
            if (campaigns == null || campaigns.length == 0 || campaigns.length > 16 || clock == null) {
                throw new IllegalArgumentException("Invalid trial configuration");
            }
            this.campaigns = campaigns.clone();
            Map<String, Boolean> ids = new LinkedHashMap<>();
            Map<String, Boolean> digests = new LinkedHashMap<>();
            for (Campaign campaign : this.campaigns) {
                if (campaign == null || ids.put(campaign.id, true) != null
                        || digests.put(campaign.digest, true) != null) {
                    throw new IllegalArgumentException("Duplicate trial configuration");
                }
            }
            this.clock = clock;
            Context app = context.getApplicationContext();
            File destination = new File(app.getNoBackupFilesDir(), FILE_NAME);
            path = destination.getAbsolutePath();
            file = new AtomicFile(destination);
            preferences = app.getSharedPreferences("snowglobe", Context.MODE_PRIVATE);
            timer = () -> {
                synchronized (Controller.this) {
                    timerPosted = false;
                    if (!closed) refresh();
                }
            };
        }

        synchronized Snapshot snapshot() {
            return refresh();
        }

        synchronized Result redeem(String code) {
            Snapshot before = refresh();
            if (closed) return new Result(false, "Free access is unavailable. Reopen the app and try again.", before);
            String normalized = normalize(code);
            String digest = normalized == null ? null : sha256(normalized);
            Campaign campaign = findByDigest(digest);
            if (campaign == null) {
                return new Result(false, "That code is not valid. Check the code and try again.", before);
            }
            if (corrupt) {
                return new Result(false, "Saved free access could not be verified. Contact support for help.", before);
            }
            if (clockBehind || effectiveNow < campaign.startsAtMillis) {
                return new Result(false, "Check your device date and time, then try again.", before);
            }
            if (effectiveNow >= campaign.hardEndsAtMillis) {
                return new Result(false, "This free access code has expired.", before);
            }
            Long firstRedemption = state.redemptions.get(campaign.id);
            if (firstRedemption != null) {
                boolean stillActive = effectiveNow < campaign.expiry(firstRedemption);
                return new Result(stillActive,
                        stillActive ? "This code is already active. Its original end date stays the same."
                                : "This code's free access has ended. Entering it again does not extend it.", before);
            }
            State candidate = state.copy();
            candidate.redemptions.put(campaign.id, effectiveNow);
            candidate.lastObservedAtMillis = effectiveNow;
            if (!write(candidate)) {
                return new Result(false, "Could not save free access. Free some storage and try again.", before);
            }
            state = candidate;
            lastPersistElapsed = lastElapsed;
            Snapshot activated = refresh();
            return new Result(activated.active, activated.active
                    ? "Free access is active. All Holiday Plus features are available until the shown end date."
                    : activated.message, activated);
        }

        synchronized void close() {
            closed = true;
            handler.removeCallbacks(timer);
            timerPosted = false;
        }

        private Snapshot refresh() {
            if (closed) return new Snapshot(false, 0L, "Free access is unavailable.");
            if (!loaded) {
                read();
                loaded = true;
            }
            long elapsed = Math.max(0L, clock.elapsedRealtimeMillis());
            long wall = clock.wallTimeMillis();
            long observed = Math.max(0L, wall);
            if (clockStarted && elapsed >= lastElapsed) {
                observed = Math.max(observed, saturatedAdd(effectiveNow, elapsed - lastElapsed));
            }
            effectiveNow = Math.max(observed, state.lastObservedAtMillis);
            clockBehind = wall < 0 || effectiveNow - Math.max(0L, wall) > CLOCK_TOLERANCE_MILLIS;
            lastElapsed = elapsed;
            clockStarted = true;

            long expiresAt = 0L;
            for (Map.Entry<String, Long> entry : state.redemptions.entrySet()) {
                Campaign campaign = findById(entry.getKey());
                if (campaign != null) expiresAt = Math.max(expiresAt, campaign.expiry(entry.getValue()));
            }
            boolean active = !corrupt && !clockBehind && effectiveNow < expiresAt;
            String message = corrupt ? "Saved free access could not be verified."
                    : clockBehind ? "Check your device date and time to use free access."
                    : active ? "Complimentary Holiday Plus access is active. No charge and no automatic renewal."
                    : expiresAt > 0 ? "Your complimentary access has ended. Your saved settings are kept."
                    : "Have a free access code? Enter it to try all Holiday Plus features.";
            Snapshot current = new Snapshot(active, expiresAt, message);
            boolean changed = lastSnapshot != null && lastSnapshot.active != active;
            // Set before notifying preferences: listeners may synchronously ask for a snapshot.
            lastSnapshot = current;
            if (!state.redemptions.isEmpty() && effectiveNow > state.lastObservedAtMillis) {
                state.lastObservedAtMillis = effectiveNow;
                if (changed || elapsed < lastPersistElapsed
                        || elapsed - lastPersistElapsed >= CHECK_INTERVAL_MILLIS) {
                    if (write(state)) lastPersistElapsed = elapsed;
                }
            }
            schedule(active, expiresAt, elapsed);
            if (changed) notifyAccessChanged();
            return current;
        }

        private void schedule(boolean active, long expiresAt, long elapsed) {
            if (!active) {
                if (timerPosted) handler.removeCallbacks(timer);
                timerPosted = false;
                return;
            }
            long delay = Math.max(1L, Math.min(CHECK_INTERVAL_MILLIS, expiresAt - effectiveNow));
            long deadline = saturatedAdd(elapsed, delay);
            if (timerPosted && scheduledAtElapsed <= deadline) return;
            if (timerPosted) handler.removeCallbacks(timer);
            scheduledAtElapsed = deadline;
            timerPosted = handler.postDelayed(timer, delay);
        }

        private void notifyAccessChanged() {
            Object savedRevision = preferences.getAll().get("plusRevision");
            long revision = savedRevision instanceof Long ? (Long) savedRevision : 0L;
            // Only an invalidation signal; neither this preference nor saved scenes grant access.
            preferences.edit().putLong("plusRevision", revision == Long.MAX_VALUE ? 0L : revision + 1L).apply();
        }

        private Campaign findById(String id) {
            for (Campaign campaign : campaigns) if (campaign.id.equals(id)) return campaign;
            return null;
        }

        private Campaign findByDigest(String digest) {
            if (digest == null) return null;
            byte[] candidate = digest.getBytes(StandardCharsets.US_ASCII);
            for (Campaign campaign : campaigns) {
                if (MessageDigest.isEqual(candidate, campaign.digest.getBytes(StandardCharsets.US_ASCII))) return campaign;
            }
            return null;
        }

        private void read() {
            try (FileInputStream input = file.openRead();
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > MAX_RECORD_BYTES) throw new IllegalArgumentException("Oversized trial record");
                    bytes.write(buffer, 0, count);
                }
                JSONObject envelope = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                if (envelope.length() != 3 || strictLong(envelope, "version") != 1L) throw new IllegalArgumentException("Trial record version");
                State restored = new State();
                restored.lastObservedAtMillis = strictLong(envelope, "lastObservedAtMillis");
                JSONArray entries = envelope.getJSONArray("redemptions");
                if (entries.length() == 0 || entries.length() > campaigns.length) throw new IllegalArgumentException("Trial record count");
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject entry = entries.getJSONObject(i);
                    if (entry.length() != 3 || !(entry.get("campaign") instanceof String)
                            || !(entry.get("digest") instanceof String)) throw new IllegalArgumentException("Trial record fields");
                    Campaign campaign = findById(entry.getString("campaign"));
                    long started = strictLong(entry, "startedAtMillis");
                    if (campaign == null || !campaign.digest.equals(entry.getString("digest"))
                            || started < campaign.startsAtMillis || started >= campaign.hardEndsAtMillis
                            || restored.lastObservedAtMillis < started
                            || restored.redemptions.put(campaign.id, started) != null) {
                        throw new IllegalArgumentException("Invalid trial record");
                    }
                }
                state = restored;
            } catch (FileNotFoundException missing) {
                // A genuinely absent record is the normal first-install state. An unreadable
                // existing file must not silently reset a previously redeemed campaign.
                corrupt = file.getBaseFile().exists();
            } catch (Exception invalid) {
                corrupt = true;
            }
        }

        private boolean write(State value) {
            FileOutputStream output = null;
            try {
                JSONArray entries = new JSONArray();
                for (Map.Entry<String, Long> item : value.redemptions.entrySet()) {
                    Campaign campaign = findById(item.getKey());
                    if (campaign == null) return false;
                    entries.put(new JSONObject().put("campaign", campaign.id).put("digest", campaign.digest)
                            .put("startedAtMillis", item.getValue()));
                }
                byte[] bytes = new JSONObject().put("version", 1).put("redemptions", entries)
                        .put("lastObservedAtMillis", value.lastObservedAtMillis).toString().getBytes(StandardCharsets.UTF_8);
                if (bytes.length > MAX_RECORD_BYTES) return false;
                output = file.startWrite();
                output.write(bytes);
                file.finishWrite(output);
                return true;
            } catch (Exception failed) {
                if (output != null) file.failWrite(output);
                return false;
            }
        }
    }

    private static final class State {
        final Map<String, Long> redemptions = new LinkedHashMap<>();
        long lastObservedAtMillis;

        State copy() {
            State copy = new State();
            copy.redemptions.putAll(redemptions);
            copy.lastObservedAtMillis = lastObservedAtMillis;
            return copy;
        }
    }

    private static long strictLong(JSONObject object, String key) throws Exception {
        Object value = object.get(key);
        if (!(value instanceof Integer) && !(value instanceof Long)) throw new IllegalArgumentException("Expected integer");
        long result = ((Number) value).longValue();
        if (result < 0L) throw new IllegalArgumentException("Negative trial timestamp");
        return result;
    }

    private static String normalize(String input) {
        if (input == null || input.length() > 96) return null;
        StringBuilder normalized = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '-' || c == ' ' || c == '\t' || c == '\n' || c == '\r') continue;
            if (c >= 'a' && c <= 'z') c = (char) (c - 'a' + 'A');
            if (!((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'))) return null;
            normalized.append(c);
        }
        return normalized.length() >= 8 && normalized.length() <= 32 ? normalized.toString() : null;
    }

    private static String sha256(String normalized) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.US_ASCII));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >>> 4) & 15, 16));
                hex.append(Character.forDigit(b & 15, 16));
            }
            return hex.toString();
        } catch (Exception unavailable) {
            return null;
        }
    }

    private static long saturatedAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }
}
