# expo-gemini-nano

Expo module bridge for on-device generative AI using Android ML Kit GenAI / Gemini Nano and Apple Foundation Models.

This package is intentionally small. It exposes local model status checks, image description, warmup, and text prompt generation to Expo development builds. It does not include camera UI, object detection, cloud fallbacks, or app-specific repair flows.

## Status

Experimental. Device support varies by OS version, hardware eligibility, model download state, Android AICore availability, Google Play services configuration, and whether Apple Intelligence is enabled.

Despite the package name, iOS uses Apple's on-device model rather than Gemini Nano. The JavaScript API stays the same across both platforms.

Call `getStatus()` before running inference. `describeImage()` and `prompt()` run only when their corresponding feature status is `"available"`. On Android, `warmup()` can prepare a downloadable model. On iOS, the operating system manages model downloads; retry after `getStatus()` reports `"available"`.

The first npm releases are published under the `beta` dist tag while device support and the upstream ML Kit GenAI APIs continue to settle.

## Platform Support

- Android: supported in Expo development builds and native builds.
- iOS 26+: text prompt generation through Apple Foundation Models.
- iOS 27+: image description through Foundation Models multimodal prompting when built with Xcode 27+.
- Web: unsupported.
- Expo Go: unsupported because this package includes native code.

## Installation

```sh
npm install expo-gemini-nano@beta
```

Then rebuild your native app:

```sh
npx expo run:android
```

For iOS, use Xcode 26+ for text generation and Xcode 27+ for image description, then rebuild the native app:

```sh
npx expo run:ios
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

On web, Expo Go, unsupported OS/device combinations, or apps without the linked native module, this returns `"unsupported"` or `"unavailable"` statuses instead of throwing at import time. On iOS, `message` distinguishes an ineligible device, disabled Apple Intelligence, and a model that is not ready yet.

### `warmup()`

Prepares the platform inference engine when available. On iOS, the warmed Foundation Models session is consumed by the next request. Apps should call this before a latency-sensitive flow, not on every render.

### `describeImage({ uri })`

Runs local image description for Android `file://` or `content://` URIs and iOS local `file://` URIs. iOS image description requires iOS 27 and Xcode 27 or newer. It returns:

```ts
type GeminiNanoImageDescription = {
  description: string;
  status: GeminiNanoFeatureStatus;
  baseModelName?: string;
};
```

### `prompt({ text })`

Runs local text prompt generation through ML Kit GenAI on Android or Apple Foundation Models on iOS 26 and newer.

Returns:

```ts
type GeminiNanoPromptResponse = {
  text: string;
  status: GeminiNanoFeatureStatus;
};
```

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

Before publishing or testing an install from a local tarball, inspect the package contents:

```sh
npm run pack:dry-run
```

The published package should include the generated `build/` files, Android and iOS source, `expo-module.config.json`, README, and license.

## Publishing

Publish beta releases first:

```sh
npm login
npm run typecheck
npm run pack:dry-run
npm run release:beta
```

Consumers can install beta releases with `npm install expo-gemini-nano@beta`. Promote a stable release to the default `latest` tag only after dogfooding the packed package in a separate Expo app on supported Android and iOS hardware.
