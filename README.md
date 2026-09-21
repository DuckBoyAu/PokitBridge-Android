# Pokit Bridge for Android

Sideload Android app that reads the **large center readout** on the official [Pokit](https://play.google.com/store/apps/details?id=com.ingenuity.pokit.dev) app and sends it live to a Garmin watch running **Pokit Pro**.

It does **not** replace the official Pokit app. Keep Pokit open and measuring; this app only copies the big number in the middle of that screen.

**Download the APK:** see [Releases](https://github.com/DuckBoyAu/PokitBridge-Android/releases).

---

## What you need

- Android phone (tested on Pixel 9 Pro)
- Official **Pokit** app from Google Play
- **Garmin Connect** installed and paired with your watch
- **Pokit Pro** watch app installed from the Connect IQ Store (or your own matching Connect IQ build)

---

## 1. Pause Google Play Protect (for this install)

Play Protect often blocks sideloaded APKs.

1. Open the **Play Store**.
2. Tap your **profile picture** (top right).
3. Tap **Play Protect**.
4. Tap the **gear** (settings).
5. Turn **off** **Scan apps with Play Protect**.
6. Install Pokit Bridge (steps below).
7. Turn Play Protect **back on** when the app is installed.

If you already started the install and see *Play Protect doesn't recognize this app*:

1. Tap **More details**.
2. Tap **Install anyway** (or **Scan app**, then install if it still offers that).

---

## 2. Allow installing unknown apps

1. Copy `PokitBridge.apk` (from [Releases](https://github.com/DuckBoyAu/PokitBridge-Android/releases)) to the phone, or open the download in Chrome/Files.
2. If Android asks *blocked for your protection*:
   - Open **Settings → Apps → Special app access → Install unknown apps**.
   - Choose **Files**, **Chrome**, or **Drive** (whichever you used).
   - Turn on **Allow from this source**.
3. Open the APK and tap **Install**.

---

## 3. Enable Restricted settings (required for Accessibility)

Sideloaded apps cannot turn on Accessibility until you unlock **Restricted settings** (Android 13 and later).

1. Open **Settings → Apps → See all apps**.
2. Tap **Pokit Bridge**.
3. Tap the **three dots** (⋮) in the top-right corner.
4. Tap **Allow restricted settings**.  
   If you do not see that item, open **Pokit Bridge** once, then return here.
5. Then open **Settings → Accessibility**.
6. Tap **Pokit Bridge**.
7. Turn it **On**. Read the prompt — it only looks at the official Pokit app screen so it can send the number to your watch.

If Accessibility still says *restricted setting*, repeat step 3–4 after opening Pokit Bridge once.

---

## 4. Use it

1. Keep **Garmin Connect** running in the background.
2. Open **Pokit Bridge**. You should see `Garmin link ready: <your watch>`.
3. Do **not** tap **Start BLE** for this mode.
4. Open the official **Pokit** app and start measuring. The large center value is what gets sent.
5. Open **Pokit Pro** on the watch.

You should see the same center number as the phone (for example `0.000`, `-0.007`, `3.14`).

---

## Troubleshooting

| Problem | What to try |
| --- | --- |
| Cannot install APK | Pause Play Protect; allow unknown apps for Files/Chrome |
| Accessibility is greyed out / restricted | Apps → Pokit Bridge → ⋮ → Allow restricted settings |
| Watch says Open phone page | Open Pokit Pro on the watch; keep Garmin Connect and Pokit Bridge enabled |
| Watch not updating | Confirm Bridge log shows `Garmin link ready` and `Center ← …` |
| Play Protect warning after install | Normal for sideload. You can turn scanning back on after install |

---

## Privacy

Pokit Bridge uses an Accessibility service **only** for `com.ingenuity.pokit.dev` (official Pokit). It reads the large center measurement and sends it to your paired Garmin watch through Garmin Connect.

This project is not affiliated with Pokit Innovations or Garmin.

---

## Build from source

```bash
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`
