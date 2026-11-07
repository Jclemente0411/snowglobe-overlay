package com.snowglobe.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;

import com.android.billingclient.api.Purchase;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Locally verifies Play's signed receipt. No preference or scene file can grant ownership.
 * Receipts are private, excluded from Android backup and never written to logs.
 * Successful foreground Play queries refresh/revoke this cache; offline revocation requires
 * a future successful query. A server verifier with RTDN can strengthen this client-only model.
 */
public final class HolidayPlusEntitlement {
    private static final int MAX_RECEIPT_BYTES = 64 * 1024;
    private static final Object STORAGE_LOCK = new Object();
    private static CachedOwnership cachedOwnership;
    // Shared across foreground Activity/controller instances; never itself an ownership proof.
    private static long observationGeneration;

    private final String packageName;
    private final String cacheKey;
    private final AtomicFile receiptFile;
    private final PublicKey publicKey;
    private final SharedPreferences effectPreferences;

    HolidayPlusEntitlement(Context context, String encodedPublicKey) {
        Context app = context.getApplicationContext();
        packageName = app.getPackageName();
        File file = new File(app.getNoBackupFilesDir(), "holiday_plus_receipt.json");
        receiptFile = new AtomicFile(file);
        publicKey = decodeKey(encodedPublicKey);
        cacheKey = file.getAbsolutePath() + "\n" + normalizeKey(encodedPublicKey);
        effectPreferences = app.getSharedPreferences("snowglobe", Context.MODE_PRIVATE);
    }

    /** Safe to read while making a settings snapshot; subsequent reads use verified process memory. */
    public static boolean isOwned(Context context, String encodedPublicKey) {
        synchronized (STORAGE_LOCK) {
            String key = new File(context.getApplicationContext().getNoBackupFilesDir(),
                    "holiday_plus_receipt.json").getAbsolutePath() + "\n" + normalizeKey(encodedPublicKey);
            if (cachedOwnership != null && cachedOwnership.key.equals(key)) return cachedOwnership.owned;
            return new HolidayPlusEntitlement(context, encodedPublicKey).isOwned();
        }
    }

    public static boolean isVerificationConfigured(String encodedPublicKey) {
        return decodeKey(encodedPublicKey) != null;
    }

    boolean isConfigured() { return publicKey != null; }

    long observationGeneration() {
        synchronized (STORAGE_LOCK) { return observationGeneration; }
    }

    void observePlayResult() {
        synchronized (STORAGE_LOCK) { ++observationGeneration; }
    }

    boolean isOwned() {
        synchronized (STORAGE_LOCK) {
            if (cachedOwnership != null && cachedOwnership.key.equals(cacheKey)) return cachedOwnership.owned;
            boolean owned = false;
            if (publicKey != null) {
                try {
                    JSONObject envelope = new JSONObject(readReceipt());
                    Purchase purchase = new Purchase(envelope.getString("receipt"), envelope.getString("signature"));
                    owned = isVerifiedPurchase(purchase)
                            && (purchase.isAcknowledged() || purchase.getPurchaseToken()
                            .equals(envelope.optString("acknowledgedToken", "")));
                } catch (Exception ignored) {
                    // Missing, corrupt, foreign or unverified receipts never unlock paid content.
                }
            }
            cachedOwnership = new CachedOwnership(cacheKey, owned);
            return owned;
        }
    }

    boolean isTarget(Purchase purchase) {
        return purchase != null && packageName.equals(purchase.getPackageName())
                && purchase.getProducts().contains(PremiumPolicy.PRODUCT_ID)
                && !purchase.getPurchaseToken().trim().isEmpty();
    }

    boolean isAuthentic(Purchase purchase) {
        if (publicKey == null || !isTarget(purchase)) return false;
        String payload = purchase.getOriginalJson();
        String encodedSignature = purchase.getSignature();
        if (payload == null || payload.length() > MAX_RECEIPT_BYTES || encodedSignature == null
                || encodedSignature.isEmpty() || encodedSignature.length() > 2048) return false;
        try {
            // Play's signed purchase protocol uses SHA1withRSA; this is not an app signing key.
            Signature verifier = Signature.getInstance("SHA1withRSA");
            verifier.initVerify(publicKey);
            verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(encodedSignature.replaceAll("\\s", "")));
        } catch (Exception ignored) {
            return false;
        }
    }

    boolean isVerifiedPurchase(Purchase purchase) {
        if (purchase == null || purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED
                || !isAuthentic(purchase)) return false;
        try {
            // BillingClient normalizes several legacy JSON states. Require the actual signed
            // purchased value, not a missing/default/canceled receipt field.
            return new JSONObject(purchase.getOriginalJson()).optInt("purchaseState", -1) == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Called only after Play reports acknowledgement or returns an already acknowledged purchase. */
    boolean saveAcknowledged(Purchase purchase) {
        if (!isVerifiedPurchase(purchase)) return false;
        synchronized (STORAGE_LOCK) {
            boolean previouslyOwned = isOwned();
            FileOutputStream output = null;
            boolean persisted = false;
            try {
                JSONObject envelope = new JSONObject();
                envelope.put("receipt", purchase.getOriginalJson());
                envelope.put("signature", purchase.getSignature());
                envelope.put("acknowledgedToken", purchase.getPurchaseToken());
                byte[] bytes = envelope.toString().getBytes(StandardCharsets.UTF_8);
                if (bytes.length > MAX_RECEIPT_BYTES) return false;
                output = receiptFile.startWrite();
                output.write(bytes);
                receiptFile.finishWrite(output);
                persisted = true;
            } catch (Exception ignored) {
                if (output != null) receiptFile.failWrite(output);
            }
            // A valid, acknowledged receipt also remains usable in this process if storage is full.
            cachedOwnership = new CachedOwnership(cacheKey, true);
            ++observationGeneration;
            if (!previouslyOwned) notifyOwnershipChanged();
            return persisted;
        }
    }

    void revoke() {
        synchronized (STORAGE_LOCK) {
            boolean previouslyOwned = isOwned();
            receiptFile.delete();
            cachedOwnership = new CachedOwnership(cacheKey, false);
            ++observationGeneration;
            if (previouslyOwned) notifyOwnershipChanged();
        }
    }

    private void notifyOwnershipChanged() {
        long revision = effectPreferences.getLong("plusRevision", 0L);
        // This only invalidates visual settings. Its value is never used to grant access.
        effectPreferences.edit().putLong("plusRevision", revision == Long.MAX_VALUE ? 0L : revision + 1L).apply();
    }

    private String readReceipt() throws Exception {
        try (FileInputStream input = receiptFile.openRead();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > MAX_RECEIPT_BYTES) throw new IllegalArgumentException("Receipt too large");
                bytes.write(buffer, 0, count);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String normalizeKey(String encodedPublicKey) {
        return encodedPublicKey == null ? "" : encodedPublicKey.replaceAll("\\s", "");
    }

    private static PublicKey decodeKey(String encodedPublicKey) {
        String normalized = normalizeKey(encodedPublicKey);
        if (normalized.isEmpty() || normalized.length() > 8192) return null;
        try {
            PublicKey key = KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(normalized)));
            return key instanceof RSAPublicKey && ((RSAPublicKey) key).getModulus().bitLength() >= 2048
                    ? key : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class CachedOwnership {
        final String key;
        final boolean owned;
        CachedOwnership(String key, boolean owned) { this.key = key; this.owned = owned; }
    }
}
