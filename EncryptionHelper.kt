package com.calculator.vault.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class EncryptionHelper(private val context: Context) {
    
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "vault_master_key"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
        private const val KEY_LENGTH = 256
        private const val ITERATION_COUNT = 100000
        private const val SALT_LENGTH = 16
    }
    
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    
    private val securePrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "vault_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    fun deriveKeyFromPin(pin: String, salt: ByteArray? = null): Pair<SecretKey, ByteArray> {
        val saltBytes = salt ?: generateSalt()
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pin.toCharArray(), saltBytes, ITERATION_COUNT, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        val key = SecretKeySpec(tmp.encoded, "AES")
        return Pair(key, saltBytes)
    }
    
    fun encryptWithPin(plaintext: String, pin: String): String {
        val (key, salt) = deriveKeyFromPin(pin)
        val iv = generateIV()
        
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        
        val combined = ByteBuffer.allocate(SALT_LENGTH + GCM_IV_LENGTH + ciphertext.size)
            .put(salt)
            .put(iv)
            .put(ciphertext)
            .array()
        
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }
    
    fun decryptWithPin(ciphertext: String, pin: String): String? {
        return try {
            val combined = Base64.decode(ciphertext, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(combined)
            
            val salt = ByteArray(SALT_LENGTH).apply { buffer.get(this) }
            val iv = ByteArray(GCM_IV_LENGTH).apply { buffer.get(this) }
            val encrypted = ByteArray(buffer.remaining()).apply { buffer.get(this) }
            
            val (key, _) = deriveKeyFromPin(pin, salt)
            
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
    
    fun encryptWithKeystore(plaintext: String): String {
        val key = getOrCreateKeystoreKey()
        val iv = generateIV()
        
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        
        val combined = ByteBuffer.allocate(GCM_IV_LENGTH + ciphertext.size)
            .put(iv)
            .put(ciphertext)
            .array()
        
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }
    
    fun decryptWithKeystore(ciphertext: String): String? {
        return try {
            val key = getOrCreateKeystoreKey()
            val combined = Base64.decode(ciphertext, Base64.NO_WRAP)
            
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
    
    fun storeEncrypted(key: String, value: String) {
        securePrefs.edit().putString(key, encryptWithKeystore(value)).apply()
    }
    
    fun retrieveEncrypted(key: String): String? {
        val encrypted = securePrefs.getString(key, null) ?: return null
        return decryptWithKeystore(encrypted)
    }
    
    fun storeVaultData(key: String, data: String, pin: String) {
        val encrypted = encryptWithPin(data, pin)
        context.getSharedPreferences("vault_data", Context.MODE_PRIVATE)
            .edit().putString(key, encrypted).apply()
    }
    
    fun retrieveVaultData(key: String, pin: String): String? {
        val encrypted = context.getSharedPreferences("vault_data", Context.MODE_PRIVATE)
            .getString(key, null) ?: return null
        return decryptWithPin(encrypted, pin)
    }
    
    fun verifyPin(pin: String): Boolean {
        val storedHash = securePrefs.getString("pin_verification", null) ?: return false
        return decryptWithPin(storedHash, pin) == "verified"
    }
    
    fun setPin(pin: String) {
        val verification = encryptWithPin("verified", pin)
        securePrefs.edit().putString("pin_verification", verification).apply()
    }
    
    fun changePin(oldPin: String, newPin: String): Boolean {
        if (!verifyPin(oldPin)) return false
        
        val prefs = context.getSharedPreferences("vault_data", Context.MODE_PRIVATE)
        val allData = prefs.all
        
        prefs.edit().apply {
            allData.forEach { (key, encrypted) ->
                if (encrypted is String) {
                    val decrypted = decryptWithPin(encrypted, oldPin)
                    if (decrypted != null) {
                        putString(key, encryptWithPin(decrypted, newPin))
                    }
                }
            }
            apply()
        }
        
        setPin(newPin)
        return true
    }
    
    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: run {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_LENGTH)
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build()
            
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }
    
    private fun generateIV(): ByteArray {
        return ByteArray(GCM_IV_LENGTH).apply {
            SecureRandom().nextBytes(this)
        }
    }
    
    private fun generateSalt(): ByteArray {
        return ByteArray(SALT_LENGTH).apply {
            SecureRandom().nextBytes(this)
        }
    }
}