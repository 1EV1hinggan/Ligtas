# Ligtas sender: step by step

Why: the old app shipped `res/raw/service_account.json` inside the APK. Anyone could unzip the app and send fake alerts
to everyone. Now the key lives only on a small server. The app proves who is signed in; the server checks the roster
(role ADMIN) and sends. Free, no credit card (Vercel Hobby). Cloud Functions would need the Blaze (billing) plan.

## Part A: do today, in this order

1. **Revoke the old key.** Firebase Console > Project settings > Service accounts > "Manage service account permissions"
   (Google Cloud) > `firebase-adminsdk-fbsvc@ligtas-eb260...` > Keys tab > delete every key.
   Old app builds stop sending. That is expected. Treat the old key as stolen even if nobody used it yet.
2. **Check GitHub.** If the project was ever pushed, search the repo and its history for `service_account`. If it is
   there, the key is burned (step 1 already fixes this), and remove it from history or make the repo private.
3. **Make a new key for the server only.** Same page > Keys > Add key > JSON. Keep the file out of the Android project
   and out of Git. Never paste it into chat, a zip or a screenshot.
4. **Deploy the sender** (this folder, `sender/`):
   - Easiest: push the `sender` folder to a PRIVATE GitHub repo, then vercel.com > Add New Project > import it.
     (Or `npm i -g vercel`, then `vercel` inside the folder.)
   - Project > Settings > Environment Variables: name `FIREBASE_SERVICE_ACCOUNT`, value = the WHOLE contents of the
     new JSON file. Redeploy.
   - Your address is `https://<project>.vercel.app/api/send`.
5. **Change the app.**
   - Replace `FcmSender.java` with `android/FcmSender.java` and set `SEND_URL` to your address.
   - Delete `app/src/main/res/raw/service_account.json`.
   - In `app/build.gradle`, remove the `google-auth-library-oauth2-http` line. Keep okhttp and firebase-auth.
   - Replace `AndroidManifest.xml` with `android/AndroidManifest.xml` (it also turns backup off), or just set
     `android:allowBackup="false"` on `<application>` in yours.
   - Build > Clean Project, then run. Search the project for `service_account` (Ctrl+Shift+F): no results.
6. **Test:** send an alert as an admin. Then sign in as a non-admin account: sending must fail with "Admins only".

## What the server refuses
Not signed in; email not verified; not an ADMIN in `roster`; single-phone targets (only topics and topic conditions);
unknown fields; over-long text; more than 40 alerts a minute from one admin. The sender name shown on phones comes from the
roster, so it cannot be faked. Tests: `npm test` (no network), `node test_handler.js`.

## Campus check
An admin whose roster entry has a `campus` can only alert that campus. The check is in `lib/campus.js` and runs in
`api/send.js` after the admin check. Targets for another campus, or with no campus at all, are refused with 403.
Admins with no `campus` are not limited yet. Teacher topics (`teach_*`) carry no campus name, so they are allowed for now.
Tests: `npm test`.

## Part B: rename the package (after Part A works)
`com.example.a1st2` is a placeholder; Google Play and Firebase want your own, e.g. `ph.edu.batstate.ligtas`.
1. Android Studio > Project view > switch to "Project" > right-click `java/com/example` > Refactor > Rename > the
   package `a1st2` > rename package (not directory). Repeat for `example`, or use Refactor > Move to a new package.
   Studio updates the Java files and the manifest for you.
2. `app/build.gradle`: change `namespace` and `applicationId` to the new name.
3. Firebase Console > Project settings > Your apps > Add app > Android with the NEW package name and your debug
   SHA-1 (`./gradlew signingReport`). Download the new `google-services.json` into `app/`. Without this, Google
   sign-in and push stop working.
4. Uninstall the old app, run the new one. Phones must sign in again to re-join their topics.
5. Search the project for `com.example.a1st2` and `a1st2`: no results left (also check `firestore.rules`, strings and
   the console if they mention it).
