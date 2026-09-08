# Remote Control Companion
1. Deploy the Kontrol folder to Vercel.
2. Add `UPSTASH_REDIS_REST_URL` and `UPSTASH_REDIS_REST_TOKEN` in Vercel Environment Variables.
3. Build the Android project with the included GitHub Action.
4. Install the APK, open it, enter the HTTPS Vercel URL, then tap **Simpan & Mulai**.

Supported commands: flashlight on/off and open TikTok. The app runs visibly as a foreground service and requires normal Android permissions.
