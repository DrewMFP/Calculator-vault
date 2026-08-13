package com.calculator.vault

import android.app.Application
import android.os.Build
import android.webkit.WebView
import java.io.File

class VaultApplication : Application() {
    
    companion object {
        lateinit var instance: VaultApplication
            private set
        
        fun getContext() = instance.applicationContext
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        }
        
        initializeVault()
    }
    
    private fun initializeVault() {
        val vaultDir = getExternalFilesDir(null)?.let { 
            File(it, "CalculatorVault").apply { mkdirs() }
        }
        
        vaultDir?.let { dir ->
            listOf("Images", "Videos", "Audio", "Documents", "Backups").forEach { 
                File(dir, it).mkdirs() 
            }
        }
    }
}