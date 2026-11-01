# SnowGlobe product and release checklist

## Product decisions (approved)
- Free: snow overlay, twinkling lights, holiday color palettes, and a curated starter set of downloadable 4K portrait wallpapers.
- Holiday Plus: **$2.99 one-time, non-consumable** Google Play purchase. Unlock extra seasonal effects (presents, candy canes, stars, confetti), expanded color and intensity controls, and exclusive wallpaper packs.
- Free core functionality must continue without payment. Restore purchases across reinstall/account devices.
- No ads in first release; no subscriptions.
- Holiday Plus must **not** be represented as purchasable until Google Play Billing is integrated, purchase acknowledgement/restore tested, and products configured in Play Console.

## Implementation milestones
1. Resolve Android CI lint and get debug build + lint green.
2. Create licensed/original 4K portrait wallpaper assets, plus thumbnails, previews, download/set-wallpaper UI, file-size and memory checks.
3. Add premium effects behind an entitlement state, while leaving free snow/lights/palettes unlocked.
4. Integrate Google Play Billing Library, product ID `holiday_plus_lifetime` (verify product setup), purchase acknowledgement, pending purchases, restore and error states. Never unlock premium solely via a local boolean.
5. Add UI tests for free controls, permission denied, overlay start/stop, premium locked/unlocked, purchase failures and restore.
6. Build signed Android App Bundle (.aab), not just debug APK, for Play internal testing. Confirm applicationId matches the Play Console app; configure signing and tester opt-in.
7. Test on actual Pixel device and other Android versions. Review Play policy for overlay permissions and wallpaper use.

## Release gates
- `assembleDebug` and `lintDebug` pass in GitHub Actions.
- Release bundle builds and uploads successfully to Play Console.
- All wallpaper image licenses, resolutions, and preview quality verified.
- No misleading payment or premium claims.
- Manual device QA and Play internal tester opt-in verified.

## Current limitations
- The current repository app is an early prototype; wallpaper library, purchase flow and premium effects are **not** complete.
- A successful debug APK alone is not sufficient to publish to Google Play.
