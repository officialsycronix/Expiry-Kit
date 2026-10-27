EXPIRY-Kit — package-keyed SINGLE FILE update system
======================================================

Ek hi file (versions.txt) = sab apps ek jagah manage.
App apna package name padke apni line dhundhta hai.

========================================
versions.txt FORMAT
========================================
https://raw.githubusercontent.com/officialsycronix/Expiry-Kit/main/versions.txt

  # comment allowed
  com.predictapps.Mobiletricks=238
  com.some.otherapp=12
  com.daily.newapp=1

Rule:
  app ka versionCode < line value  -> FORCED UPDATE DIALOG
  app ka versionCode >= line value -> normal (kuch nahi)
  line missing / value 0 ya -1      -> us app ke liye OFF
  fetch fail / offline              -> normal (block nahi)

========================================
PEHLI BAAR — naya app inject
========================================
1. APK inject karo (classes9.dex + logo + attach line)
   — DEX SAB APPS ME SAME, per-app rebuild NAHI
2. versions.txt me line ADD karo:
   <naya.package.name>=<uska abhi ka versionCode>
   git push
3. Bas. Us app me dialog OFF (barabar hai).

Package name nikalne ka:
  aapt2 dump badging app.apk | grep package

========================================
UPDATE DENI HAI (release ke time)
========================================
1. Naya APK banao — manifest versionCode BADHAO (238 -> 239)
2. versions.txt me SIRF USKI LINE badlo:
   com.predictapps.Mobiletricks=239
   git push
3. Purane users: 238 < 239 -> DIALOG ✅
   Baaki sab apps UNTOUCHED -> unme kuch nahi ✅

CDN cache ~5 min — turant nahi, thodi der me live.

========================================
DAILY UPLOAD FLOW (staggered apps)
========================================
Day 1:  app A inject + line A=1 push
Day 2:  app B inject + line B=1 push
...
Day 30: sirf app A update -> sirf line A badli
        -> sirf A me dialog, B..Z pe kuch nahi

Fresh app = uski line barabar/missing -> never premature dialog.

========================================
FILES
========================================
  classes9.dex              -> inject (sab apps me same)
  modkit_logo.jpg           -> assets/ (skip agar pehle se hai)
  src/.../ExpiryKit.java    -> source (VERSION_URL config)
  versions.txt              <- THE single control file
  INJECT.txt                -> MT steps
