package ir.goldpayamak.core

import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object OfflineLicense {
    private val urlEncoder = Base64.getUrlEncoder().withoutPadding()
    private val requestFormat = Regex("[A-Za-z0-9_-]{22}")
    private val signatureFormat = Regex("[A-Za-z0-9_-]{80,100}")

    fun requestCode(packageName: String, androidId: String): String {
        val identity = "$packageName:$androidId".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(identity)
        return urlEncoder.encodeToString(digest.copyOf(16))
    }

    fun verify(publicKey: String, requestCode: String, license: String): Boolean {
        val code = license.trim()
        if (!requestFormat.matches(requestCode) || !signatureFormat.matches(code)) return false

        return try {
            val keyBytes = Base64.getDecoder().decode(publicKey)
            val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyBytes))
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(key)
            verifier.update("goldpayamak-license-v1:$requestCode".toByteArray(Charsets.UTF_8))
            verifier.verify(Base64.getUrlDecoder().decode(code))
        } catch (_: GeneralSecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
