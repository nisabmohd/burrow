package app.burrow.money.data

import androidx.core.graphics.scale
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Receipts and bills kept with a transaction (76): copied into app storage
 * (`files/attachments/<txnId>/`), photos shrunk to ≤ 1600 px JPEG. Never leaves the phone except
 * inside an encrypted backup.
 */
class Attachments(private val context: Context, private val db: BurrowDatabase) {
    /** A file picked or shot while the form is open, before the transaction has an id. */
    data class Pending(val file: File, val name: String, val mime: String, val bytes: Long)

    private val pendingDir get() = File(context.cacheDir, "pending-attachments").apply { mkdirs() }

    /** Copies [uri] into a pending file (shrinking photos). */
    suspend fun take(uri: Uri): Pending? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "file"
            if (mime.startsWith("image/")) {
                val out = File(pendingDir, "${System.nanoTime()}.jpg")
                shrink(uri, out)
                Pending(out, name.substringBeforeLast('.') + ".jpg", "image/jpeg", out.length())
            } else {
                val out = File(pendingDir, "${System.nanoTime()}-${safe(name)}")
                resolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } ?: return@runCatching null
                Pending(out, name, mime, out.length())
            }
        }.getOrNull()
    }

    /** A new, empty file for the camera to write into, and its content Uri. */
    fun cameraTarget(): Pair<File, Uri> {
        val f = File(pendingDir, "camera-${System.nanoTime()}.jpg")
        f.createNewFile()
        return f to androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", f)
    }

    /** After the camera wrote [raw]: shrink it in place. */
    suspend fun fromCamera(raw: File): Pending? = withContext(Dispatchers.IO) {
        if (raw.length() == 0L) return@withContext null.also { raw.delete() }
        val out = File(pendingDir, "photo-${System.nanoTime()}.jpg")
        runCatching { shrink(Uri.fromFile(raw), out) }.getOrNull() ?: return@withContext null
        raw.delete()
        Pending(out, "photo-${java.time.LocalDate.now()}.jpg", "image/jpeg", out.length())
    }

    /** Moves pending files under the saved transaction. */
    suspend fun keep(txnId: Long, pending: List<Pending>) = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "$DIR/$txnId").apply { mkdirs() }
        pending.forEach { p ->
            val target = uniqueIn(dir, safe(p.name))
            if (!p.file.renameTo(target)) { p.file.copyTo(target, overwrite = true); p.file.delete() }
            db.extras().attach(AttachmentEntity(txnId = txnId, path = "$DIR/$txnId/${target.name}", name = p.name, mime = p.mime, bytes = target.length()))
        }
    }

    suspend fun remove(a: AttachmentEntity) = withContext(Dispatchers.IO) {
        db.extras().detach(a)
        File(context.filesDir, a.path).delete()
    }

    fun file(a: AttachmentEntity) = File(context.filesDir, a.path)

    /** Files and rows of transactions that no longer exist (deleted, and Undo has passed). */
    suspend fun cleanOrphans() = withContext(Dispatchers.IO) {
        val ids = db.backup().txns().map { it.id }.toSet()
        db.backup().attachments().filter { it.txnId !in ids }.forEach { remove(it) }
        File(context.filesDir, DIR).listFiles()?.filter { it.name.toLongOrNull()?.let { id -> id !in ids } == true }?.forEach { it.deleteRecursively() }
        pendingDir.listFiles()?.forEach { it.delete() }
    }

    private fun shrink(uri: Uri, out: File) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PX) sample *= 2
        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("not an image")
        val scale = MAX_PX.toFloat() / maxOf(bmp.width, bmp.height)
        val sized = if (scale < 1f) bmp.scale((bmp.width * scale).toInt(), (bmp.height * scale).toInt()) else bmp
        out.outputStream().use { sized.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }

    private fun safe(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60).ifEmpty { "file" }

    private fun uniqueIn(dir: File, name: String): File {
        var f = File(dir, name)
        var i = 1
        while (f.exists()) f = File(dir, name.substringBeforeLast('.') + "-${i++}." + name.substringAfterLast('.', "bin"))
        return f
    }

    companion object {
        const val DIR = "attachments"
        const val MAX_PX = 1600
    }
}
