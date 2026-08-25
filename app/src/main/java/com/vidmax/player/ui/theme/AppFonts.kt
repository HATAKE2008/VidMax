package com.vidmax.player.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.vidmax.player.R
import java.io.File

/**
 * Central manager for the app-wide font system.
 *
 * Supports three kinds of selections, encoded as a single string id:
 *  - [SYSTEM_DEFAULT]           → platform default (Roboto)
 *  - a built-in font id         → bundled TTFs under res/font
 *  - "custom:<fileName>"        → user-imported TTF/OTF stored in filesDir/fonts
 */
object AppFonts {

    private const val TAG = "AppFonts"

    const val SYSTEM_DEFAULT = "system_default"
    const val CUSTOM_PREFIX = "custom:"
    val IMPORTABLE_EXTENSIONS = setOf("ttf", "otf")

    /** Bundled open-source (SIL OFL / Apache) fonts shipped with the APK. */
    data class BuiltInFont(
        val id: String,
        val displayName: String,
        val regularRes: Int,
        val accentRes: Int?,
        val accentWeight: FontWeight = FontWeight.Bold
    )

    val builtInFonts: List<BuiltInFont> = listOf(
        BuiltInFont("poppins", "Poppins", R.font.poppins_regular, R.font.poppins_semibold, FontWeight.SemiBold),
        BuiltInFont("montserrat", "Montserrat", R.font.montserrat_regular, R.font.montserrat_bold),
        BuiltInFont("inter", "Inter", R.font.inter_regular, R.font.inter_bold),
        BuiltInFont("nunito", "Nunito", R.font.nunito_regular, R.font.nunito_bold),
        BuiltInFont("oswald", "Oswald", R.font.oswald_regular, R.font.oswald_bold),
        BuiltInFont("lora", "Lora", R.font.lora_regular, R.font.lora_bold),
    )

    private val builtInCache = HashMap<String, FontFamily>()
    private val customCache = HashMap<String, FontFamily>()

    /** Directory where imported fonts live (private app storage). */
    fun fontsDir(context: Context): File =
        File(context.filesDir, "fonts").apply { mkdirs() }

    /** Imported font files currently on disk, sorted by name. */
    fun importedFontFiles(context: Context): List<File> =
        fontsDir(context).listFiles { f ->
            f.isFile && f.extension.lowercase() in IMPORTABLE_EXTENSIONS
        }?.sortedBy { it.name.lowercase() } ?: emptyList()

    /** Human readable name for any font id. */
    fun displayNameFor(fontId: String): String = when {
        fontId == SYSTEM_DEFAULT -> "System Default"
        fontId.startsWith(CUSTOM_PREFIX) ->
            File(fontId.removePrefix(CUSTOM_PREFIX)).nameWithoutExtension.ifBlank { "Imported" }
        else -> builtInFonts.firstOrNull { it.id == fontId }?.displayName ?: "System Default"
    }

    /**
     * Copies a SAF-provided [uri] into the private fonts dir.
     * The file is validated by attempting to instantiate a [Typeface];
     * invalid payloads are removed again.
     *
     * @return the sanitized file name (without the custom: prefix).
     */
    fun importFont(context: Context, uri: Uri): Result<String> = runCatching {
        val resolver = context.contentResolver
        val originalName = queryDisplayName(context, uri) ?: "imported_font.ttf"

        val sourceExt = File(originalName).extension.lowercase()
        val ext = if (sourceExt in IMPORTABLE_EXTENSIONS) sourceExt else "ttf"
        val base = sanitizeName(File(originalName).nameWithoutExtension)

        val dir = fontsDir(context)
        var fileName = "$base.$ext"
        var counter = 1
        while (File(dir, fileName).exists()) {
            fileName = "${base}_$counter.$ext"
            counter++
        }

        val dest = File(dir, fileName)
        resolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Cannot open the selected file")

        try {
            Typeface.createFromFile(dest)
        } catch (t: Throwable) {
            dest.delete()
            throw IllegalArgumentException("Not a valid .ttf / .otf font file")
        }
        synchronized(customCache) { customCache.remove(CUSTOM_PREFIX + fileName) }
        fileName
    }

    /** Deletes an imported font file. Returns false if it did not exist. */
    fun deleteImportedFont(context: Context, fontId: String): Boolean {
        val name = fontId.removePrefix(CUSTOM_PREFIX)
        synchronized(customCache) { customCache.remove(fontId) }
        val file = File(fontsDir(context), name)
        return file.exists() && file.delete()
    }

    /** Resolves a persisted font id into a Compose [FontFamily]. */
    fun resolveFontFamily(context: Context, fontId: String): FontFamily = when {
        fontId == SYSTEM_DEFAULT -> FontFamily.Default
        fontId.startsWith(CUSTOM_PREFIX) ->
            synchronized(customCache) { customCache[fontId] } ?: loadCustom(context, fontId)
        else ->
            synchronized(builtInCache) { builtInCache[fontId] } ?: loadBuiltIn(fontId)
    }

    private fun loadBuiltIn(id: String): FontFamily {
        val font = builtInFonts.firstOrNull { it.id == id } ?: return FontFamily.Default
        val family = if (font.accentRes != null) {
            FontFamily(
                Font(font.regularRes, FontWeight.Normal),
                Font(font.accentRes, font.accentWeight)
            )
        } else {
            FontFamily(Font(font.regularRes, FontWeight.Normal))
        }
        synchronized(builtInCache) { builtInCache[id] = family }
        return family
    }

    private fun loadCustom(context: Context, fontId: String): FontFamily {
        val name = fontId.removePrefix(CUSTOM_PREFIX)
        val family = try {
            FontFamily(Typeface.createFromFile(File(fontsDir(context), name)))
        } catch (e: Exception) {
            Log.e(TAG, "Could not load imported font '$name'", e)
            FontFamily.Default
        }
        synchronized(customCache) { customCache[fontId] = family }
        return family
    }

    private fun sanitizeName(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().trim { it == '.' }
            .ifBlank { "imported_font" }
            .take(60)

    private fun queryDisplayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    } catch (e: Exception) {
        null
    }
}
