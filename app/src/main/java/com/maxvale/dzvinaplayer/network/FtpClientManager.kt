package com.maxvale.dzvinaplayer.network

import com.maxvale.dzvinaplayer.data.FtpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile

class FtpClientManager {
    private val ftpClient = FTPClient()
    var currentServer: FtpServer? = null
        private set
    
    suspend fun connect(server: FtpServer): Boolean = withContext(Dispatchers.IO) {
        try {
            if (ftpClient.isConnected) {
                ftpClient.disconnect()
            }
            ftpClient.connect(server.host, server.port)
            val loginSuccess = ftpClient.login(server.user, server.pass)
            if (loginSuccess) {
                ftpClient.enterLocalPassiveMode()
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                currentServer = server
                return@withContext true
            }
            return@withContext false
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun listFiles(path: String = ""): List<FTPFile> = withContext(Dispatchers.IO) {
        try {
            if (!ftpClient.isConnected) return@withContext emptyList()
            if (path.isNotEmpty() && path != "/") {
                ftpClient.changeWorkingDirectory(path)
            }
            return@withContext ftpClient.listFiles().toList()
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    suspend fun changeDirUp(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!ftpClient.isConnected) return@withContext false
            return@withContext ftpClient.changeToParentDirectory()
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
    
    suspend fun getCurrentDir(): String = withContext(Dispatchers.IO) {
        try {
             if (!ftpClient.isConnected) return@withContext "/"
             return@withContext ftpClient.printWorkingDirectory() ?: "/"
        } catch (e: Exception) {
            return@withContext "/"
        }
    }

    suspend fun deleteFile(name: String, isDir: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!ftpClient.isConnected) return@withContext false
            return@withContext if (isDir) {
                ftpClient.removeDirectory(name)
            } else {
                ftpClient.deleteFile(name)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    fun disconnect() {
        try {
            if (ftpClient.isConnected) {
                ftpClient.logout()
                ftpClient.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
