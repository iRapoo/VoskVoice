package xyz.quenix.voskvoice.speech

import android.content.Context
import java.io.File

/**
 * Where the models live on the watch: `/data/data/xyz.quenix.voskvoice/files/models/<code>`.
 *
 * Internal storage rather than `/sdcard/Android/data`, because on some watches (TicWatch Pro 3)
 * even adb is not allowed into `Android/data`. Debug builds can still get a model in with
 * `adb push` + `run-as` (see README).
 */
object ModelStore {

    fun root(context: Context): File = File(context.filesDir, "models")

    fun dir(context: Context, language: Language): File = File(root(context), language.code)

    /** A Vosk model folder always contains the acoustic model and its config. */
    fun isInstalled(context: Context, language: Language): Boolean {
        val dir = dir(context, language)
        return File(dir, "am/final.mdl").isFile && File(dir, "conf/model.conf").isFile
    }

    fun installed(context: Context): List<Language> = Language.entries.filter { isInstalled(context, it) }

    fun sizeMb(context: Context, language: Language): Long =
        dir(context, language).walkBottomUp().filter { it.isFile }.sumOf { it.length() } / (1024 * 1024)

    fun delete(context: Context, language: Language) {
        ModelCache.evict(language)
        dir(context, language).deleteRecursively()
    }
}
