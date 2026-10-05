package com.example.recoverx.scanner

import android.content.Context
import android.net.Uri
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest

/** Staged identity: cheap sampled hash first, full SHA-256 only to confirm a match. */
object ContentFingerprint {
    private const val SAMPLE = 64 * 1024

    /** size + first/middle/last 64 KB. Bounded I/O regardless of file size. */
    private fun quick(context: Context, uri: Uri): String? = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).use { fis ->
                val ch = fis.channel
                val total = ch.size()
                if (total <= 0L) return@use null
                val md = MessageDigest.getInstance("SHA-256")
                md.update(total.toString().toByteArray())
                val buf = ByteBuffer.allocate(SAMPLE)
                readAt(ch, 0L, buf, md)
                if (total > SAMPLE) readAt(ch, total - SAMPLE, buf, md)
                if (total > SAMPLE * 3L) readAt(ch, total / 2 - SAMPLE / 2, buf, md)
                hex(md.digest())
            }
        }
    } catch (e: Exception) { null }

    fun full(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { s ->
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(SAMPLE)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
            hex(md.digest())
        }
    } catch (e: Exception) { null }

    /** Same function is used for existing files and candidates so results are comparable. */
    fun identity(context: Context, uri: Uri): String? = quick(context, uri) ?: full(context, uri)

    private fun readAt(ch: FileChannel, pos: Long, buf: ByteBuffer, md: MessageDigest) {
        buf.clear()
        var p = pos
        while (buf.hasRemaining()) {
            val n = ch.read(buf, p)
            if (n <= 0) break
            p += n
        }
        buf.flip()
        md.update(buf)
    }

    private fun hex(bytes: ByteArray): String {
        val c = "0123456789abcdef"
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) { sb.append(c[(b.toInt() shr 4) and 0xF]); sb.append(c[b.toInt() and 0xF]) }
        return sb.toString()
    }
}