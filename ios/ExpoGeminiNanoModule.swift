import ExpoModulesCore
import Foundation

#if canImport(FoundationModels)
import FoundationModels
#endif

public final class ExpoGeminiNanoModule: Module {
  public func definition() -> ModuleDefinition {
    Name("ExpoGeminiNano")

    AsyncFunction("getStatus") { () -> [String: Any] in
      Self.status()
    }

    AsyncFunction("warmup") { (promise: Promise) in
      #if canImport(FoundationModels)
      guard #available(iOS 26.0, *) else {
        promise.resolve(Self.unsupportedStatus("Apple Foundation Models requires iOS 26 or newer."))
        return
      }

      Task {
        do {
          try await AppleFoundationModelClient.shared.warmup()
          promise.resolve(Self.status())
        } catch {
          promise.reject("ERR_GEMINI_NANO_WARMUP", error.localizedDescription)
        }
      }
      #else
      promise.resolve(Self.unsupportedStatus("Apple Foundation Models requires Xcode 26 or newer."))
      #endif
    }

    AsyncFunction("prompt") { (payload: [String: Any], promise: Promise) in
      guard let text = (payload["text"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
            !text.isEmpty else {
        promise.reject("ERR_GEMINI_NANO_INVALID_PROMPT", "prompt requires non-empty text.")
        return
      }

      #if canImport(FoundationModels)
      guard #available(iOS 26.0, *) else {
        promise.reject("ERR_GEMINI_NANO_UNSUPPORTED", "Apple Foundation Models requires iOS 26 or newer.")
        return
      }

      Task {
        do {
          let response = try await AppleFoundationModelClient.shared.prompt(text)
          promise.resolve([
            "text": response,
            "status": "available"
          ])
        } catch {
          promise.reject("ERR_GEMINI_NANO_PROMPT", error.localizedDescription)
        }
      }
      #else
      promise.reject("ERR_GEMINI_NANO_UNSUPPORTED", "Apple Foundation Models requires Xcode 26 or newer.")
      #endif
    }

    AsyncFunction("describeImage") { (payload: [String: Any], promise: Promise) in
      guard let uri = payload["uri"] as? String, !uri.isEmpty else {
        promise.reject("ERR_GEMINI_NANO_INVALID_URI", "describeImage requires a uri.")
        return
      }

      #if canImport(FoundationModels) && compiler(>=6.4)
      guard #available(iOS 27.0, *) else {
        promise.reject("ERR_GEMINI_NANO_UNSUPPORTED", "On-device image description requires iOS 27 or newer.")
        return
      }

      do {
        let imageURL = try Self.resolveImageURL(uri)
        Task {
          do {
            let description = try await AppleFoundationModelClient.shared.describeImage(at: imageURL)
            promise.resolve([
              "description": description,
              "status": "available",
              "baseModelName": "Apple Foundation Models"
            ])
          } catch {
            promise.reject("ERR_GEMINI_NANO_IMAGE_DESCRIPTION", error.localizedDescription)
          }
        }
      } catch {
        promise.reject("ERR_GEMINI_NANO_INVALID_URI", error.localizedDescription)
      }
      #else
      promise.reject("ERR_GEMINI_NANO_UNSUPPORTED", "On-device image description requires Xcode 27 and iOS 27 or newer.")
      #endif
    }
  }

  private static func status() -> [String: Any] {
    #if canImport(FoundationModels)
    guard #available(iOS 26.0, *) else {
      return unsupportedStatus("Apple Foundation Models requires iOS 26 or newer.")
    }

    let modelStatus = foundationModelStatus()
    var result: [String: Any] = [
      "platform": "ios",
      "imageDescription": imageDescriptionStatus(modelStatus: modelStatus.status),
      "prompt": modelStatus.status,
      "baseModelName": "Apple Foundation Models"
    ]
    if let message = modelStatus.message {
      result["message"] = message
    }
    return result
    #else
    return unsupportedStatus("Apple Foundation Models requires Xcode 26 or newer.")
    #endif
  }

  private static func unsupportedStatus(_ message: String) -> [String: Any] {
    [
      "platform": "ios",
      "imageDescription": "unsupported",
      "prompt": "unsupported",
      "message": message
    ]
  }

  #if canImport(FoundationModels)
  @available(iOS 26.0, *)
  private static func foundationModelStatus() -> (status: String, message: String?) {
    switch SystemLanguageModel.default.availability {
    case .available:
      return ("available", nil)
    case .unavailable(.deviceNotEligible):
      return ("unsupported", "This device does not support Apple Intelligence.")
    case .unavailable(.appleIntelligenceNotEnabled):
      return ("unavailable", "Apple Intelligence is not enabled.")
    case .unavailable(.modelNotReady):
      return ("downloading", "The Apple Intelligence model is not ready yet.")
    case .unavailable:
      return ("unknown", "Apple Foundation Models is unavailable for an unknown reason.")
    }
  }

  @available(iOS 26.0, *)
  private static func imageDescriptionStatus(modelStatus: String) -> String {
    #if compiler(>=6.4)
    if #available(iOS 27.0, *) {
      return modelStatus
    }
    #endif
    return "unsupported"
  }
  #endif

  #if canImport(FoundationModels) && compiler(>=6.4)
  @available(iOS 27.0, *)
  private static func resolveImageURL(_ uri: String) throws -> URL {
    let url: URL
    if let parsedURL = URL(string: uri), parsedURL.scheme != nil {
      url = parsedURL
    } else {
      url = URL(fileURLWithPath: uri)
    }

    guard url.isFileURL else {
      throw ExpoGeminiNanoError("iOS image description currently supports local file URLs only.")
    }
    guard FileManager.default.fileExists(atPath: url.path) else {
      throw ExpoGeminiNanoError("Image file does not exist at uri.")
    }
    return url
  }
  #endif
}

