# SubDL automatic English subtitles

The player now attempts to search for an English subtitle when a video starts and adds a downloaded subtitle when SubDL returns a direct subtitle file.

## Configure your API key locally

Do not put the API key in a source file or commit it to GitHub. Add this line to your **user-level** Gradle properties file:

- Linux/macOS: `~/.gradle/gradle.properties`
- Windows: `%USERPROFILE%\.gradle\gradle.properties`

```properties
SUBDL_API_KEY=YOUR_SUBDL_API_KEY
```

Then build as usual:

```bash
./gradlew assembleDebug
```

If the key is not configured, automatic subtitle downloads remain disabled and video playback continues normally.

## Notes

- Requires internet access and a working SubDL API key.
- Subtitle availability and exact matching depend on SubDL's catalog.
- The integration is best-effort: no matching subtitle or a network/API error should not stop video playback.
- A key typed into a chat or other shared location should be rotated if you believe it may be exposed.
