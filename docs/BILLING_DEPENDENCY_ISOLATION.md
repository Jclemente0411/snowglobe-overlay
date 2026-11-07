# Billing dependency isolation

SnowGlobe pins Google Play Billing **9.1.0** and retains its complete dependency
class graph. The app manifest removes the optional DataTransport backend discovery
service, job uploader service and alarm uploader receiver, together with the
`INTERNET` and `ACCESS_NETWORK_STATE` permissions they introduce. Billing IPC, the
`com.android.vending.BILLING` permission, the SDK version metadata and both proxy
billing activities remain.

## Why this is necessary

The [official Billing 9.1.0 POM](https://dl.google.com/dl/android/maven2/com/android/billingclient/billing/9.1.0/billing-9.1.0.pom)
includes DataTransport API 3.0.0, runtime 3.1.8 and CCT backend 3.1.8. Their merged
manifests add networking permissions even though the Billing AAR's own manifest
does not. Checking only the Billing AAR was insufficient. The official POMs for
Billing 8.0.0, 8.1.0, 8.2.1 and 8.3.0 have the same transport dependencies; a
supported-version downgrade does not remove this dependency chain.

## Evidence for the pinned configuration

The exact Google-published [DataTransport runtime 3.1.8 source archive](https://dl.google.com/dl/android/maven2/com/google/android/datatransport/transport-runtime/3.1.8/transport-runtime-3.1.8-sources.jar)
establishes the boundary: `MetadataBackendRegistry` returns no backend when its
discovery service is absent. `DefaultScheduler` then reports an unregistered
backend to its callback and returns **before** decorating or persisting the event,
or scheduling an upload. The SDK's fire-and-forget transport callback accepts
that rejection. Removing both uploader components also prevents Android from
invoking upload handlers for old jobs. Initialization-time attempts to schedule
legacy queue entries are contained by the runtime's `SafeLoggingExecutor`.

Class exclusion was tested and rejected: although Billing's logger has a
catch-all initialization handler, `BillingClient.build()` can fail during class
verification before that handler executes. The actual-SDK regression caught a
`NoClassDefFoundError` for `Destination`. This build therefore retains all classes
and controls the manifest entrypoints instead.

Reviewed [Billing 9.1.0 AAR](https://dl.google.com/dl/android/maven2/com/android/billingclient/billing/9.1.0/billing-9.1.0.aar)
SHA-256:

```
d28286d4e4c18725510980de01211d9b72501e7146d96817fa0c9999af9b0d22
```

This is **evidence about a pinned implementation, not a documented public Google
telemetry opt-out API**. No SDK bytecode is modified. Permissions are removed
together with the backend registration and upload entrypoints, and no fake billing
implementation replaces Google Play.

## Required verification

`BillingSdkIsolationTest` exercises actual SDK construction, connection,
product lookup, purchase lookup and closure with all transport classes retained.
It also submits a synthetic byte to the actual transport scheduler, which must
reject it as an unregistered backend and leave no telemetry upload job. The
instrumented package/privacy regression repeats these checks on ART and verifies
the compiled app's networking permissions and transport components are absent.
Neither test launches a purchase, acknowledges or consumes an item, or grants an
entitlement.

Every dependency change must repeat those tests, inspect the complete resolved
debug/release runtime graphs, and verify both merged manifests plus the final
bundle. The real Android 15 and Android 17 suites and signed-bundle permission
gate must pass before release. A fake gateway test alone does not verify this
SDK integration.

These checks do not prove a live Google Play transaction. The genuine app licensing
key, active product, localized price, license-test purchase/restore/refund and
acknowledgement checks described in [Holiday Plus setup](HOLIDAY_PLUS_SETUP.md)
remain required before activating paid checkout.
