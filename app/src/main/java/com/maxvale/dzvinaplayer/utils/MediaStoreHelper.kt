package com.maxvale.dzvinaplayer.utils

import android.content.Context
import android.provider.MediaStore
import java.io.File

data class MediaFile(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val dateModified: Long = 0
) {
    fun toFile() = File(path)
}

object MediaStoreHelper {
    fun getMediaInFolder(context: Context, folderPath: String?): List<MediaFile> {
        val result = mutableListOf<MediaFile>()
        val externalStoragePath = android.os.Environment.getExternalStorageDirectory().absolutePath
        
        // If folderPath is null or equal to externalStoragePath, we are at root
        val currentPath = folderPath ?: externalStoragePath

        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.RELATIVE_PATH
        )

        // Query both Audio and Video
        val uriAudio = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val uriVideo = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val uris = listOf(uriAudio, uriVideo)
        
        val folders = mutableSetOf<String>()
        val files = mutableListOf<MediaFile>()

        uris.forEach { uri ->
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val relativePathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataIndex)
                    val name = cursor.getString(nameIndex)
                    val size = cursor.getLong(sizeIndex)
                    val date = cursor.getLong(dateIndex)
                    val relativePath = cursor.getString(relativePathIndex) // e.g. "Movies/SubFolder/"

                    val file = File(path)
                    val parentFile = file.parentFile ?: continue
                    val parentPath = parentFile.absolutePath

                    if (parentPath == currentPath) {
                        files.add(MediaFile(path, name, false, size, date))
                    } else if (parentPath.startsWith(currentPath)) {
                        // This file is in a subfolder of currentPath
                        // We want to extract the immediate subfolder name
                        val subPath = parentPath.removePrefix(currentPath).removePrefix("/")
                        val immediateSubFolder = subPath.split("/").firstOrNull()
                        if (!immediateSubFolder.isNullOrEmpty()) {
                            folders.add(immediateSubFolder)
                        }
                    }
                }
            }
        }

        folders.forEach { folderName ->
            val folderPath = if (currentPath.endsWith("/")) "$currentPath$folderName" else "$currentPath/$folderName"
            result.add(MediaFile(folderPath, folderName, true))
        }
        
        result.addAll(files)
        
        return result.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun getUriForFile(context: Context, file: File): android.net.Uri? {
        if (file.isDirectory) return null
        val contentResolver = context.contentResolver
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DATA} = ?"
        val selectionArgs = arrayOf(file.absolutePath)

        val uris = listOf(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )

        uris.forEach { uri ->
            contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    return android.content.ContentUris.withAppendedId(uri, id)
                }
            }
        }
        return null
    }

    fun deleteFile(context: Context, file: File): Boolean {
        if (file.isDirectory) {
            return file.deleteRecursively()
        }

        // Try to delete via MediaStore first to keep DB in sync
        val contentResolver = context.contentResolver
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DATA} = ?"
        val selectionArgs = arrayOf(file.absolutePath)

        val uris = listOf(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )

        uris.forEach { uri ->
            contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    val itemUri = android.content.ContentUris.withAppendedId(uri, id)
                    val deleted = contentResolver.delete(itemUri, null, null)
                    if (deleted > 0) return true
                }
            }
        }

        // Fallback to direct file deletion
        return file.delete()
    }
}

