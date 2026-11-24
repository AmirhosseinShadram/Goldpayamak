package ir.goldpayamak.core

import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineLicenseTest {
    @Test
    fun licenseRequiresTheIssuerAndTheSameDeviceAndPackage() {
        val generator = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }
        val issuer = generator.generateKeyPair()
        val publicKey = Base64.getEncoder().encodeToString(issuer.public.encoded)
        val request = OfflineLicense.requestCode("ir.goldpayamak.app", "device-a")
        val signer = Signature.getInstance("SHA256withECDSA").apply {
            initSign(issuer.private)
            update("goldpayamak-license-v1:$request".toByteArray(Charsets.UTF_8))
        }
        val code = Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign())

        assertTrue(OfflineLicense.verify(publicKey, request, code))
        assertTrue(OfflineLicense.verify(publicKey, request, " $code\n"))
        assertFalse(OfflineLicense.verify(publicKey, OfflineLicense.requestCode("ir.goldpayamak.app", "device-b"), code))
        assertFalse(OfflineLicense.verify(publicKey, OfflineLicense.requestCode("ir.goldpayamak.app.redesign", "device-a"), code))
        val otherKey = Base64.getEncoder().encodeToString(generator.generateKeyPair().public.encoded)
        assertFalse(OfflineLicense.verify(otherKey, request, code))
        assertFalse(OfflineLicense.verify(publicKey, request, code.take(20)))
        assertFalse(OfflineLicense.verify("invalid", request, code))
    }
}
