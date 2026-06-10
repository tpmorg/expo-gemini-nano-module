package expo.modules.gemininano

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.imagedescription.ImageDescription
import com.google.mlkit.genai.imagedescription.ImageDescriptionRequest
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.io.File
import java.net.URI
import kotlinx.coroutines.runBlocking

class ExpoGeminiNanoModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("ExpoGeminiNano")

    AsyncFunction("getStatus") {
      getStatus()
    }

    AsyncFunction("warmup") {
      val context = appContext.reactContext ?: throw IllegalStateException("React context is not available.")

      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return@AsyncFunction unsupportedStatus("Android API 26 or newer is required.")
      }

      val imageDescriber = ImageDescription.getClient(ImageDescriberOptions.builder(context).build())
      try {
        val status = imageDescriber.checkFeatureStatus().get()
        if (status == FeatureStatus.AVAILABLE || status == FeatureStatus.DOWNLOADABLE) {
          imageDescriber.prepareInferenceEngine().get()
        }
      } finally {
        imageDescriber.close()
      }

      val generator = Generation.getClient()
      try {
        runBlocking {
          if (generator.checkStatus() == FeatureStatus.AVAILABLE) {
            generator.warmup()
          }
        }
      } finally {
        generator.close()
      }

      getStatus()
    }

    AsyncFunction("describeImage") { payload: Map<String, Any?> ->
      val context = appContext.reactContext ?: throw IllegalStateException("React context is not available.")
      val uri = payload["uri"] as? String ?: throw IllegalArgumentException("describeImage requires a uri.")

      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        throw IllegalStateException("Gemini Nano image description requires Android API 26 or newer.")
      }

      val bitmap = decodeBitmap(uri)
      val imageDescriber = ImageDescription.getClient(ImageDescriberOptions.builder(context).build())

      try {
        val status = imageDescriber.checkFeatureStatus().get()
        if (status == FeatureStatus.UNAVAILABLE) {
          throw IllegalStateException("Gemini Nano image description is unavailable on this device.")
        }

        val request = ImageDescriptionRequest.builder(bitmap).build()
        val result = imageDescriber.runInference(request).get()
        val description = result.description.trim()

        mapOf(
          "description" to description,
          "status" to statusToString(status),
          "baseModelName" to runCatching { imageDescriber.baseModelName.get() }.getOrNull()
        )
      } finally {
        imageDescriber.close()
      }
    }

    AsyncFunction("prompt") { payload: Map<String, Any?> ->
      val text = (payload["text"] as? String)?.trim()
        ?: throw IllegalArgumentException("prompt requires text.")

      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        throw IllegalStateException("Gemini Nano prompt generation requires Android API 26 or newer.")
      }

      val generator = Generation.getClient()
      try {
        val promptResult = runBlocking {
          val status = generator.checkStatus()
          if (status == FeatureStatus.UNAVAILABLE) {
            throw IllegalStateException("Gemini Nano Prompt API is unavailable on this device.")
          }

          val request = GenerateContentRequest.Builder(TextPart(text)).build()
          val response = generator.generateContent(request)
          val candidateText = response.candidates.firstOrNull()?.text?.trim().orEmpty()

          mapOf(
            "text" to candidateText,
            "status" to statusToString(status)
          )
        }

        promptResult
      } finally {
        generator.close()
      }
    }
  }

  private fun getStatus(): Map<String, Any?> {
    val context = appContext.reactContext

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return unsupportedStatus("Android API 26 or newer is required.")
    }

    if (context == null) {
      return unsupportedStatus("React context is not available.")
    }

    val imageDescriber = ImageDescription.getClient(ImageDescriberOptions.builder(context).build())
    val imageStatus = runCatching {
      statusToString(imageDescriber.checkFeatureStatus().get())
    }.getOrElse { "unknown" }
    val baseModelName = runCatching { imageDescriber.baseModelName.get() }.getOrNull()
    imageDescriber.close()

    val generator = Generation.getClient()
    val promptStatus = runCatching {
      runBlocking { statusToString(generator.checkStatus()) }
    }.getOrElse { "unknown" }
    generator.close()

    return mapOf(
      "platform" to "android",
      "imageDescription" to imageStatus,
      "prompt" to promptStatus,
      "baseModelName" to baseModelName
    )
  }

  private fun unsupportedStatus(message: String): Map<String, Any?> =
    mapOf(
      "platform" to "android",
      "imageDescription" to "unsupported",
      "prompt" to "unsupported",
      "message" to message
    )

  private fun decodeBitmap(uriString: String): Bitmap {
    val normalizedUri = Uri.parse(uriString)
    val path = when (normalizedUri.scheme) {
      "file" -> normalizedUri.path
      null -> uriString
      else -> URI(uriString).path
    } ?: throw IllegalArgumentException("Could not resolve image uri.")

    val file = File(path)
    if (!file.exists()) {
      throw IllegalArgumentException("Image file does not exist at uri.")
    }

    return BitmapFactory.decodeFile(file.absolutePath)
      ?: throw IllegalArgumentException("Could not decode image bitmap.")
  }

  private fun statusToString(@FeatureStatus status: Int): String =
    when (status) {
      FeatureStatus.AVAILABLE -> "available"
      FeatureStatus.DOWNLOADABLE -> "downloadable"
      FeatureStatus.DOWNLOADING -> "downloading"
      FeatureStatus.UNAVAILABLE -> "unavailable"
      else -> "unknown"
    }

}
