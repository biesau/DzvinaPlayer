package com.maxvale.dzvinaplayer.network

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CustomDataSourceFactory(context: Context) : DataSource.Factory {
    private val defaultFactory = DefaultDataSource.Factory(context)

    override fun createDataSource(): DataSource {
        val defaultDataSource = defaultFactory.createDataSource()
        return object : DataSource {
            var isFtp = false
            var ftpDataSource: FtpDataSource? = null

            override fun addTransferListener(transferListener: TransferListener) {
                defaultDataSource.addTransferListener(transferListener)
            }

            override fun open(dataSpec: DataSpec): Long {
                if (dataSpec.uri.scheme == "ftp") {
                    isFtp = true
                    ftpDataSource = FtpDataSource()
                    return ftpDataSource!!.open(dataSpec)
                }
                isFtp = false
                return defaultDataSource.open(dataSpec)
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                return if (isFtp) {
                    ftpDataSource!!.read(buffer, offset, length)
                } else {
                    defaultDataSource.read(buffer, offset, length)
                }
            }

            override fun getUri(): Uri? {
                return if (isFtp) ftpDataSource?.uri else defaultDataSource.uri
            }

            override fun close() {
                if (isFtp) {
                    ftpDataSource?.close()
                } else {
                    defaultDataSource.close()
                }
            }
        }
    }
}
