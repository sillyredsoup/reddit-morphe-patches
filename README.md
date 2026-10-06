# Reddit patches for Morphe

These three independent patches target Reddit 2026.14.0:

- **Reddit - Hide app posts** hides Reddit Dev Platform games and apps in listings. It adds a **Hide games in feed** switch under Reddit's Morphe settings → Feed. The switch defaults to on and applies to newly loaded listings. This patch requires the upstream Morphe settings patch for the switch.
- **Reddit - NSFW mode** adds its own **NSFW** button to the bottom bar, after Vertical if present or otherwise after Home. It uses Reddit's blur-content icon, with a filled icon and active highlight when enabled. Inbox keeps its normal function. Turning the mode on enables Show NSFW, turns blur off, and shows only NSFW posts in the feed. Turning it off disables Show NSFW, turns blur back on, and shows only non-NSFW posts. The current feed refreshes after Reddit acknowledges both settings, without restarting the screen. Further taps while an update is pending queue the latest requested mode. Normal operation is silent; only unavailable settings, failed updates, or timeouts show a toast.
- **Reddit - Vertical home feed** adds a **Vertical** button with a video icon to the bottom bar. From Home, it opens Reddit's full screen media player at the first visible post (or the next image/video), in Home's order. Near the end it requests another page from Home's own pager and appends its images and videos. Repeated posts are skipped by post ID. Titles, usernames, and controls start hidden; tap the media to show or hide them. Normal post taps retain Reddit's behavior. This patch is experimental until tested in the app.

Add the GitHub source in Morphe Manager:

https://morphe.software/add-source?github=sillyredsoup/reddit-morphe-patches

In Expert mode, select both this source and the built-in Morphe source. Select the patches you want here, then select the upstream Reddit patches you want. **Hide navigation buttons** with **Hide Games** can be used alongside the NSFW button.

If you used version 0.1.x, select the two new patch names explicitly. Morphe does not carry the old combined patch selection over to them.

## Local build

Run `./build-local.sh` to build `reddit-mode.mpp` with public build tools. The bundle can also be imported as a Local source. This patch is pinned to Reddit 2026.14.0 and fails if its target bytecode changes.

## Verification

Morphe Desktop discovers all three patches and applies them together with all 18
default upstream Reddit patches from Morphe v1.46.0 to the 2026.14.0 APKM.
The authenticated Android emulator can load Home, open the vertical media viewer,
advance by swiping up, and toggle NSFW filtering both ways without a recorded crash.
See [Android testing](testing/android/README.md) for the persistent local setup.

Run `python3 testing/nsfw-regression.py` to check button ownership after another
navigation screen is created, toggling while account state lags, and pending,
confirmed, rejected, queued and timed-out account updates. These focused JVM
checks use Android/Compose fixtures. The user confirmed that v0.3.7 fixes the
profile → broken toggle reproduction on their phone.

Version 0.3.9 has build and local regression checks only. Emulator testing and
post-release download checks were skipped at the user's request; the new tab and
icon were subsequently checked in v0.3.10 after the user resumed testing.
Version 0.3.10 fixes the icon renderer lookup and was visually verified in the
emulator: NSFW is outlined when off and filled when on, with Inbox preserved and
the Vertical video icon also rendered correctly.
