# Pokit Bridge

Sideload Android app that reads the **large center readout** on the official [Pokit](https://play.google.com/store/apps/details?id=com.ingenuity.pokit.dev) app and sends it live to a Garmin **fēnix 8** / **tactix 8** running **Pokit Pro**.

It does **not** replace the official Pokit app. Keep Pokit open and measuring; this app only copies the big number in the middle of that screen.

**Latest download (v0.6.2):**

- [PokitBridge.apk](https://github.com/DuckBoyAu/PokitBridge-Android/releases/latest/download/PokitBridge.apk) — Android phone
- [PokitPro.iq](https://github.com/DuckBoyAu/PokitBridge-Android/releases/latest/download/PokitPro.iq) — Connect IQ store / Garmin upload
- Watch sideload (USB): pick the `.prg` for your watch from [Releases](https://github.com/DuckBoyAu/PokitBridge-Android/releases/latest)

Phone and watch **must** be this matching pair (App ID `860d8a30d7e84298b4be71bd013e4afa`). Mixing an older APK with a newer watch app (or the other way around) stops live readings.

---

## What you need

- Android phone (tested on Pixel 9 Pro)
- Official **Pokit** app from Google Play
- **Garmin Connect** installed and paired with your watch
- A **fēnix 8** or **tactix 8**
- **Pokit Pro** on the watch (Connect IQ Store, or sideload from this release)

---

## 1. Install the watch app

### Option A — Connect IQ Store (if the listing is live)

Install **Pokit Pro** from Connect IQ on your phone, then sync the watch.

### Option B — USB sideload

1. Plug the watch in. Choose **MTP** / file transfer if asked.
2. Copy the matching file into `GARMIN\APPS\`:

   | Watch | File |
   | --- | --- |
   | fēnix 8 AMOLED 47mm / 51mm, tactix 8 47mm AMOLED | `PokitPro-fenix847mm.prg` |
   | fēnix 8 AMOLED 43mm | `PokitPro-fenix843mm.prg` |
   | fēnix 8 Pro | `PokitPro-fenix8pro47mm.prg` |
   | fēnix 8 Solar 47mm | `PokitPro-fenix8solar47mm.prg` |
   | fēnix 8 Solar 51mm, tactix 8 51mm Solar | `PokitPro-fenix8solar51mm.prg` |

3. Eject the watch. Open **Pokit Pro** from the apps list.

You can also upload `PokitPro.iq` from the [release](https://github.com/DuckBoyAu/PokitBridge-Android/releases/latest) on the [Connect IQ developer dashboard](https://apps.garmin.com/developer/dashboard) if you are publishing the listing.

---

## 2. Pause Google Play Protect (for this install)

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

## 3. Allow installing unknown apps

1. Download [PokitBridge.apk](https://github.com/DuckBoyAu/PokitBridge-Android/releases/latest/download/PokitBridge.apk) on the phone, or copy it from a PC.
2. If Android asks *blocked for your protection*:
   - Open **Settings → Apps → Special app access → Install unknown apps**.
   - Choose **Files**, **Chrome**, or **Drive** (whichever you used).
   - Turn on **Allow from this source**.
3. Open the APK and tap **Install**.

---

## 4. Enable Restricted settings (required for Accessibility)

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

## 5. Use it

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
| Watch not updating | Confirm Bridge log shows `Garmin link ready` and `Center ← …`. Install **both** the latest APK and the matching watch file from this release |
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
