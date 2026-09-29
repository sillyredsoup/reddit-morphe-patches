# Reddit patches for Morphe

This project targets Reddit 2026.14.0.

Add this source in Morphe on your phone:

https://morphe.software/add-source?github=sillyredsoup/reddit-morphe-patches

In Expert mode, keep the built-in Morphe source selected alongside this one,
then enable **Reddit - Hide apps and NSFW mode** in this source's tab.

The **Reddit - Hide apps and NSFW mode** patch removes interactive Reddit App/game posts from listings. It also adds a separate NSFW bottom bar button. Enabling it saves the existing Show NSFW and Blur NSFW values, sets Show NSFW on and Blur NSFW off, and filters listings to NSFW posts. Tapping again restores the saved values.

The button works with Morphe's upstream Hide navigation buttons patch and its Hide Games setting. It reuses Reddit's Games tab UI, so the icon is still the Games icon. The new label is NSFW, and the button highlights while the mode is on. Only this new button toggles the mode; the original Games action stays intact. Reddit must have its Games feature enabled for this descriptor to be available.

## Local build

The credential-free local build downloads public tools on first use:

```sh
./build-local.sh
```

The output is `reddit-mode.mpp`. Add it alongside Morphe's upstream patch bundle, select **Reddit - Hide apps and NSFW mode**, and keep upstream Reddit patches selected as usual. This patch is pinned to the APK version above and fails if its bytecode fingerprints change.

The same bundle can also be added as a Local source after copying the
`reddit-mode.mpp` file to the phone. Morphe's Simple mode selects one source
at a time; Expert mode allows both source tabs in the same patching run.

## Current verification

The bundle compiles and Morphe Desktop applies it to the 2026.14.0 APKM together with 18 default upstream patches. The UI, account setting calls, and filter still need device testing.
