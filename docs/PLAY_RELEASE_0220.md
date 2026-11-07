# SnowGlobe 0.22.0 Play handoff

## Verified release download

[Download SnowGlobe-0220-signed-AAB-18-1.zip](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42393453546/artifacts/13583285949), extract it, and upload **SnowGlobe-0.22.0-build100029.aab** to the existing `com.snowglobe.overlay` listing. The ZIP also contains the checksum, source identity and verification reports. Save it before the artifact expires on November 21, 2026.

[Run 42393453546](https://github.com/Jclemente0411/snowglobe-overlay/actions/runs/42393453546) passed both complete Android suites and existing-key signing/bundle checks for source `914164b561dc761f501e59df026b4490c738ad2c`. This is the free release: its signed-build log records `commerceConfigured: false`. Holiday Plus checkout requires the separately documented configuration and a new build. Read the [final release audit](RELEASE_READINESS_0220.md), including the workspace interruption and remaining account/physical-device limits.

## Release notes

A fresh focus on winter magic:
- More natural, full-screen snow with layered depth and varied crystals.
- A simpler Christmas Studio and refined SnowGlobe icon.
- Two included original 4K wallpapers, saved directly on your device.
- Preserved pause, power-saving and wallpaper controls.
- Removed the optional SnowGlobe keyboard so you can keep using your preferred keyboard.

Holiday Plus purchase support is included in the code. Do not advertise the $2.99 upgrade as purchasable until its genuine licensing key, active product and Play test transaction are verified.

## Store text matching the free release

Name: **SnowGlobe: Snow & Lights**

Short description: **Layered Christmas snow, glowing lights and original 4K winter wallpapers.**

Full description:

Bring a little winter wonder to your phone with SnowGlobe. Watch layered snow drift across supported screens, add warm Christmas lights, or choose an original winter wallpaper.

Make it yours with snow size, speed, amount, visibility and crystal shapes. Tune your lights and colors, choose a curated scene, and save favorite settings. Two original 2160 × 3840 wallpapers are included: Winter Lodge and Moonlit Pines.

Start or pause over-app effects in SnowGlobe, its notification or the optional Quick Settings tile. Reduced motion, battery-saving and thermal controls help keep the atmosphere comfortable. Live wallpaper is selected separately through Android's wallpaper picker.

SnowGlobe does not include a keyboard, ads or an app account. Artwork is generated on your device. The optional over-app effect requires Display over other apps permission. Android can hide overlays on protected screens; pause effects before payment or login if a protected app requires it. Ordinary overlays do not appear over the secure lock screen or notification shade; live wallpaper is the supported way to decorate available Home/Lock screens.

Designed with Pixel in mind. SnowGlobe is independent and is not affiliated with Google or Samsung.

## Foreground-service declaration draft

Type: `specialUse` / `FOREGROUND_SERVICE_SPECIAL_USE`.

User-facing function: Ongoing holiday snowfall and light decorations on supported screens after the user explicitly enables the overlay. The persistent notification explains that effects are active and provides Pause; the in-app and Quick Settings controls also stop effects.

Impact of interruption/deferment: The visible effect the user deliberately started would disappear or fail to begin. Work is visual and immediate while enabled, and animation is stopped or reduced for hidden/noninteractive/power-limited conditions.

Demonstration to record from the actual app: open SnowGlobe, grant overlay access, start effects, go Home, show the persistent notification, press Pause and show the overlay stopping. Google requests an actual demonstration URL for the declaration. This document is not a submitted declaration or approval.

Reference: [Google foreground-service declaration requirements](https://support.google.com/googleplay/android-developer/answer/13392821).

## Account checks

Use the verified signed file and correct version code. Review the current [privacy policy](PRIVACY.md), Data safety answers including Google Play Billing behavior, content rating, ads declaration, store screenshots, countries, payments/product setup and production access in the existing Console account. No account state has been inspected during this blocked session.

Target API 36 matches the current [phone-app submission requirement](https://support.google.com/googleplay/android-developer/answer/11926878). Google review and account-specific production testing can affect availability; a completed AAB does not guarantee a same-day public rollout. See [personal-account testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465) when applicable.
