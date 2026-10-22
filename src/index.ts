import { requireNativeModule } from "expo-modules-core";
import { Platform } from "react-native";

export type GeminiNanoFeatureStatus =
  | "available"
  | "downloadable"
  | "downloading"
  | "unavailable"
  | "unsupported"
  | "unknown";

export type GeminiNanoStatus = {
  platform: "android" | "ios" | "web" | "unknown";
  imageDescription: GeminiNanoFeatureStatus;
  prompt: GeminiNanoFeatureStatus;
  baseModelName?: string;
  message?: string;
};

export type GeminiNanoImageDescription = {
  description: string;
  status: GeminiNanoFeatureStatus;
  baseModelName?: string;
};

export type DescribeImagePayload = {
  uri: string;
};

export type PromptPayload = {
  text: string;
};

export type GeminiNanoPromptResponse = {
  text: string;
  status: GeminiNanoFeatureStatus;
};

type ExpoGeminiNanoNativeModule = {
  getStatus(): Promise<GeminiNanoStatus>;
  describeImage(payload: DescribeImagePayload): Promise<GeminiNanoImageDescription>;
  prompt(payload: PromptPayload): Promise<GeminiNanoPromptResponse>;
  warmup(): Promise<GeminiNanoStatus>;
};

const unsupportedMessage = "On-device generation is unavailable on this platform or the native module is not linked.";

const unsupportedModule: ExpoGeminiNanoNativeModule = {
  async getStatus() {
    return {
      platform: Platform.OS === "android" || Platform.OS === "ios" || Platform.OS === "web" ? Platform.OS : "unknown",
      imageDescription: "unsupported",
      prompt: "unsupported",
      message: unsupportedMessage
    };
  },
  async describeImage() {
    throw new Error("On-device image description is unavailable on this platform or the native module is not linked.");
  },
  async prompt() {
    throw new Error("On-device prompt generation is unavailable on this platform or the native module is not linked.");
  },
  async warmup() {
    return this.getStatus();
  }
};

function loadNativeModule(): ExpoGeminiNanoNativeModule {
  if (Platform.OS !== "android" && Platform.OS !== "ios") {
    return unsupportedModule;
  }

  try {
    return requireNativeModule<ExpoGeminiNanoNativeModule>("ExpoGeminiNano");
  } catch {
    return unsupportedModule;
  }
}

const nativeModule = loadNativeModule();

export default nativeModule;
