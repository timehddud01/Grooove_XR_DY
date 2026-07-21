# Music recognition proxy

This zero-dependency Node service accepts a phone WAV capture at `POST /v1/music-recognitions` and forwards it to ACRCloud. It validates WAV MIME, size and duration, limits each anonymous installation ID to five requests per minute, applies a provider timeout, selects the best canonical match from multiple candidates, and normalizes provider failures into typed JSON errors.

## Run

Set the credentials shown in your ACRCloud project in the backend runtime secret store, then run:

```powershell
$env:ACRCLOUD_HOST = 'identify-ap-southeast-1.acrcloud.com'
$env:ACRCLOUD_ACCESS_KEY = '<project-access-key>'
$env:ACRCLOUD_ACCESS_SECRET = '<project-access-secret>'
npm start
```

Set `music_recognition_proxy_url` in `DisplayAccess/local.properties` to the reachable backend URL. Android Emulator uses `http://10.0.2.2:8787` by default. A physical phone must use the computer's reachable LAN URL or an HTTPS deployment.

## Privacy and secrets

- ACRCloud credentials are read only from backend environment variables; they are never returned to or embedded in the Android app.
- Audio exists only in request memory while it is validated and forwarded. It is not written to disk or retained after the request.
- Request bodies, file names, and audio bytes are never logged. The only client identifier is a random app-install UUID used for rate limiting.
- Deploy behind HTTPS outside a local development network. Production rate limiting should use a shared store when running more than one proxy instance.

Run `npm test` to verify request signing, canonical candidate selection, no-match, input validation, rate limiting, and typed provider errors.
