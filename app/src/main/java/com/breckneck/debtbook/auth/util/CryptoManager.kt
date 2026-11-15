package com.breckneck.debtbook.auth.util

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import com.breckneck.deptbook.domain.util.ENCRYPTION_ALIAS
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.KeyStore.SecretKeyEntry
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

@RequiresApi(Build.VERSION_CODES.M)
class CryptoManager {

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private fun getDecryptCipherForIv(iv: ByteArray): Cipher {
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getKey(), IvParameterSpec(iv))
        }
    }

    private fun getKey(): SecretKey {
        val existingKey = keyStore.getEntry(ENCRYPTION_ALIAS, null) as? SecretKeyEntry
        return existingKey?.secretKey ?: createKey()
    }

    private fun createKey(): SecretKey {
        return KeyGenerator.getInstance(ALGORITHM).apply {
            init(
                KeyGenParameterSpec.Builder(
                    ENCRYPTION_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(BLOCK_MODE)
                    .setEncryptionPaddings(PADDING)
                    .setUserAuthenticationRequired(false)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }

    fun encrypt(bytes: ByteArray, outputStream: OutputStream): ByteArray {
        // A new IV/cipher for every write, including a retry in the same Activity.
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getKey())
        }
        val encryptedBytes = cipher.doFinal(bytes)
        outputStream.write(cipher.iv.size)
        outputStream.write(cipher.iv)
        outputStream.write(encryptedBytes.size)
        outputStream.write(encryptedBytes)
        return encryptedBytes
    }

    fun decrypt(inputStream: InputStream): ByteArray = DataInputStream(inputStream).use {
        val ivSize = it.readUnsignedByte()
        require(ivSize == 16) { "Invalid PIN storage" }
        val iv = ByteArray(ivSize).also(it::readFully)
        val encryptedSize = it.readUnsignedByte()
        require(encryptedSize == 16) { "Invalid PIN storage" }
        val encrypted = ByteArray(encryptedSize).also(it::readFully)
        require(it.read() == -1) { "Invalid PIN storage" }
        getDecryptCipherForIv(iv).doFinal(encrypted)
    }

    companion object {
        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7
        private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
    }
}