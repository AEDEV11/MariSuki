package com.aesprt.marisuki.data

import android.content.Context
import android.net.Uri
import android.database.Cursor
import androidx.room.withTransaction
import com.aesprt.marisuki.data.local.PosDatabase
import com.aesprt.marisuki.data.auth.EncryptedSessionStore
import com.aesprt.marisuki.domain.auth.PosAccessControl
import com.aesprt.marisuki.domain.auth.PosPermission
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.aesprt.marisuki.BuildConfig
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Portable encrypted SQLite row backup. Import is one Room transaction and schema-checked. */
class EncryptedBackupManager(
    private val context: Context,
    private val database: PosDatabase,
    private val accessControl: PosAccessControl,
    private val sessions: EncryptedSessionStore,
    private val productImages: ProductImageStore,
) {
    suspend fun export(uri: Uri, passphrase: String) = withContext(Dispatchers.IO) {
        accessControl.requirePermission(PosPermission.CREATE_BACKUPS)
        validatePassphrase(passphrase)
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Could not open the selected backup file.")
        writeSnapshot(output, passphrase)
    }

    suspend fun createAutomaticBackup() = withContext(Dispatchers.IO) {
        val directory = automaticBackupDirectory().apply { mkdirs() }
        val passphrase = automaticPassphrase()
        val target = File(directory, "marisuki-auto-${System.currentTimeMillis()}.mskb")
        val temporary = File(directory, "${target.name}.tmp")
        writeSnapshot(FileOutputStream(temporary), passphrase)
        if (!temporary.renameTo(target)) throw IOException("Could not finish the automatic backup file.")
        directory.listFiles()?.filter { it.extension == "mskb" }?.sortedByDescending(File::lastModified)?.drop(AUTO_BACKUP_RETENTION)?.forEach(File::delete)
    }

    suspend fun hasAutomaticBackup(): Boolean = withContext(Dispatchers.IO) {
        automaticBackupDirectory().listFiles()?.any { it.extension == "mskb" } == true
    }

    suspend fun restoreLatestAutomaticBackup() = withContext(Dispatchers.IO) {
        accessControl.requirePermission(PosPermission.RESTORE_BACKUPS)
        val latest = automaticBackupDirectory().listFiles()?.filter { it.extension == "mskb" }?.maxByOrNull(File::lastModified)
            ?: throw IOException("No automatic backup is available on this device yet.")
        restoreInput(FileInputStream(latest), automaticPassphrase())
        sessions.clear()
    }

    private fun automaticBackupDirectory(): File = File(context.filesDir, if (BuildConfig.DEBUG) "automatic_backups_debug" else "automatic_backups")

    private fun automaticPassphrase(): String {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = (keyStore.getKey(autoKeyAlias(), null) as? SecretKey) ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(autoKeyAlias(), KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
        val secretFile = File(context.filesDir, if (BuildConfig.DEBUG) "automatic_backup_secret_debug.bin" else AUTO_SECRET_FILENAME)
        if (secretFile.exists()) {
            DataInputStream(BufferedInputStream(FileInputStream(secretFile))).use { stored ->
                val nonce = stored.readSafeBytes(NONCE_BYTES)
                val length = stored.readInt()
                if (length !in 16..128) throw IOException("The automatic backup key file is invalid.")
                val encryptedSecret = ByteArray(length).also { stored.readFully(it) }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
                return Base64.getEncoder().encodeToString(cipher.doFinal(encryptedSecret))
            }
        }
        val secret = ByteArray(32).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encryptedSecret = cipher.doFinal(secret)
        val temporarySecret = File(secretFile.parentFile, "${secretFile.name}.tmp")
        DataOutputStream(BufferedOutputStream(FileOutputStream(temporarySecret))).use { stored ->
            stored.writeInt(cipher.iv.size)
            stored.write(cipher.iv)
            stored.writeInt(encryptedSecret.size)
            stored.write(encryptedSecret)
        }
        if (!temporarySecret.renameTo(secretFile)) throw IOException("Could not save the automatic backup key.")
        return Base64.getEncoder().encodeToString(secret)
    }

    private suspend fun writeSnapshot(output: OutputStream, passphrase: String) {
        validatePassphrase(passphrase)
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, nonce))

        output.use { raw ->
            DataOutputStream(BufferedOutputStream(raw)).use { header ->
                header.write(MAGIC)
                header.writeInt(FORMAT_VERSION)
                header.writeInt(PBKDF_ITERATIONS)
                header.writeInt(salt.size)
                header.write(salt)
                header.writeInt(nonce.size)
                header.write(nonce)
                header.flush()
                val encrypted = CipherOutputStream(header, cipher)
                val payload = DataOutputStream(GZIPOutputStream(BufferedOutputStream(encrypted)))
                database.withTransaction {
                    val sqlite = database.openHelper.writableDatabase
                    payload.writeInt(sqlite.version)
                    val imageCursor = sqlite.query("SELECT DISTINCT imageFileName FROM products WHERE imageFileName IS NOT NULL ORDER BY imageFileName")
                    val imageNames = imageCursor.use { rows ->
                        buildList { while (rows.moveToNext()) add(rows.getString(0)) }
                    }
                    if (imageNames.size > MAX_BACKUP_IMAGE_COUNT) throw IOException("This store has too many product photos for one backup.")
                    payload.writeInt(imageNames.size)
                    var totalPhotoBytes = 0L
                    imageNames.forEach { filename ->
                        val bytes = productImages.readBackupImage(filename)
                        totalPhotoBytes += bytes.size
                        if (totalPhotoBytes > MAX_BACKUP_IMAGE_BYTES) throw IOException("Product photos exceed the backup size limit.")
                        payload.writeString(filename)
                        payload.writeInt(bytes.size)
                        payload.write(bytes)
                    }
                    payload.writeInt(TABLES.size)
                    TABLES.forEach { table ->
                        val cursor = sqlite.query("SELECT * FROM `$table`")
                        cursor.use { rows ->
                            payload.writeString(table)
                            payload.writeInt(rows.columnCount)
                            rows.columnNames.forEach { payload.writeString(it) }
                            val countCursor = sqlite.query("SELECT COUNT(*) FROM `$table`")
                            val rowCount = countCursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
                            payload.writeInt(rowCount)
                            while (rows.moveToNext()) {
                                for (column in 0 until rows.columnCount) payload.writeCell(rows, column)
                            }
                        }
                    }
                }
                payload.close()
            }
        }
    }

    suspend fun restore(uri: Uri, passphrase: String) = withContext(Dispatchers.IO) {
        accessControl.requirePermission(PosPermission.RESTORE_BACKUPS)
        validatePassphrase(passphrase)
        val raw = context.contentResolver.openInputStream(uri) ?: throw IOException("Could not open the selected backup file.")
        restoreInput(raw, passphrase)
        sessions.clear()
    }

    private suspend fun restoreInput(raw: InputStream, passphrase: String) {
        validatePassphrase(passphrase)
        raw.use { input ->
            val header = DataInputStream(BufferedInputStream(input))
            val magic = ByteArray(MAGIC.size)
            header.readFully(magic)
            if (!magic.contentEquals(MAGIC)) throw IOException("This is not a MariSuki backup.")
            val formatVersion = header.readInt()
            if (formatVersion !in SUPPORTED_FORMAT_VERSIONS) throw IOException("This backup format is not supported by this version of MariSuki.")
            if (header.readInt() != PBKDF_ITERATIONS) throw IOException("This backup uses unsupported encryption settings.")
            val salt = header.readSafeBytes(SALT_BYTES)
            val nonce = header.readSafeBytes(NONCE_BYTES)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, nonce))
            val encrypted = CipherInputStream(header, cipher)
            val payload = DataInputStream(GZIPInputStream(BufferedInputStream(encrypted)))
            val restoredPhotoNames = mutableListOf<String>()
            try {
                database.withTransaction {
                    val sqlite = database.openHelper.writableDatabase
                    val schemaVersion = payload.readInt()
                    if (formatVersion == 1) {
                        if (schemaVersion !in 3..4) throw IOException("This older backup was made with an unsupported database version.")
                    } else if (formatVersion == 2 && schemaVersion != 5) {
                        throw IOException("This backup was made with an unsupported database version.")
                    } else if (formatVersion == 3 && schemaVersion != sqlite.version) {
                        throw IOException("This backup was made with a different database version. Update or restore it with the matching app version.")
                    }
                    val imageNameMapping = if (formatVersion == 3) readProductImages(payload, restoredPhotoNames) else emptyMap()
                    val restoreTables = if (formatVersion == 1) TABLES_V1 else TABLES
                    val tableCount = payload.readInt()
                    if (tableCount != restoreTables.size) throw IOException("The backup is incomplete or has an unsupported schema.")
                    val columnsByTable = restoreTables.associateWith { table ->
                        sqlite.query("SELECT * FROM `$table` LIMIT 0").use { it.columnNames.toList() }
                    }
                    restoreTables.asReversed().forEach { table -> sqlite.execSQL("DELETE FROM `$table`") }
                    restoreTables.forEach { table ->
                        if (payload.readString() != table) throw IOException("The backup table list is invalid.")
                        val columnCount = payload.readInt()
                        val liveColumns = columnsByTable.getValue(table)
                        val expectedColumns = if (formatVersion == 1) {
                            liveColumns.filterNot { column ->
                                table == "sales" && (column == "salesChannel" || schemaVersion < 4 && column == "cashierAccountId") ||
                                    table == "audit_log" && schemaVersion < 4 && column == "actorAccountId" ||
                                    table == "products" && column == "imageFileName"
                            }
                        } else if (formatVersion == 2) {
                            liveColumns.filterNot { table == "products" && it == "imageFileName" }
                        } else liveColumns
                        if (columnCount !in 1..MAX_COLUMNS || columnCount != expectedColumns.size) {
                            throw IOException("The backup table layout is invalid.")
                        }
                        val columns = List(columnCount) { payload.readString() }
                        if (columns != expectedColumns) throw IOException("The backup schema does not match this app version.")
                        val rowCount = payload.readInt()
                        if (rowCount !in 0..MAX_ROWS) throw IOException("The backup contains an invalid number of rows.")
                        val sql = "INSERT INTO `$table` (${columns.joinToString(",") { "`$it`" }}) VALUES (${columns.joinToString(",") { "?" }})"
                        val statement = sqlite.compileStatement(sql)
                        try {
                            repeat(rowCount) {
                                statement.clearBindings()
                                columns.indices.forEach { index ->
                                    if (formatVersion == 3 && table == "products" && columns[index] == "imageFileName") {
                                        payload.readImageName(statement, index + 1, imageNameMapping)
                                    } else {
                                        payload.readCell(statement, index + 1)
                                    }
                                }
                                statement.executeInsert()
                            }
                        } finally {
                            statement.close()
                        }
                    }
                    if (payload.read() != -1) throw IOException("The backup contains unexpected trailing data.")
                    sqlite.compileStatement("INSERT INTO `audit_log` (`id`, `operation`, `targetId`, `details`, `occurredAtMillis`) VALUES (?, ?, ?, ?, ?)")
                        .use { statement ->
                            statement.bindString(1, java.util.UUID.randomUUID().toString())
                            statement.bindString(2, "BACKUP_RESTORED")
                            statement.bindNull(3)
                            statement.bindString(4, "Encrypted backup restored")
                            statement.bindLong(5, System.currentTimeMillis())
                            statement.executeInsert()
                        }
                }
            } catch (error: Throwable) {
                restoredPhotoNames.forEach(productImages::delete)
                throw error
            } finally {
                payload.close()
            }
        }
    }

    private fun readProductImages(payload: DataInputStream, writtenNames: MutableList<String>): Map<String, String> {
        val count = payload.readInt()
        if (count !in 0..MAX_BACKUP_IMAGE_COUNT) throw IOException("The backup contains an invalid number of product photos.")
        val mapping = LinkedHashMap<String, String>(count)
        var totalBytes = 0L
        repeat(count) {
            val sourceName = payload.readString()
            val extension = productImages.backupExtension(sourceName)
            if (mapping.containsKey(sourceName)) throw IOException("The backup contains a duplicate product photo.")
            val length = payload.readInt()
            if (length !in 1..ProductImageStore.MAX_IMAGE_BYTES.toInt()) {
                throw IOException("The backup contains an invalid product photo.")
            }
            totalBytes += length
            if (totalBytes > MAX_BACKUP_IMAGE_BYTES) throw IOException("Product photos exceed the restore size limit.")
            val bytes = ByteArray(length).also(payload::readFully)
            val restoredName = productImages.saveRestoredImage(extension, bytes)
            writtenNames += restoredName
            mapping[sourceName] = restoredName
        }
        return mapping
    }

    companion object {
        private val MAGIC = "MSKPOS01".toByteArray(Charsets.US_ASCII)
        private const val FORMAT_VERSION = 3
        private val SUPPORTED_FORMAT_VERSIONS = setOf(1, 2, FORMAT_VERSION)
        private const val PBKDF_ITERATIONS = 240_000
        private const val SALT_BYTES = 16
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        private const val MAX_COLUMNS = 128
        private const val MAX_ROWS = 10_000_000
        private const val MAX_STRING_BYTES = 16 * 1024 * 1024
        private const val MAX_BACKUP_IMAGE_COUNT = 100_000
        private const val MAX_BACKUP_IMAGE_BYTES = 512L * 1024L * 1024L
        private const val AUTO_BACKUP_RETENTION = 7
        private const val AUTO_SECRET_FILENAME = "automatic_backup_secret.bin"
        private val TABLES_V1 = listOf(
            "categories", "products", "product_units", "inventory_balances", "inventory_movements",
            "customers", "suppliers", "sales", "sale_items", "payments", "customer_ledger_entries",
            "customer_payments", "stock_deliveries", "stock_delivery_items", "expenses", "refunds",
            "refund_items", "audit_log", "app_settings", "printer_configurations",
        )
        private val TABLES = TABLES_V1 + listOf(
            "store_profile", "setup_drafts", "staff_accounts", "staff_permissions", "auth_attempt_state", "restricted_action_audit",
        )

        private fun autoKeyAlias() = if (BuildConfig.DEBUG) "marisuki_automatic_backup_key_debug" else "marisuki_automatic_backup_key"

        private fun validatePassphrase(value: String) {
            require(value.length >= 12) { "Use a backup password with at least 12 characters." }
        }

        private fun deriveKey(passphrase: String, salt: ByteArray) = SecretKeySpec(
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(PBEKeySpec(passphrase.toCharArray(), salt, PBKDF_ITERATIONS, 256))
                .encoded,
            "AES",
        )

        private fun DataOutputStream.writeString(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            writeInt(bytes.size)
            write(bytes)
        }

        private fun DataInputStream.readString(): String {
            val length = readInt()
            if (length !in 0..MAX_STRING_BYTES) throw IOException("The backup contains an invalid text field.")
            val bytes = ByteArray(length)
            readFully(bytes)
            return String(bytes, Charsets.UTF_8)
        }

        private fun DataInputStream.readSafeBytes(expected: Int): ByteArray {
            val length = readInt()
            if (length != expected) throw IOException("The backup encryption header is invalid.")
            return ByteArray(length).also(::readFully)
        }

        private fun DataOutputStream.writeCell(cursor: Cursor, column: Int) {
            when (cursor.getType(column)) {
                Cursor.FIELD_TYPE_NULL -> writeByte(0)
                Cursor.FIELD_TYPE_INTEGER -> { writeByte(1); writeLong(cursor.getLong(column)) }
                Cursor.FIELD_TYPE_FLOAT -> { writeByte(2); writeDouble(cursor.getDouble(column)) }
                Cursor.FIELD_TYPE_STRING -> { writeByte(3); writeString(cursor.getString(column)) }
                Cursor.FIELD_TYPE_BLOB -> {
                    writeByte(4)
                    val bytes = cursor.getBlob(column)
                    writeInt(bytes.size)
                    write(bytes)
                }
                else -> throw IOException("The database contains an unsupported value.")
            }
        }

        private fun DataInputStream.readCell(statement: androidx.sqlite.db.SupportSQLiteStatement, index: Int) {
            when (readUnsignedByte()) {
                0 -> statement.bindNull(index)
                1 -> statement.bindLong(index, readLong())
                2 -> statement.bindDouble(index, readDouble())
                3 -> statement.bindString(index, readString())
                4 -> {
                    val length = readInt()
                    if (length !in 0..MAX_STRING_BYTES) throw IOException("The backup contains an invalid binary field.")
                    statement.bindBlob(index, ByteArray(length).also(::readFully))
                }
                else -> throw IOException("The backup contains an unknown data type.")
            }
        }

        private fun DataInputStream.readImageName(
            statement: androidx.sqlite.db.SupportSQLiteStatement,
            index: Int,
            imageNames: Map<String, String>,
        ) {
            when (readUnsignedByte()) {
                0 -> statement.bindNull(index)
                3 -> {
                    val oldName = readString()
                    val restoredName = imageNames[oldName]
                        ?: throw IOException("The backup is missing a photo referenced by a product.")
                    statement.bindString(index, restoredName)
                }
                else -> throw IOException("The backup contains an invalid product photo reference.")
            }
        }
    }
}
