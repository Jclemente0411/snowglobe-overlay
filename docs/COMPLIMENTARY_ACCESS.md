# Complimentary full-feature access

Introduced in SnowGlobe **0.22.2 / 100031**, package `com.snowglobe.overlay`.

## Owner and tester flow

Open **Home → Explore Holiday Plus**, enter a supplied **Access code**, and select **Activate code**. Codes work inside SnowGlobe; they are not Google Play redemption codes. No payment method or app account is required. Access activates immediately, and the sheet shows its expiration. A valid code also works when paid checkout is unavailable.

The owner receives two privately delivered campaign codes:

| Campaign | Access after first redemption | Intended distribution |
| --- | --- | --- |
| Owner testing | Up to 90 days | Keep private for the owner's testing |
| Shared preview | Up to 7 days | Give free to users trying the full version |

Both campaigns start **November 7, 2026 at 00:00 UTC** and have a hard end of **March 1, 2027 at 00:00 UTC**. Actual expiration is the earlier of the first redemption plus the allotted duration or that hard end. Codes redeemed near the hard end provide less time. In U.S. Eastern time, the hard end is **February 28, 2027 at 7:00 p.m. EST**. Re-entering a code does not extend its trial. These grants do not renew and never charge the user.

The raw codes are deliberately absent from source, test fixtures, CI logs and this public document. Only random-code hashes and campaign rules ship in the application. Keep the longer owner code private. Shared codes are intended to be reusable by different testers; they are not globally single-use codes and must not be sold as an alternative payment route.

## Features covered

The combined access decision covers all premium switches and sliders, saved-scene rendering, three premium scene recipes, both premium wallpaper exports, the preview, over-app effects and the live wallpaper. The existing basic snow, lights and free artwork remain available without a code.

Verified Google Play ownership and complimentary access remain separate. `HolidayPlusEntitlement` and billing `Snapshot.owned` retain their signed-purchase meaning. `HolidayFeatureAccess.hasAccess` grants feature access when either purchase ownership or a current trial is valid. A Play ownership refresh or refund cannot erase an independent valid trial; an expired trial cannot remove a genuine purchase. Restore purchases restores Play ownership, not local trial history.

## Persistence and expiration

Trial state uses a private atomic file beneath Android's backup-excluded app directory. Campaign identity and activation history are validated against the compiled campaign rules; arbitrary expiration values or visual preferences cannot grant access. Code matching tolerates ASCII case, spaces and hyphens, with bounded input. Saved scenes never contain a code or entitlement.

Active access is observed through a lightweight timer, the activity's foreground lifecycle and renderer visibility. `plusRevision` invalidates already-running visuals when access changes. Rendering does not read trial files on each animation frame. Expiration preserves the user's selected visual settings while the existing scene policy clamps premium effects back to the free set.

Time enforcement uses device wall time with monotonic elapsed-time and high-water safeguards against ordinary clock rollback. This is an offline preview mechanism, not a server-authoritative licensing service. Clearing app storage or uninstalling removes local redemption history; normal-clock reuse remains bounded by the fixed campaign end. An offline application cannot guarantee immediate revocation, global redemption counts, per-person limits, or resistance to a rooted device with modified code/storage/time. A new campaign currently requires an application update.

For future campaigns, generate a new random code and a new campaign ID. Preserve existing campaign IDs, digests and timing rules while their local records remain supported; replacing a digest or removing a known campaign without an explicit storage migration invalidates its saved record. Append campaigns within the controller's bounded storage limits and add a migration before retiring historical definitions. Never commit plaintext owner or giveaway codes, and never change a campaign's original activation history to extend a trial silently.

## Paid upgrades and Google Play

The existing optional `holiday_plus_lifetime` upgrade remains a one-time Google Play purchase. Its product, licensing key, price and actual transaction checks are independent of this complimentary feature. See [Holiday Plus setup](HOLIDAY_PLUS_SETUP.md). Passing trial tests is not evidence of a real Google Play payment or product activation.

Google Play's own one-time product promotions require an active product buy option. Reusable custom Play promo codes are subscription-only; those are different from these no-charge in-app trial codes. See [Play Console promotions](https://support.google.com/googleplay/android-developer/answer/6321495?hl=en), [Billing promo integration](https://developer.android.com/google/play/billing/promo), and [Google Play Payments policy](https://support.google.com/googleplay/android-developer/answer/9858738?hl=en).

Android references: [monotonic and wall clocks](https://developer.android.com/reference/android/os/SystemClock), [backup-excluded files](https://developer.android.com/reference/android/content/Context#getNoBackupFilesDir()).

## Verification

The release workflow runs JVM tests and every declared Android instrumentation test on Android 15 and Android 17 before signing. Targeted tests cover valid and invalid entry, repeated redemption, trial persistence, expiration, tampered/corrupt state, clock behavior, separate purchased ownership, premium settings and real UI activation. Fixtures are isolated from release campaign codes. The exact completed run and signed artifact are recorded in [the release handoff](PLAY_RELEASE_0222.md).
