package com.calculator.vault.managers

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Base64
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FileManager(private val context: Context) {
    
    companion object {
        private const val VAULT_FOLDER = "CalculatorVault"
        private const val IMAGES_FOLDER = "Images"
        private const val VIDEOS_FOLDER = "Videos"
        private const val AUDIO_FOLDER = "Audio"
        private const val DOCUMENTS_FOLDER = "Documents"
    }
    
    private val vaultDir: File by lazy {
        File(context.getExternalFilesDir(null), VAULT_FOLDER).apply {
            if (!exists()) mkdirs()
        }
    }
    
    fun getVaultDirectory(): File = vaultDir
    
    fun getStorageInfo(): Map<String, Any> {
        val stat = StatFs(vaultDir.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong
        
        return mapOf(
            "totalBytes" to totalBlocks * blockSize,
            "availableBytes" to availableBlocks * blockSize,
            "usedBytes" to (totalBlocks - availableBlocks) * blockSize,
            "vaultPath" to vaultDir.absolutePath
        )
    }
    
    fun createFolder(parentPath: String, name: String): Map<String, Any> {
        val parent = if (parentPath.isEmpty()) vaultDir else File(parentPath)
        val newFolder = File(parent, name)
        
        return if (newFolder.mkdirs()) {
            mapOf(
                "success" to true,
                "path" to newFolder.absolutePath,
                "name" to name
            )
        } else {
            mapOf("success" to false, "error" to "Failed to create folder")
        }
    }
    
    fun listFiles(path: String): Map<String, Any> {
        val dir = if (path.isEmpty()) vaultDir else File(path)
        val files = dir.listFiles() ?: emptyArray()
        
        val fileList = files.map { file ->
            mapOf(
                "name" to file.name,
                "path" to file.absolutePath,
                "isDirectory" to file.isDirectory,
                "size" to file.length(),
                "lastModified" to file.lastModified(),
                "canRead" to file.canRead(),
                "canWrite" to file.canWrite()
            )
        }
        
        return mapOf(
            "path" to dir.absolutePath,
            "files" to fileList,
            "count" to fileList.size
        )
    }
    
    fun deleteFile(path: String): Boolean {
        val file = File(path)
        return if (file.exists()) {
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        } else false
    }
    
    fun renameFile(oldPath: String, newName: String): Map<String, Any> {
        val oldFile = File(oldPath)
        val newFile = File(oldFile.parent, newName)
        
        return if (oldFile.renameTo(newFile)) {
            mapOf(
                "success" to true,
                "newPath" to newFile.absolutePath,
                "newName" to newName
            )
        } else {
            mapOf("success" to false, "error" to "Failed to rename")
        }
    }
    
    fun moveFile(sourcePath: String, destPath: String): Map<String, Any> {
        val source = File(sourcePath)
        val dest = File(destPath, source.name)
        
        return try {
            source.inputStream().use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            source.delete()
            mapOf(
                "success" to true,
                "newPath" to dest.absolutePath
            )
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: "Unknown error"))
        }
    }
    
    fun copyFile(sourcePath: String, destPath: String): Map<String, Any> {
        val source = File(sourcePath)
        val dest = File(destPath, source.name)
        
        return try {
            source.inputStream().use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            mapOf(
                "success" to true,
                "newPath" to dest.absolutePath
            )
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: "Unknown error"))
        }
    }
    
    fun readFileAsBase64(path: String): String {
        val file = File(path)
        return file.inputStream().use { input ->
            val bytes = input.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }
    
    fun writeFileFromBase64(path: String, base64Data: String): Map<String, Any> {
        return try {
            val bytes = Base64.decode(base64Data, Base64.NO_WRAP)
            File(path).outputStream().use { output ->
                output.write(bytes)
            }
            mapOf(
                "success" to true,
                "path" to path,
                "bytesWritten" to bytes.size
            )
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: "Unknown error"))
        }
    }
    
    fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "IMG_${timeStamp}.jpg"
        val storageDir = File(vaultDir, IMAGES_FOLDER).apply { mkdirs() }
        return File(storageDir, imageFileName)
    }
    
    fun createVideoFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val videoFileName = "VID_${timeStamp}.mp4"
        val storageDir = File(vaultDir, VIDEOS_FOLDER).apply { mkdirs() }
        return File(storageDir, videoFileName)
    }
    
    fun getFolderForType(type: String): File {
        return when (type.lowercase()) {
            "image" -> File(vaultDir, IMAGES_FOLDER).apply { mkdirs() }
            "video" -> File(vaultDir, VIDEOS_FOLDER).apply { mkdirs() }
            "audio" -> File(vaultDir, AUDIO_FOLDER).apply { mkdirs() }
            else -> File(vaultDir, DOCUMENTS_FOLDER).apply { mkdirs() }
        }
    }
}