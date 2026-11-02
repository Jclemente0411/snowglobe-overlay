# Dormant Holiday Plus foundation

Approved commercial direction: USD 2.99 one-time non-consumable holiday_plus_lifetime, no subscription. A future checkout must show the localized price returned by Google Play, not hardcode $2.99 for all regions.

This beta ships NO purchase UI, BillingClient dependency, billing permission, checkout launcher, networking or feature restrictions. PremiumPolicy is a tested policy/contract only; CHECKOUT_ENABLED=false, PAYWALL_VISIBLE=false and BETA_UNLOCKED=true. Changing a preference cannot activate payments. Unit and instrumentation tests require these values, and the signed-bundle gate rejects billing/network permissions.

Before enabling commerce in a separate release:
- Configure and activate the matching Play Console one-time product and USD price.
- Implement BillingClient and reconnect/query lifecycle; retrieve localized ProductDetails.
- Verify purchases before granting permanent entitlements; prefer a server verifier with refund/revocation notifications. Do not trust a local boolean.
- Handle cancellation, unavailable product, already-owned, network failure and PENDING without charging again or granting prematurely.
- Acknowledge valid PURCHASED transactions, never consume this lifetime product; restore from the owning Play account after reinstall.
- Add receipt/refund/revocation tests and tester accounts. Internal-track membership alone is not license-tester configuration.
- Gate paywall rendering and checkout separately; beta testers retain access while commerce is disabled.

References reviewed 2026-11-02:
https://developer.android.com/google/play/billing/integrate
https://developer.android.com/google/play/billing/test
https://support.google.com/googleplay/android-developer/answer/6062777

No real payment or end-to-end Google Play purchase test has been performed by this beta. The owner is not required to pay to test it.
