package ir.goldpayamak.issuer

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import ir.goldpayamak.core.OfflineLicense
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import java.util.Base64

internal data class IssuedLicense(
    val id: Long,
    val name: String,
    val request: String,
    val code: String,
    val issuedAt: Long,
)

internal class IssuerStore(context: Context) : SQLiteOpenHelper(context, "issued-licenses.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE issued_licenses (
                id INTEGER PRIMARY KEY,
                customer_name TEXT NOT NULL,
                request_code TEXT NOT NULL,
                license_code TEXT NOT NULL,
                issued_at INTEGER NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Unsupported database migration: $oldVersion → $newVersion")
    }

    fun list(): List<IssuedLicense> =
        readableDatabase.rawQuery(
            "SELECT id, customer_name, request_code, license_code, issued_at FROM issued_licenses ORDER BY issued_at DESC, id DESC",
            null,
        ).use { rows ->
            buildList {
                while (rows.moveToNext()) {
                    add(IssuedLicense(rows.getLong(0), rows.getString(1), rows.getString(2), rows.getString(3), rows.getLong(4)))
                }
            }
        }

    fun record(name: String, request: String, code: String): IssuedLicense {
        val timestamp = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("customer_name", name)
            put("request_code", request)
            put("license_code", code)
            put("issued_at", timestamp)
        }
        val id = writableDatabase.insertOrThrow("issued_licenses", null, values)
        return IssuedLicense(id, name, request, code, timestamp)
    }
}

internal object DeviceIssuer {
    private const val KEY_ALIAS = "goldpayamak-license-issuer-v1"

    @Synchronized
    private fun signingKey(): KeyStore.PrivateKeyEntry {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(
                    KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .build()
                )
            }.generateKeyPair()
        }
        return keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
    }

    fun publicKey(): String =
        Base64.getEncoder().encodeToString(signingKey().certificate.publicKey.encoded)

    fun issue(request: String): String =
        OfflineLicense.sign(signingKey().privateKey, request)
}
