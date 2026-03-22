package com.maxvale.dzvinaplayer.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

data class DlnaServer(val uuid: String, val name: String, val location: String, var controlUrl: String = "")
data class DlnaItem(val id: String, val parentId: String, val title: String, val isContainer: Boolean, val resUrl: String? = null)

class DlnaManager {
    suspend fun discoverServers(): List<DlnaServer> = withContext(Dispatchers.IO) {
        val servers = mutableMapOf<String, DlnaServer>()
        val multicastAddress = InetAddress.getByName("239.255.255.250")
        val port = 1900
        var socket: MulticastSocket? = null
        try {
            socket = MulticastSocket(port)
            socket.joinGroup(multicastAddress)
            socket.soTimeout = 3000

            val request = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 3\r\n" +
                    "ST: urn:schemas-upnp-org:device:MediaServer:1\r\n\r\n"
            val reqData = request.toByteArray()
            val packet = DatagramPacket(reqData, reqData.size, multicastAddress, port)
            socket.send(packet)

            val startTime = System.currentTimeMillis()
            val buf = ByteArray(1024)
            while (System.currentTimeMillis() - startTime < 3000) {
                val recv = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(recv)
                    val response = String(recv.data, 0, recv.length)
                    if (response.startsWith("HTTP/1.1 200 OK")) {
                        var location = ""
                        var usn = ""
                        val lines = response.split("\r\n")
                        for (line in lines) {
                            if (line.lowercase().startsWith("location:")) {
                                location = line.substring(9).trim()
                            } else if (line.lowercase().startsWith("usn:")) {
                                usn = line.substring(4).trim().substringBefore("::")
                            }
                        }
                        if (location.isNotEmpty() && !servers.containsKey(usn)) {
                            servers[usn] = DlnaServer(uuid = usn, name = "Unknown DLNA Server", location = location)
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { socket?.leaveGroup(multicastAddress) } catch (e: Exception) {}
            socket?.close()
        }

        // Fetch description XML to get friendly name and ContentDirectory controlUrl
        val updatedServers = mutableListOf<DlnaServer>()
        for (server in servers.values) {
            try {
                val (name, control) = fetchDeviceDescription(server.location)
                if (control.isNotEmpty()) {
                    server.controlUrl = control
                    updatedServers.add(server.copy(name = name))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return@withContext updatedServers
    }

    private fun fetchDeviceDescription(locationUrl: String): Pair<String, String> {
        val url = URL(locationUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        conn.inputStream.use { stream ->
            val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val doc = builder.parse(stream)
            doc.documentElement.normalize()

            var name = "Media Server"
            val friendlyNames = doc.getElementsByTagName("friendlyName")
            if (friendlyNames.length > 0) name = friendlyNames.item(0).textContent

            var controlUrl = ""
            val services = doc.getElementsByTagName("service")
            for (i in 0 until services.length) {
                val service = services.item(i) as Element
                val type = service.getElementsByTagName("serviceType").item(0)?.textContent
                if (type?.contains("ContentDirectory") == true) {
                    controlUrl = service.getElementsByTagName("controlURL").item(0)?.textContent ?: ""
                    if (!controlUrl.startsWith("http")) {
                        val portStr = if (url.port != -1) ":${url.port}" else ""
                        var base = "${url.protocol}://${url.host}$portStr"
                        if (!controlUrl.startsWith("/")) base += "/"
                        controlUrl = base + controlUrl
                    }
                    break
                }
            }
            return Pair(name, controlUrl)
        }
    }

    suspend fun browse(controlUrl: String, objectId: String = "0"): List<DlnaItem> = withContext(Dispatchers.IO) {
        val soapBody = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\n" +
                "  <s:Body>\n" +
                "    <u:Browse xmlns:u=\"urn:schemas-upnp-org:service:ContentDirectory:1\">\n" +
                "      <ObjectID>$objectId</ObjectID>\n" +
                "      <BrowseFlag>BrowseDirectChildren</BrowseFlag>\n" +
                "      <Filter>*</Filter>\n" +
                "      <StartingIndex>0</StartingIndex>\n" +
                "      <RequestedCount>200</RequestedCount>\n" +
                "      <SortCriteria></SortCriteria>\n" +
                "    </u:Browse>\n" +
                "  </s:Body>\n" +
                "</s:Envelope>"

        val url = URL(controlUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
        conn.setRequestProperty("SOAPAction", "\"urn:schemas-upnp-org:service:ContentDirectory:1#Browse\"")
        conn.doOutput = true

        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(soapBody)
            writer.flush()
        }

        val items = mutableListOf<DlnaItem>()
        if (conn.responseCode == 200) {
            conn.inputStream.use { stream ->
                val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                val doc = builder.parse(stream)
                val resultNodes = doc.getElementsByTagName("Result")
                if (resultNodes.length > 0) {
                    val didlText = resultNodes.item(0).textContent
                    items.addAll(parseDidl(didlText))
                }
            }
        } else {
            Log.e("DlnaManager", "Browse failed with code ${conn.responseCode}")
        }
        return@withContext items
    }

    private fun parseDidl(didl: String): List<DlnaItem> {
        val items = mutableListOf<DlnaItem>()
        try {
            val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val doc = builder.parse(java.io.ByteArrayInputStream(didl.toByteArray(Charsets.UTF_8)))
            
            val containers = doc.getElementsByTagName("container")
            for (i in 0 until containers.length) {
                val node = containers.item(i) as Element
                val id = node.getAttribute("id")
                val parentId = node.getAttribute("parentID")
                val title = node.getElementsByTagName("dc:title").item(0)?.textContent ?: "Folder"
                items.add(DlnaItem(id, parentId, title, true))
            }

            val itemNodes = doc.getElementsByTagName("item")
            for (i in 0 until itemNodes.length) {
                val node = itemNodes.item(i) as Element
                val id = node.getAttribute("id")
                val parentId = node.getAttribute("parentID")
                val title = node.getElementsByTagName("dc:title").item(0)?.textContent ?: "File"
                val resUrl = node.getElementsByTagName("res").item(0)?.textContent
                items.add(DlnaItem(id, parentId, title, false, resUrl))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return items
    }
}
