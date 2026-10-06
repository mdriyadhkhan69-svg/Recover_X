package com.example.recoverx.scanner

import android.net.Uri
import com.example.recoverx.model.ConfidenceLevel
import com.example.recoverx.model.FileCategory
import com.example.recoverx.model.LiveStatus
import com.example.recoverx.model.RecoveryConfidence
import com.example.recoverx.model.RecoverySourceKind
import com.example.recoverx.model.ScanSource
import com.example.recoverx.model.ScannedFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

/** Root-only JPEG carving. Without root, isRootAvailable() is false and nothing here runs. */
object RootCarver {

    private const val MIN_BYTES = 100 * 1024
    private const val MAX_BYTES = 12 * 1024 * 1024

    private val DEVICES = listOf(
        "/dev/block/by-name/userdata",
        "/dev/block/bootdevice/by-name/userdata",
        "/dev/block/mmcblk1"
    )

    private fun su(cmd: String): String = try {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out
    } catch (e: Exception) { "" }

    fun isRootAvailable(): Boolean = su("id").contains("uid=0")

    fun findDevice(): String? = DEVICES.firstOrNull { su("test -e $it && echo yes").contains("yes") }

    fun carve(
        device: String,
        outDir: File,
        maxFiles: Int,
        maxBytes: Long,
        shouldStop: () -> Boolean,
        onProgress: (bytesRead: Long, found: Int) -> Unit
    ): List<File> {
        outDir.deleteRecursively()
        outDir.mkdirs()
        val results = mutableListOf<File>()
        val proc = ProcessBuilder("su", "-c", "dd if=$device bs=1048576 2>/dev/null").start()
        try {
            val input = proc.inputStream
            val buf = ByteArray(1 shl 20)
            val cur = ByteArrayOutputStream()
            var inside = false
            var b0 = 0
            var b1 = 0
            var total = 0L
            while (!shouldStop() && total < maxBytes && results.size < maxFiles) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                for (i in 0 until n) {
                    val b = buf[i].toInt() and 0xFF
                    if (!inside) {
                        if (b0 == 0xFF && b1 == 0xD8 && b == 0xFF) {
                            inside = true
                            cur.reset()
                            cur.write(0xFF); cur.write(0xD8); cur.write(0xFF)
                        }
                    } else {
                        cur.write(b)
                        if (b1 == 0xFF && b == 0xD9 && cur.size() >= MIN_BYTES) {
                            val f = File(outDir, "carved_${results.size}.jpg")
                            f.writeBytes(cur.toByteArray())
                            results.add(f)
                            inside = false
                        } else if (cur.size() > MAX_BYTES) {
                            inside = false
                        }
                    }
                    b0 = b1
                    b1 = b
                }
                onProgress(total, results.size)
            }
        } finally {
            proc.destroy()
        }
        return results
    }

    fun toScanned(f: File): ScannedFile {
        val len = f.length()
        return ScannedFile(
            id = "carved-${f.absolutePath}",
            name = f.name,
            sizeLabel = String.format(Locale.US, "%.1f MB", len / 1048576.0),
            category = FileCategory.PHOTO,
            confidence = RecoveryConfidence.ON_DEVICE,
            uriString = Uri.fromFile(f).toString(),
            liveStatus = LiveStatus.POSSIBLY_RECOVERABLE,
            confidenceLevel = ConfidenceLevel.MEDIUM,
            sizeBytes = len,
            dedupeKey = "${f.name}-$len",
            source = ScanSource.FILESYSTEM,
            sourceKind = RecoverySourceKind.CARVED,
            evidence = "JPEG found by signature carving in raw storage (root). May be partial if the file was fragmented."
        )
    }
}