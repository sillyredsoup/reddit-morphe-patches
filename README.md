# Reddit patches for Morphe

These two independent patches target Reddit 2026.14.0:

- **Reddit - Hide app posts** hides Reddit Dev Platform games and apps in listings. It adds a **Hide games in feed** switch under Reddit's Morphe settings → Feed. The switch defaults to on and applies to newly loaded listings. This patch requires the upstream Morphe settings patch for the switch.
- **Reddit - NSFW mode** uses the Inbox slot for an NSFW button in the bottom bar. It keeps the Inbox icon; the Home and Games buttons stay in place. Turning the mode on shows unblurred NSFW posts and filters out non-NSFW posts; turning it off restores the Show NSFW and Blur NSFW values saved when the mode was enabled.

Add the GitHub source in Morphe Manager:

https://morphe.software/add-source?github=sillyredsoup/reddit-morphe-patches

In Expert mode, select both this source and the built-in Morphe source. Select either or both patches here, then select the upstream Reddit patches you want. **Hide navigation buttons** with **Hide Games** can be used alongside the NSFW button.

If you used version 0.1.x, select the two new patch names explicitly. Morphe does not carry the old combined patch selection over to them.

## Local build

Run `./build-local.sh` to build `reddit-mode.mpp` with public build tools. The bundle can also be imported as a Local source. This patch is pinned to Reddit 2026.14.0 and fails if its target bytecode changes.

## Verification

Morphe Desktop discovers both patches and applies them together with the upstream Reddit patches to the 2026.14.0 APKM. The button, settings switch, and filtering still need device testing.
