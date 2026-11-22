package com.breckneck.debtbook.auth

import android.content.Context
import android.util.AtomicFile
import com.breckneck.debtbook.auth.util.CryptoManager
import com.breckneck.deptbook.domain.util.CRYPTO_FILE_NAME
import java.io.File
import java.io.FileOutputStream

/** Replaces only the encrypted PIN. Never deletes or restores user records. */
internal class PinCodeStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, CRYPTO_FILE_NAME))

    fun read(legacyPin: String): String {
        val base = file.baseFile
        if (!base.exists() && !File(base.path + ".bak").exists()) return legacyPin
        // Missing keys or malformed files must never fall back to an old PIN.
        return runCatching { CryptoManager().decrypt(file.openRead()).decodeToString() }.getOrDefault("")
    }

    fun save(pin: String) {
        require(pin.length == 4 && pin.all { it in '0'..'9' })
        val crypto = CryptoManager()
        var stream: FileOutputStream? = null
        try {
            stream = file.startWrite()
            crypto.encrypt(pin.encodeToByteArray(), stream)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}
