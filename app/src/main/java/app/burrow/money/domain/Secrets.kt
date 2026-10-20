package app.burrow.money.domain

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/*
 * The app lock PIN and encrypted backups (docs/DATA.md § Backup). Only javax.crypto, no library;
 * pure, unit-tested on the JVM.
 */

private val random = SecureRandom()

private fun pbkdf2(secret: CharArray, salt: ByteArray, iterations: Int, bits: Int): ByteArray =
    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(secret, salt, iterations, bits)).encoded

private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
private fun String.unhex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

/** The app lock PIN, kept as "salt:hash" (PBKDF2-HMAC-SHA256); the PIN itself is never stored. */
object PinHash {
    private const val ITERATIONS = 60_000

    fun make(pin: String): String {
        val salt = ByteArray(16).also(random::nextBytes)
        return salt.hex() + ":" + pbkdf2(pin.toCharArray(), salt, ITERATIONS, 256).hex()
    }

    fun matches(pin: String, stored: String): Boolean {
        val (salt, hash) = stored.split(':').takeIf { it.size == 2 } ?: return false
        val test = pbkdf2(pin.toCharArray(), salt.unhex(), ITERATIONS, 256)
        return java.security.MessageDigest.isEqual(test, hash.unhex())
    }
}

class WrongPassword : Exception("wrong backup password")
class NotABackup : Exception("not a Burrow backup")

/**
 * A backup file: "BURROW1", then a 16-byte salt, a 12-byte IV, and the gzipped JSON sealed with
 * AES-256-GCM under a key made from the password (PBKDF2-HMAC-SHA256, 200,000 rounds).
 */
object BackupCrypto {
    private val MAGIC = "BURROW1".toByteArray()
    private const val ITERATIONS = 200_000

    fun seal(plain: ByteArray, password: CharArray, iterations: Int = ITERATIONS): ByteArray {
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(pbkdf2(password, salt, iterations, 256), "AES"), GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + cipher.doFinal(gzip(plain))
    }

    fun open(file: ByteArray, password: CharArray, iterations: Int = ITERATIONS): ByteArray {
        if (file.size < MAGIC.size + 28 || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw NotABackup()
        val salt = file.copyOfRange(MAGIC.size, MAGIC.size + 16)
        val iv = file.copyOfRange(MAGIC.size + 16, MAGIC.size + 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(pbkdf2(password, salt, iterations, 256), "AES"), GCMParameterSpec(128, iv))
        val zipped = try {
            cipher.doFinal(file, MAGIC.size + 28, file.size - MAGIC.size - 28)
        } catch (_: AEADBadTagException) {
            throw WrongPassword()
        }
        return gunzip(zipped)
    }

    private fun gzip(b: ByteArray): ByteArray = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(b) } }.toByteArray()
    private fun gunzip(b: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(b)).use { it.readBytes() }
}

/** CSV of transactions (More › Export or import CSV): date, type, payee, amount, currency, category, account, note. */
object Csv {
    val HEADER = listOf("date", "type", "payee", "amount", "currency", "category", "account", "note")

    fun write(rows: List<List<String>>): String = (listOf(HEADER) + rows).joinToString("\r\n") { r -> r.joinToString(",") { quote(it) } } + "\r\n"

    private fun quote(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** Rows as maps by header name (lower case), quotes and line breaks inside quotes handled. */
    fun read(text: String): List<Map<String, String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        val t = text.removePrefix("﻿")
        while (i < t.length) {
            val ch = t[i]
            when {
                quoted && ch == '"' && i + 1 < t.length && t[i + 1] == '"' -> { cell.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                !quoted && ch == ',' -> { row.add(cell.toString()); cell.clear() }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && i + 1 < t.length && t[i + 1] == '\n') i++
                    row.add(cell.toString()); cell.clear()
                    if (row.any { it.isNotBlank() }) rows.add(row)
                    row = mutableListOf()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); if (row.any { it.isNotBlank() }) rows.add(row) }
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim().lowercase() }
        return rows.drop(1).map { r -> header.indices.associate { header[it] to (r.getOrNull(it) ?: "").trim() } }
    }
}
