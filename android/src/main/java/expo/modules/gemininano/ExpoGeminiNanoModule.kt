package expo.modules.gemininano

import android.content.Context
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

      val bitmap = decodeBitmap(context, uri)
      val imageDescriber = ImageDescription.getClient(ImageDescriberOptions.builder(context).build())

      try {
        val status = imageDescriber.checkFeatureStatus().get()
        requireAvailable(status, "Gemini Nano image description")

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
          requireAvailable(status, "Gemini Nano Prompt API")

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

  private fun decodeBitmap(context: Context, uriString: String): Bitmap {
    val normalizedUri = Uri.parse(uriString)

    return when (normalizedUri.scheme) {
      "content" -> context.contentResolver.openInputStream(normalizedUri)?.use { inputStream ->
        BitmapFactory.decodeStream(inputStream)
      } ?: throw IllegalArgumentException("Could not decode image bitmap from content uri.")
      "file", null -> {
        val path = if (normalizedUri.scheme == "file") normalizedUri.path else uriString
        val file = File(path ?: throw IllegalArgumentException("Could not resolve image uri."))
        if (!file.exists()) {
          throw IllegalArgumentException("Image file does not exist at uri.")
        }

        BitmapFactory.decodeFile(file.absolutePath)
          ?: throw IllegalArgumentException("Could not decode image bitmap.")
      }
      else -> throw IllegalArgumentException("Image uri scheme is not supported. Use file:// or content://.")
    }
  }

  private fun statusToString(@FeatureStatus status: Int): String =
    when (status) {
      FeatureStatus.AVAILABLE -> "available"
      FeatureStatus.DOWNLOADABLE -> "downloadable"
      FeatureStatus.DOWNLOADING -> "downloading"
      FeatureStatus.UNAVAILABLE -> "unavailable"
      else -> "unknown"
    }

  private fun requireAvailable(@FeatureStatus status: Int, featureName: String) {
    when (status) {
      FeatureStatus.AVAILABLE -> return
      FeatureStatus.DOWNLOADABLE ->
        throw IllegalStateException("$featureName model is downloadable. Call warmup(), then retry when status is available.")
      FeatureStatus.DOWNLOADING ->
        throw IllegalStateException("$featureName model is downloading. Retry when status is available.")
      FeatureStatus.UNAVAILABLE ->
        throw IllegalStateException("$featureName is unavailable on this device.")
      else ->
        throw IllegalStateException("$featureName status is unknown.")
    }
  }

}
