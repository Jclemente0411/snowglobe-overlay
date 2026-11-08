# SnowGlobe Holiday Plus — release setup

Updated November 8, 2026 (UTC). This is the existing app's one-time upgrade, not a second app. The billing implementation introduced in 0.22.0 is retained in 0.22.2; Console activation and real purchase testing must be separately observed before paid-launch readiness is claimed.

## Current signed-build status

The **0.22.2 / 100031** signed release from [run 42431191659](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42431191659), source `f6f65700f00559e6cb36776d7020a92f301f2341`, passed its JVM and Android 15/17 device gates. The downloaded AAB passed signature, metadata, 16 KB library, archive and checksum verification and retains the existing upload certificate. See the [release handoff](PLAY_RELEASE_0222.md) for its exact artifact identity and digest.

The Play licensing key remains unconfigured. Actual release metadata records **`commerceConfigured: false` / `commerce-not-configured`**, and the $2.99 checkout remains disabled. Complimentary codes independently unlock the full feature set for testing. Complete the configuration and real-transaction steps below, then build a higher version code if 100031 has already been used.

## Complimentary access in 0.22.2

The **0.22.2 / 100031** patch adds independent no-charge access codes. A valid trial unlocks every Plus feature even when this Play checkout configuration is incomplete. Owner testing lasts up to 90 days and the shared preview lasts up to 7 days, both capped by the campaign end. Codes do not produce purchase receipts, require payment, or renew automatically. See [complimentary access operation](COMPLIMENTARY_ACCESS.md) and the [0.22.2 release handoff](PLAY_RELEASE_0222.md).

The owner reports that the 0.22.1 Play build now installs and runs. The access-code patch does not establish that the paid product is activated or that a real billing transaction passed. `commerceConfigured` remains an independent release metadata field.

## Exact product

| Field | Value |
| --- | --- |
| App/package | SnowGlobe / `com.snowglobe.overlay` |
| Product ID | `holiday_plus_lifetime` |
| Product title | SnowGlobe Holiday Plus |
| Product type | One-time product; non-consumable |
| Purchase option | Buy (permanent access); not Rent or subscription |
| Suggested purchase-option ID | `lifetime` |
| U.S. base price | USD 2.99, as authorized by the owner |
| Checkout price | The current localized price returned by Google Play |
| Description | Unlock festive falling decorations, extra motion and scene recipes, and two exclusive 4K winter wallpapers. One-time purchase; no subscription. |

Free: layered full-height snowfall, snow controls, top lights/colors, Christmas Eve and Arctic Night scenes, live wallpaper, Winter Lodge and Moonlit Pines 4K wallpapers.

Plus: candy canes, presents, trees, snowmen, glass bulbs, stars and confetti; floating fairy lights; alternate particle directions and rotation; Snowy Village, Vintage Christmas and North Pole Party recipes; Candlelight Village and Aurora Night 4K wallpapers.

## Console sequence

1. Use the existing `com.snowglobe.overlay` app and retain its existing app/upload signing identities. Upload a billing-enabled AAB to an appropriate test track if Console requires it before product configuration.
2. In **Monetize with Play → Products → One-time products**, create or inspect `holiday_plus_lifetime`. Use the exact ID above; product IDs cannot be silently substituted in the app.
3. Add a permanent **Buy** purchase option. Set the authorized U.S. price to **$2.99**, choose the intended regions, review local prices/taxes and activate the option. A draft or inactive option is not returned as available to BillingClient. No rental or recurring billing belongs to this product.
4. In the app's **Monetize → Monetization setup → Licensing**, obtain the genuine base64 RSA licensing public key. This public verification key is distinct from the private upload keystore or its SHA-256 fingerprint.
5. Store that public value as the repository Actions variable `PLAY_BILLING_PUBLIC_KEY` (or build with Gradle property `playBillingPublicKey`). The build requires a valid RSA public key of at least 2048 bits if a value is supplied. An empty key yields a free build with checkout disabled and `commerceConfigured: false` in release metadata. A `true` flag proves valid key format only; verify that the key belongs to this exact Console app and complete a Play test purchase before claiming paid readiness.
6. Build a new higher-version-code signed AAB if 100031 has already been uploaded. Do not re-upload different bytes under a used version code.
7. Confirm an appropriate payments profile and the product's country availability in Console. Use a license tester account on the actual Play-installed package to verify the one-time upgrade, Restore, cancellation, pending approval, acknowledgement, reinstall and refund/revocation. Ordinary test-track accounts can still incur real charges; a license tester receives Google's test payment methods.

Official references: [billing setup](https://developer.android.com/google/play/billing/getting-ready), [one-time product model and activation](https://support.google.com/googleplay/android-developer/answer/16430488), [find the licensing key](https://support.google.com/googleplay/android-developer/answer/186113), [purchase testing](https://developer.android.com/google/play/billing/test).

## Implemented safeguards

Billing Library 9.1.0; current localized ProductDetails; fresh buy offer before checkout; a single lifecycle-scoped client; pending and cancellation handling; no consumption of the permanent product; acknowledgement only after verified PURCHASED state; restore/query on foreground; successful empty ownership query revokes access; unavailable queries preserve previously verified ownership. The overlay pauses before Google's protected checkout UI.

The complete billing dependency graph is retained. Diagnostic transport upload components and their networking permissions are removed through the manifest, with actual-SDK and device regression gates. This is a reviewed boundary for the pinned SDK implementation, not a public Google telemetry opt-out API. See [Billing dependency isolation](BILLING_DEPENDENCY_ISOLATION.md) before updating dependencies.

A signed receipt must match the expected package and product, contain the actual purchased state and verify against the configured public key. Proof is written atomically under `noBackupFilesDir`; plain preferences and saved scenes cannot grant it. Purchase tokens and receipts are not logged. Tests use explicitly generated test keys confined to tests; no test key is embedded into release configuration.

This is client-side signed-receipt verification. Google recommends backend verification and real-time developer notifications for stronger fraud and revocation handling. This app does not have that backend: refunds are recognized on a subsequent successful Play ownership query, and no claim is made that offline revocations arrive immediately. References: [purchase security](https://developer.android.com/google/play/billing/security), [one-time lifecycle](https://developer.android.com/google/play/billing/lifecycle/one-time).

## Observed access limitation

Automatic approval review blocked the Cloud browser's Play Console navigation when it redirected to the unapproved `https://google.play` origin. No Console product, price, payments profile, licensing key or real purchase was inspected or changed. The blocked navigation was not bypassed. This account-side limitation does not prevent building and verifying the effects-only free functionality.