private struct ExpoGeminiNanoError: LocalizedError {
  let message: String

  init(_ message: String) {
    self.message = message
  }

  var errorDescription: String? {
    message
  }
}

#if canImport(FoundationModels)
@available(iOS 26.0, *)
private actor AppleFoundationModelClient {
  static let shared = AppleFoundationModelClient()

  private var warmedSession: LanguageModelSession?

  func warmup() throws {
    try requireAvailable()
    let session = LanguageModelSession()
    session.prewarm()
    warmedSession = session
  }

  func prompt(_ text: String) async throws -> String {
    try requireAvailable()
    let session = nextSession()
    return try await session.respond(to: text).content.trimmingCharacters(in: .whitespacesAndNewlines)
  }

  #if compiler(>=6.4)
  @available(iOS 27.0, *)
  func describeImage(at imageURL: URL) async throws -> String {
    try requireAvailable()
    let session = nextSession()
    let response = try await session.respond {
      "Describe this image clearly and concisely."
      Attachment(imageURL)
    }
    return response.content.trimmingCharacters(in: .whitespacesAndNewlines)
  }
  #endif

  private func nextSession() -> LanguageModelSession {
    if let warmedSession {
      self.warmedSession = nil
      return warmedSession
    }
    return LanguageModelSession()
  }

  private func requireAvailable() throws {
    switch SystemLanguageModel.default.availability {
    case .available:
      return
    case .unavailable(.deviceNotEligible):
      throw ExpoGeminiNanoError("This device does not support Apple Intelligence.")
    case .unavailable(.appleIntelligenceNotEnabled):
      throw ExpoGeminiNanoError("Apple Intelligence is not enabled.")
    case .unavailable(.modelNotReady):
      throw ExpoGeminiNanoError("The Apple Intelligence model is not ready yet.")
    case .unavailable:
      throw ExpoGeminiNanoError("Apple Foundation Models is unavailable for an unknown reason.")
    }
  }
}
#endif
