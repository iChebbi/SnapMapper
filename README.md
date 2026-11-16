<p align="center">
  <img src="docs/icon.svg" width="112" alt="SnapMapper icon">
</p>

<h1 align="center">SnapMapper</h1>

<p align="center">
  <b>Give the Snap Key on your OPPO phone a new job.</b><br>
  Single, double and long press, each with its own action.
</p>

<p align="center">
  <a href="../../releases/latest"><b>⬇ Download SnapMapper.apk (96 KB)</b></a>
</p>

<p align="center">
  <img src="docs/screenshot.png" width="320" alt="SnapMapper settings screen">
</p>

- **No computer, no root, no Developer options.** Install it straight on the phone.
- **Tiny: 96 KB.** The whole app is 97,893 bytes.
- **No extra battery use.** No timers, no polling, no internet. It only does work when you
  press the Snap Key.
- **No permissions to grant.** The only thing you switch on is its accessibility service.
- **Open source**, MIT licensed.

> [!IMPORTANT]
> **Set your Snap Key to "Translate".** After installing, open
> **Settings → Snap Key** and choose **Translate**. That's how ColorOS
> hands the Snap Key to SnapMapper. If it's set to anything else, SnapMapper never receives
> your presses.

---

## Contents

- [Quick start](#quick-start)
- [What it can do](#what-it-can-do)
- [Which phones](#which-phones)
- [Install, step by step](#install-step-by-step)
- [Choosing actions (read this: long press)](#choosing-actions)
- [Permissions and privacy](#permissions-and-privacy)
- [Size and battery](#size-and-battery)
- [Troubleshooting](#troubleshooting)
- [FAQ](#faq)
- [How it works](#how-it-works)
- [Limitations](#limitations)
- [Uninstall](#uninstall)
- [Build from source](#build-from-source)
- [Credits and license](#credits-and-license)

---

## Quick start

1. **Uninstall ColorOS Translate** if your phone has it.
2. **Download** [`SnapMapper.apk`](../../releases/latest) on the phone.
3. Open it from **My Files → Downloads** and choose **App Market (official)**, not "Package
   installer". If it shows a security scan, tap **Install anyway**.
4. Open SnapMapper and turn it on: **Accessibility → Downloaded apps → SnapMapper → On**.
5. **Set the Snap Key to Translate:** open **Settings → Additional settings → Snap Key** and
   choose **Translate**. SnapMapper now receives the Snap Key in place of the Translate app.
6. Press the Snap Key. Then pick your actions in SnapMapper.

Details for each step are [below](#install-step-by-step).

---

## What it can do

**Three gestures:** single press, double press and long press, each with its own action.

**Actions**

| Category | Actions |
|---|---|
| **Quick toggles** | Flashlight · Switch ring / vibrate · Cycle ring / vibrate / silent |
| **Capture** | Take screenshot · Open camera · Record video |
| **Open** | Any app · A link · Voice assistant · Web search · Circle to Search · Start a timer |
| **Media** | Play / pause · Next track · Previous track |
| **System** | Notifications · Quick settings · Recent apps · Home · Back · Lock screen · Power menu · Split screen |

**Smart behaviour**

- **Different actions when locked.** Choose a separate set for when the screen is off or locked.
- **Per-app actions.** Replace the single press while a chosen app is open, for example
  play/pause in your music app.
- **Pause during calls.** The Snap Key is ignored while you're on a call.
- **Feel the result.** Switching to vibrate gives one long strong buzz; switching to ring gives
  two short strong buzzes. You know what happened without looking.
- **On-screen confirmation (optional).** A small pill around the front camera shows what ran.
  Its position can be fine-tuned in Advanced settings.
- **Advanced settings.** Exact double-press window in milliseconds, and a press history.

The settings screen follows the ColorOS look: large title, rounded cards, light and dark mode.

---

## Which phones

Tested on the **OPPO Find X9 Ultra** (CPH2841, ColorOS 17, Android 17).

It should also work on other OPPO / OnePlus phones with a Snap Key (also called the action
button), **as long as the ColorOS Translate app can be uninstalled or isn't installed**.
See [How it works](#how-it-works) for why. If you try it on another phone, please open an issue
and say whether it worked.

---

## Install, step by step

### 1. Remove ColorOS Translate (if you have it)

SnapMapper takes the place of the ColorOS Translate app, so the two can't be installed at the
same time.

- **Settings → Apps → App management**, find **Translate**, tap **Uninstall**.
- Don't see Translate? Then you don't have it. Skip this step.
- If Translate can't be uninstalled on your phone, SnapMapper can't be installed there.

### 2. Download the app

On the phone, open the [latest release](../../releases/latest) and download
**`SnapMapper.apk`**.

### 3. Install it with App Market

Open **My Files** (the ColorOS file manager that comes with your phone), go to **Downloads**,
and tap **SnapMapper.apk**. When Android asks how to open it, choose **App Market (official)**:

<p align="center">
  <img src="docs/install-open-with.png" width="360" alt="Open with: App Market (official) or Package installer">
</p>

App Market scans the app. SnapMapper isn't from the App Market store, so it may show a warning.
Tap **Install anyway**.

> **Why App Market and not "Package installer"?** SnapMapper uses the package name
> `com.coloros.translate` (see [How it works](#how-it-works)). ColorOS only lets apps with an
> OPPO-style package name be installed by an app store. App Market is OPPO's store, so ColorOS
> accepts it. "Package installer" fails with **"App not installed"**.

### 4. Turn on SnapMapper

1. Open **SnapMapper**. It shows what's left to set up.
2. Tap **Turn on SnapMapper** → **Downloaded apps → SnapMapper → On**.
3. If the switch is greyed out: **Settings → Apps → SnapMapper → ⋮ (top right) → Allow
   restricted settings**, then try again.

### 5. Set the Snap Key to "Translate"

This is the step that connects the Snap Key to SnapMapper:

1. Open the phone's **Settings**.
2. Go to **Additional settings → Snap Key**.
3. Choose **Translate** from the list.

ColorOS sends Translate's Snap Key presses to the app named `com.coloros.translate`, and that's
now SnapMapper. (Translate no longer opens; SnapMapper runs your actions instead.)

SnapMapper now says **"Snap Key is ready"**. Press the Snap Key to try it (the default single
press toggles the flashlight), then choose your actions.

<details>
<summary><b>Alternative: install with ADB from a computer</b></summary>

If App Market isn't available, you can install once from a computer. This needs USB debugging
(Developer options) only for the install; turn it off afterwards.

```bash
adb install -i com.android.vending SnapMapper.apk
```

`-i com.android.vending` marks the install as coming from Google Play, which ColorOS also
accepts. Then continue with [step 4](#4-turn-on-snapmapper).

</details>

---

## Choosing actions

**Use single and double press for anything you do while typing. Use long press for actions
that open something.**

On a **single or double press**, ColorOS sends SnapMapper a silent background message.
Nothing on screen changes.

On a **long press**, ColorOS always opens a tiny invisible SnapMapper screen. This is how
ColorOS handles long presses for the Translate slot, and SnapMapper can't change it. The screen
closes instantly, but if the keyboard is open, it **briefly hides and reappears (flickers)**.

| Good for long press | Better on single / double press |
|---|---|
| Open camera / record video | Switch ring / vibrate |
| Open an app or a link | Cycle ring / vibrate / silent |
| Take screenshot | Toggle flashlight |
| Voice assistant | Play / pause, next / previous track |
| Lock screen, power menu, recent apps | Anything you use while typing |
| Start a timer, web search | |

**Circle to Search:** SnapMapper asks Android to open it, which takes about a third of a second.
The phone has to be unlocked. On phones that refuse that call, SnapMapper falls back to what a
finger does and holds the navigation handle for a moment: that takes over a second, needs the
handle (or the home button) shown, and its hold time is under
**Advanced settings → Circle to Search hold**. Tested on the OPPO Find X9 (CPH2797, ColorOS 17).

**Screen off:** ColorOS turns the screen on for every long press. For actions that don't need
the screen (ring/vibrate, flashlight, media), SnapMapper turns it straight back off, so you'll
see at most a brief flash.

**Double press:** SnapMapper waits a moment after the first press to see if a second one
follows. If no double-press action is set, single presses run instantly. Tune the wait under
**Behaviour → Double press speed**, or set it exactly in **Advanced settings**.

---

## Permissions and privacy

SnapMapper asks for **no runtime permissions**. There's nothing to allow in a pop-up.

| Access | Used? |
|---|---|
| Internet | **No.** The app has no internet permission, so nothing can leave your phone. |
| Storage, location, contacts, camera, mic | **No.** |
| Root, ADB, Developer options | **No.** |
| Vibration | Yes, for haptic feedback. Android grants it automatically. |
| Accessibility service | **The one thing you switch on.** See below. |

**What the accessibility service is used for:** only for things a normal app isn't allowed to
do from the background: taking a screenshot, locking the screen, opening notifications, quick
settings, recent apps or the power menu, opening apps while you're in another app, holding the
navigation handle as the Circle to Search fallback (the "perform gestures" capability), and showing
the optional pill around the camera.

It's configured to **not read screen content** (`canRetrieveWindowContent="false"`), and it
doesn't receive your key presses or typing. If you add per-app actions, it's told which app is
in the foreground, and only then.

Settings and press history are stored only on the phone. The full source is in this repository.

---

## Size and battery

**Lightweight:**

| What | Size |
|---|---|
| Download (APK) | **96 KB** (97,893 bytes) |
| Installed size | **96 KB** app file (Android may add a little compiled code and cache) |
| Libraries | None. Plain Android framework code, no AndroidX, no third-party SDKs |
| Network access | None (no internet permission) |

**No extra battery use:**

- **No timers, no polling, no wake locks, no location, no network.**
- **Idle between presses.** Android keeps its accessibility service loaded, as it does for
  every accessibility app, but the service sits idle and does no work.
- **It works only when you press.** ColorOS wakes SnapMapper's small press receiver when you
  press the Snap Key, and disconnects it about a minute later.
- **No key filtering.** It doesn't inspect other buttons or your typing.
- **The optional camera pill** uses one small window that's hidden, and draws nothing, between
  presses.

To check on your own phone: **Settings → Battery → Battery usage**. SnapMapper (it may be
listed as Translate) should be at or near 0%.

---

## Troubleshooting

**"App not installed" when installing**
You used "Package installer". Open the APK again from **My Files** and choose **App Market
(official)**. If Translate is still installed, uninstall it first.

**The accessibility switch is greyed out**
Settings → Apps → SnapMapper → ⋮ → **Allow restricted settings**, then turn it on again.

**Pressing the Snap Key does nothing**
- The Snap Key must be set to **Translate** (Settings → Additional settings → Snap Key).
- SnapMapper must be on in Accessibility. Open SnapMapper: the status card says what's missing.
- Check the action isn't "Do nothing", and that "Pause during calls" isn't stopping it.
- If ColorOS stopped SnapMapper after a restart, turn it off and on again in Accessibility.

**The keyboard flickers on long press**
Expected: see [Choosing actions](#choosing-actions). Move that action to single or double press.

**The camera pill isn't centred on the camera**
SnapMapper → Advanced settings → **Pill position**, then tap Up or Down (it previews live).

**Single press feels slow**
That's the double-press wait. Pick **Fast** under Behaviour → Double press speed, or set no
double-press action so single presses run instantly.

---

## FAQ

**Why does the Snap Key have to be on "Translate"?**
That's the only Snap Key function that can be handed to a third-party app. See
[How it works](#how-it-works).

**Will the real Translate feature still work?**
No. SnapMapper replaces the ColorOS Translate app. To get it back, uninstall SnapMapper and
reinstall Translate from App Market.

**Why does SnapMapper show up as "Translate" in some places?**
In ColorOS menus that list the Snap Key function, it shows the function name (Translate). Your
app drawer shows SnapMapper.

**Does it work when the phone is locked or the screen is off?**
Yes. You can even give it a separate set of actions for when it's locked.

**Does it need to run all the time?**
No. Nothing runs between presses; see [Size and battery](#size-and-battery).

**Is it safe?**
It has no internet permission and doesn't read your screen. The code is short and all here to
read.

---

## How it works

ColorOS doesn't let normal apps see the Snap Key. The button (`BTN_TRIGGER_HAPPY32` on the
`gpio-keys` input device) isn't mapped to an Android key code, so accessibility services and
key listeners never receive it. ColorOS handles it inside the system instead:

1. The framework (`StrategyActionButtonKeyLaunchApp`) reads the raw key and turns it into
   internal events: **single tap** or **long press**.
2. ColorOS's gesture app (`com.oplus.gesture`) runs the function you chose in
   **Settings → Snap Key**. Each function ("scene") is defined in its built-in
   `action_button_scene_config.xml`, which names an **owner package** and what to start.
3. For **Translate**, the owner is `com.coloros.translate`, and:
   - **single tap**: the gesture app binds to
     `com.coloros.translate/.ui.cube.KeyMessengerService` and sends a Messenger message with
     `key = "SINGLE_TAP"`;
   - **long press**: it starts the activity action `coloros.intent.action.translate.CUBE` in
     that package, with `action_key_type = 2`.
     Other versions of the gesture app (8.6.0 on the Find X9) start a foreground service with
     action `oplus.intent.action.GLOBAL_TRANSLATION` instead; SnapMapper provides both.

SnapMapper uses the package name `com.coloros.translate` and provides exactly that service and
activity, so ColorOS delivers every Snap Key press to it. SnapMapper only accepts messages sent
by `com.oplus.gesture`. It then works out single, double or long press and runs your action.

**Installing:** ColorOS blocks installs of non-OPPO apps with OPPO-style package names, unless
the installer is a trusted app store. Installing through OPPO's App Market (or Google Play via
ADB) satisfies that check.

**Routes that don't work** without OPPO's signature or root: ColorOS's key interceptor API
(`OplusKeyEventManager`) and the Snap Key scene database both require the signature-only
permission `oplus.permission.OPLUS_COMPONENT_SAFE`. The hidden "test" Snap Key function, whose
long press would be silent, is removed from the list by ColorOS on every boot.

---

## Limitations

- **Snap Key must stay on "Translate".** Any other function sends presses back to ColorOS.
- **Long press flickers the keyboard** if it's open (see [Choosing actions](#choosing-actions)).
- **If OPPO reinstalls Translate**, for example through a system update, it will conflict with
  SnapMapper and the Snap Key will stop reaching it. Uninstall Translate again and reinstall
  SnapMapper.
- **A ColorOS update could change how the Snap Key works.** Tested on the version above.
- Updates must be signed with the same key as the installed version. If you build it yourself,
  uninstall the release version first.

---

## Uninstall

1. **Settings → Apps → SnapMapper → Uninstall.**
2. **Settings → Additional settings → Snap Key**: choose any other function.
3. Optional: reinstall **Translate** from App Market.

Nothing else on your phone was changed.

---

## Build from source

Requirements: JDK 17 and the Android SDK (platform 35).

```bash
git clone https://github.com/amarsakore/SnapMapper.git
cd SnapMapper
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. Install it the same way
as the release (App Market, or the ADB command).

| File | What it does |
|---|---|
| `app/src/main/java/com/coloros/translate/ui/cube/KeyMessengerService.kt` | Receives single taps from ColorOS |
| `app/src/main/java/com/coloros/translate/ui/cube/LongPressActivity.kt` | Receives long presses |
| `app/src/main/java/com/amar/snapmapper/SnapKeyService.kt` | Accessibility service: gestures, locked/per-app logic, camera pill |
| `app/src/main/java/com/amar/snapmapper/KeyAction.kt` | All actions and their haptics |
| `app/src/main/java/com/amar/snapmapper/MainActivity.kt` | The settings screen |
| `app/src/main/java/com/amar/snapmapper/Prefs.kt` | Settings storage |

Issues and pull requests are welcome.

---

## Credits and license

Inspired by [KeySnap](https://github.com/Nicholas-Arda/KeySnap). No code from KeySnap is used.

SnapMapper isn't affiliated with or endorsed by OPPO. "OPPO", "ColorOS" and "Snap Key" are
trademarks of their owners.

Released under the [MIT License](LICENSE).
