package ir.goldpayamak.issuer

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import org.bouncycastle.asn1.sec.SECObjectIdentifiers
import org.bouncycastle.crypto.params.ECNamedDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo
import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

internal data class UnlockedIssuer(val privateKey: PrivateKey, val publicKey: String)

internal class PortableIssuer(private val context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "issuer-private.pem"))
    val hasKey: Boolean get() = file.baseFile.isFile

    fun unlock(password: CharArray): UnlockedIssuer =
        file.openRead().use { PortableIssuerKey.unlock(it.readBytes(), password) }

    fun import(uri: Uri, password: CharArray): UnlockedIssuer {
        val pem = requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
            val bytes = ByteArray(8_193)
            var size = 0
            while (size < bytes.size) {
                val count = input.read(bytes, size, bytes.size - size)
                if (count < 0) break
                size += count
            }
            require(size in 1..8_192)
            bytes.copyOf(size)
        }
        val key = PortableIssuerKey.unlock(pem, password)
        val stream = file.startWrite()
        try {
            stream.write(pem)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
        return key
    }
}

internal object PortableIssuerKey {
    fun unlock(pem: ByteArray, password: CharArray): UnlockedIssuer {
        val encrypted = PEMParser(pem.inputStream().reader(Charsets.US_ASCII)).use { parser ->
            val first = parser.readObject()
            require(first is PKCS8EncryptedPrivateKeyInfo && parser.readObject() == null)
            first
        }
        val decryptor = JceOpenSSLPKCS8DecryptorProviderBuilder()
            .setProvider(BouncyCastleProvider())
            .build(password)
        val info = encrypted.decryptPrivateKeyInfo(decryptor)
        val parameters = PrivateKeyFactory.createKey(info) as? ECPrivateKeyParameters
            ?: error("Expected EC signing key")
        val domain = parameters.parameters as? ECNamedDomainParameters
        require(domain?.name == SECObjectIdentifiers.secp256r1)
        val publicPoint = domain!!.g.multiply(parameters.d).normalize()
        val publicKey = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(
            ECPublicKeyParameters(publicPoint, domain)
        )
        val keyBytes = info.encoded
        val privateKey = try {
            KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        } finally {
            keyBytes.fill(0)
        }
        return UnlockedIssuer(privateKey, Base64.getEncoder().encodeToString(publicKey.encoded))
    }
}
