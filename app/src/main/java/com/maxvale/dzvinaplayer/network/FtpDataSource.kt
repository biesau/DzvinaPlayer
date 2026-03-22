package com.maxvale.dzvinaplayer.network

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.InputStream
import kotlin.math.min

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class FtpDataSource : BaseDataSource(true) {
    private var ftpClient: FTPClient? = null
    private var inputStream: InputStream? = null
    private var currentUri: Uri? = null
    private var bytesToRead: Long = 0
    private var bytesRead: Long = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        currentUri = dataSpec.uri
        transferInitializing(dataSpec)

        val host = currentUri?.host ?: throw java.io.IOException("No host")
        val port = if (currentUri?.port != -1) currentUri?.port ?: 21 else 21
        val userInfo = currentUri?.userInfo?.split(":")
        val user = userInfo?.getOrNull(0) ?: "anonymous"
        val pass = userInfo?.getOrNull(1) ?: ""
        val path = currentUri?.path ?: throw java.io.IOException("No path")

        ftpClient = FTPClient()
        ftpClient?.connect(host, port)
        if (ftpClient?.login(user, pass) != true) {
            throw java.io.IOException("FTP Login failed")
        }
        ftpClient?.enterLocalPassiveMode()
        ftpClient?.setFileType(FTP.BINARY_FILE_TYPE)
        
        if (dataSpec.position > 0) {
            ftpClient?.setRestartOffset(dataSpec.position)
        }

        ftpClient?.sendCommand("SIZE", path)
        val reply = ftpClient?.replyString
        var fileSize: Long = C.LENGTH_UNSET.toLong()
        if (reply != null && reply.startsWith("213 ")) {
            fileSize = reply.substring(4).trim().toLongOrNull() ?: C.LENGTH_UNSET.toLong()
        }

        inputStream = ftpClient?.retrieveFileStream(path) ?: throw java.io.IOException("No input stream for path $path")

        bytesToRead = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else if (fileSize != C.LENGTH_UNSET.toLong()) {
            fileSize - dataSpec.position
        } else {
            C.LENGTH_UNSET.toLong()
        }

        opened = true
        transferStarted(dataSpec)
        return bytesToRead
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesToRead != C.LENGTH_UNSET.toLong() && bytesToRead == bytesRead) {
            return C.RESULT_END_OF_INPUT
        }

        val bytesToReadThisTime = if (bytesToRead != C.LENGTH_UNSET.toLong()) {
            val remaining = bytesToRead - bytesRead
            min(length.toLong(), remaining).toInt()
        } else length

        val read = inputStream?.read(buffer, offset, bytesToReadThisTime) ?: -1
        if (read == -1) {
            if (bytesToRead != C.LENGTH_UNSET.toLong() && bytesToRead != bytesRead) {
                throw java.io.EOFException()
            }
            return C.RESULT_END_OF_INPUT
        }
        bytesRead += read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        currentUri = null
        try {
            inputStream?.close()
        } catch (e: Exception) {}
        inputStream = null
        
        try {
            ftpClient?.completePendingCommand()
            ftpClient?.logout()
            ftpClient?.disconnect()
        } catch (e: Exception) {}
        ftpClient = null
        
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}
