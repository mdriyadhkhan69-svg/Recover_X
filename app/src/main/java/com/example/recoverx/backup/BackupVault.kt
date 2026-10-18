package com.example.recoverx.backup

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.example.recoverx.scanner.ScanAuthorization
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream

/** Encrypted storage for backup copies. Decrypting requires an active scan authorization. */
object BackupVault {
    private const val VIEW_DIR = "backup_view"

    data class Header(val name: String, val size: Long)

    private fun enc(c: Context, f: File): EncryptedFile {
        val key = MasterKey.Builder(c.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedFile.Builder(
            c.applicationContext, f, key,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }

    /** Writes [name + size + content] encrypted to dest. dest must not exist. */
    fun encryptFrom(c: Context, input: InputStream, name: String, size: Long, dest: File) {
        if (dest.exists()) dest.delete()
        enc(c, dest).openFileOutput().use { out ->
            val dos = DataOutputStream(out)
            val nb = name.toByteArray(Charsets.UTF_8)
            dos.writeInt(nb.size)
            dos.write(nb)
            dos.writeLong(size)
            input.copyTo(dos)
            dos.flush()
        }
    }

    private fun readHeader(dis: DataInputStream): Header {
        val len = dis.readInt()
        require(len in 0..2048) { "bad header" }
        val nb = ByteArray(len)
        dis.readFully(nb)
        return Header(String(nb, Charsets.UTF_8), dis.readLong())
    }

    fun readHeader(c: Context, f: File): Header? {
        ScanAuthorization.requireAuthorized()
        return try {
            enc(c, f).openFileInput().use { readHeader(DataInputStream(it)) }
        } catch (e: SecurityException) { throw e
        } catch (e: Exception) { null }
    }

    /** Decrypts into the temporary view cache. Returns the plain temp file. */
    fun decryptToView(c: Context, f: File): File? {
        ScanAuthorization.requireAuthorized()
        return try {
            val dir = File(c.cacheDir, VIEW_DIR).apply { mkdirs() }
            enc(c, f).openFileInput().use { ins ->
                val dis = DataInputStream(ins)
                val h = readHeader(dis)
                val safe = h.name.replace(Regex("[^A-Za-z0-9._\\- ]"), "_")
                val out = File(dir, f.name.removeSuffix(".bak") + "__" + safe)
                out.outputStream().use { dis.copyTo(it) }
                out
            }
        } catch (e: SecurityException) { throw e
        } catch (e: Exception) { null }
    }

    fun clearViewCache(c: Context) {
        File(c.cacheDir, VIEW_DIR).deleteRecursively()
    }
}