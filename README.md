# expo-gemini-nano

Expo module bridge for Android ML Kit GenAI APIs backed by Gemini Nano / AICore.

This package is intentionally small. It exposes local Android Gemini Nano status checks, image description, warmup, and text prompt generation to Expo development builds. It does not include camera UI, object detection, cloud fallbacks, or app-specific repair flows.

## Status

Experimental. The Android ML Kit GenAI APIs used here are early and device support varies by Android version, AICore availability, model download state, and Google Play services configuration.

## Platform Support

- Android: supported in Expo development builds and native builds.
- iOS: unsupported.
- Web: unsupported.
- Expo Go: unsupported because this package includes native Android code.

## Installation

```sh
npm install expo-gemini-nano
```

Then rebuild your native app:

```sh
npx expo run:android
```

## API

```ts
import ExpoGeminiNano from "expo-gemini-nano";

const status = await ExpoGeminiNano.getStatus();

await ExpoGeminiNano.warmup();

const image = await ExpoGeminiNano.describeImage({
  uri: "file:///path/to/image.jpg"
});

const response = await ExpoGeminiNano.prompt({
  text: "Summarize the likely repair steps for a leaking faucet."
});
```

### `getStatus()`

Returns feature status for image description and prompt generation:

```ts
type GeminiNanoFeatureStatus =
  | "available"
  | "downloadable"
  | "downloading"
  | "unavailable"
  | "unsupported"
  | "unknown";
```

### `warmup()`

Prepares the image description inference engine when available and warms prompt generation when supported. Apps should call this before a latency-sensitive flow, not on every render.

### `describeImage({ uri })`

Runs local image description for a file URI and returns:

```ts
type GeminiNanoImageDescription = {
  description: string;
  status: GeminiNanoFeatureStatus;
  baseModelName?: string;
};
```

### `prompt({ text })`

Runs local text prompt generation.

## App Responsibilities

Apps should own:

- Camera capture and image compression.
- Cloud or mock fallbacks.
- User-facing copy for unsupported devices.
- Retry and backoff policies.
- Safety warnings for advice generated from model output.

## Development

This repo contains only the Expo module. Use a separate Expo app to dogfood native behavior.

```sh
npm run typecheck
```
