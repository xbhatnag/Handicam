package com.xbhatnag.handicam

import android.app.Application
import android.content.Context.CONNECTIVITY_SERVICE
import android.content.Context.WIFI_SERVICE
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.SupplicantState
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.widget.ImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder


object Connection {
    // MAKE ALL YOUR CHANGES HERE.
    // Activate the "Smart Remote App" on your Sony camera.
    // Copy the WiFi Network and Password as displayed on the camera.
    // Get the IP Address of the camera by manually connecting to the WiFi.
    // The IP Address remains fixed, so this is not a problem.
    // The quotes around the SSID and PSK are necessary. DO NOT REMOVE THEM.
    private val CameraSSID = "\"<Insert Camera WiFi Network Name>\""
    private val CameraPSK = "\"<Insert Camera WiFi Network Password>\""
    private val CameraIP = "<Insert Camera IP>"

    private val CameraEndpoint = "http://$CameraIP:8080/sony/camera"
    private var networkId = -1
    private lateinit var client: OkHttpClient

    fun json(method: String, params: Array<out String>) : String {
        val obj = JSONObject()
        with(obj) {
            put("method", method)
            put("params", JSONArray(params))
            put("id", 1)
            put("version", "1.0")
        }
        return obj.toString()
    }

    fun send(method: String, vararg params: String): String {
        val data = json(method, params)
        val body = data.toRequestBody("application/json; charset=ascii".toMediaType())
        val request = Request.Builder()
            .url(CameraEndpoint)
            .post(body)
            .build()
        val response = client.newCall(request).execute().body!!.string()
        return response
    }

    fun establishConnection(wifiManager: WifiManager, networkId: Int) {
        while (true) {
            // Attempt to disconnect from the current network
            val disconnectSuccess = wifiManager.disconnect()
            require(disconnectSuccess)

            // Attempt to connect to our network
            val enableSuccess = wifiManager.enableNetwork(networkId, true)
            require(enableSuccess)

            val reconnectSuccess = wifiManager.reconnect()
            require(reconnectSuccess)

            // Wait until you are connected to a network
            while (wifiManager.connectionInfo.supplicantState != SupplicantState.COMPLETED) { }
            if (wifiManager.connectionInfo.networkId == networkId) {
                break
            }
        }
    }

    fun waitUntilConnected(connManager: ConnectivityManager): Network {
        while(true) {
            for (network in connManager.allNetworks) {
                val capabilities = connManager.getNetworkCapabilities(network)
                val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                val isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                if (isWifi && isConnected) {
                    Thread.sleep(2000)
                    return network
                }
            }
        }
    }

    fun connectToWifi(context: Application) {
        val connManager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiManager = context.getSystemService(WIFI_SERVICE) as WifiManager

        // Add network to list
        val wifiConfig = WifiConfiguration().apply {
            SSID = CameraSSID
            preSharedKey = CameraPSK
        }
        networkId = wifiManager.addNetwork(wifiConfig)
        require(networkId != -1)
        establishConnection(wifiManager, networkId)
        val network = waitUntilConnected(connManager)
        connManager.bindProcessToNetwork(network)
        client = OkHttpClient.Builder().socketFactory(network.socketFactory).build()
    }

    fun disconnectFromWiFi(context: Application) {
        val wifiManager = context.getSystemService(WIFI_SERVICE) as WifiManager

        // Attempt to disconnect from our network
        val disconnectSuccess = wifiManager.disconnect()
        require(disconnectSuccess)

        val disableSuccess = wifiManager.disableNetwork(networkId)
        require(disableSuccess)
    }

    fun startRecMode() = send("startRecMode")

    fun stopRecMode() = send("stopRecMode")

    fun takePicture() = send("actTakePicture")

    /*
     * LIVEVIEW FUNCTIONS
     */

    fun startLiveview(): String {
        val response = send("startLiveview")
        return JSONObject(response).getJSONArray("result").getString(0)
    }

    fun stopLiveview() = send("stopLiveview")

    fun ByteArray.toInt(): Int {
        return ByteBuffer.wrap(this).order(ByteOrder.BIG_ENDIAN).int
    }

    fun parseCommonHeader(byteArray: ByteArray): Boolean {
        require(byteArray[0] == 0xFF.toByte())
        require(byteArray[1] == 0x01.toByte())
        return true
    }

    fun parsePayloadHeader(byteArray: ByteArray): Pair<Int, Int> {
        require(byteArray[0] == 0x24.toByte())
        require(byteArray[1] == 0x35.toByte())
        require(byteArray[2] == 0x68.toByte())
        require(byteArray[3] == 0x79.toByte())
        val imageSize = byteArrayOf(0x00, byteArray[4], byteArray[5], byteArray[6]).toInt()
        val paddingSize = byteArrayOf(0x00, 0x00, 0x00, byteArray[7]).toInt()
        return imageSize to paddingSize
    }

    fun BufferedInputStream.fillBuffer(size: Int): ByteArray {
        val buffer = ByteArray(size)
        var offset = 0
        while (true) {
            val actualRead = read(buffer, offset, size - offset)
            require(actualRead > 0)
            offset += actualRead
            if (offset == size) break
        }
        return buffer
    }

    fun stream(liveviewURL: String, view: ImageView) {
        val request = Request.Builder().url(liveviewURL).get().build()
        val response = client.newCall(request).execute()
        val stream = BufferedInputStream(response.body!!.byteStream())
        while (true) {
            // Read and process the common header
            val commonHeader = stream.fillBuffer(8)
            parseCommonHeader(commonHeader)

            // Read and process the payload header
            val payloadHeader = stream.fillBuffer(128)
            val (imageSize, paddingSize) = parsePayloadHeader(payloadHeader)

            // Read the image and padding into their respective buffers
            val imageArray = stream.fillBuffer(imageSize)

            if (paddingSize > 0) {
                stream.fillBuffer(paddingSize)
            }

            // Process the image into a bitmap
            val bitmap = BitmapFactory.decodeByteArray(imageArray, 0, imageSize)

            // Set image on UI
            GlobalScope.launch(Dispatchers.Main) {
                view.setImageBitmap(bitmap)
            }
        }
    }

    fun zoomIn() = send("actZoom", "in", "1shot")

    fun zoomOut() = send("actZoom", "out", "1shot")

    fun halfPressShutter() = send("actHalfPressShutter")
}