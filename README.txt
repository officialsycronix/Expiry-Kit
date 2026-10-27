EXPIRY-Kit — remote version-check forced update dialog
=======================================================

Dialog conditionally dikhta hai:
  local versionCode < remote versionCode  -> UPDATE REQUIRED (forced)
  local >= remote                         -> kuch nahi, app normal
  fetch fail / offline                    -> kuch nahi (block nahi)
  remote = "0" ya "-1"                    -> permanently off

========================================
UPDATE KAISE MANAGE KAROGE (workflow)
========================================

1. EK BAAR SETUP (release se pehle):
   - GitHub pe ek GIST banao (gist.github.com) — public
     File: latest.txt
     Content: apne CURRENT versionCode ka number (example: 239)
   - Uska RAW URL copy karo:
     https://gist.githubusercontent.com/YOUR_USER/GIST_ID/raw/latest.txt
   - ModKit.java me VERSION_URL = "paste karo ye raw URL"
   - Dex rebuild → APK me inject → release

2. JAB TAK LATEST VERSION HAI:
   latest.txt me purana versionCode pada hai (jo app me hai)
   -> local == remote -> dialog NAHI dikhta. App normal chalega.

3. JAB NAYA VERSION BANAYO (v2 ready hai):
   - Naya APK banao (expiry kit ke saath ya bina — jo chaho)
   - GitHub gist me latest.txt ka number BADAL DO
     (v2 ka versionCode, example: 240)
   - Purane apps: local 239 < remote 240 -> FORCED DIALOG ON ✅
   - User "Update Now" dabayega -> channel pe v2 milega
   - Naya install karke -> local 240 == remote 240 -> dialog OFF

4. KISI BHI VERSION KO TEMP OFF KARNA HO:
   latest.txt me "0" likh do -> sab jagah band.

VersionCode kaise nikale:
  aapt2 dump badging app.apk | grep package
  (ya MT Manager me APK info dekho)

URL HOSTING:
  - GitHub Gist (recommended, free, 30 sec me)
  - Ya koi bhi raw text URL (pastebin raw, apna server, etc.)
  - Bas text file ho jisme sirf ek number ho

========================================
FILE LIST
========================================
  classes9.dex       -> APK me add (rename next classesN)
  modkit_logo.jpg    -> assets/
  ModKit.java        -> source (VERSION_URL, text edit)
  INJECT.txt         -> MT steps
  Expiry-Kit-Preview.html -> browser preview
