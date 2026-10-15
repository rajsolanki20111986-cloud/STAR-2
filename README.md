# Star — Android Voice & Chat Assistant

Star is an Android app with Gemini-powered live voice conversation, text chat, and configurable voice output.

## Build APK on GitHub
1. Push to `main` or run **Build APK** from the Actions tab.
2. Open the successful workflow run and download the `Star-debug-apk` artifact.

## First run
1. Open Settings and add your Gemini API key.
2. Choose a supported voice.
3. Open Voice and tap the central control to start a live conversation, or use Chat for text and speech input.

Star does not include accessibility-based screen reading, tapping, typing, app launching, or system/media control. Android requires a foreground-service notification while the microphone session is active.

## Project notes
- `Persona.kt`: Star's personality prompt.
- `ChatEngine.kt`: text chat API and user-friendly API error handling.
- `LiveSession.kt` and `AudioStreamer.kt`: live voice conversation and audio streaming.
