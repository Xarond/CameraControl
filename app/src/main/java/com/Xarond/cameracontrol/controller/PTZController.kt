package com.Xarond.cameracontrol.controller

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class PTZController(
    private val ip: String,
    private val port: Int,
    private val context: Context,
    private val username: String = "",
    private val password: String = ""
) {
    private val client = OkHttpClient()
    private val soapType = "application/soap+xml; charset=utf-8".toMediaType()
    private val baseUrl = "http://$ip:$port"

    private var mediaXAddr: String? = null
    private var ptzXAddr:   String? = null
    private var profileToken:String? = null

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!resolveXAddr()) return@launch
                fetchProfileToken()
            } catch (e: Exception) {
                Log.e("PTZController", "Initialization error", e)
                toastMain("PTZ init error: ${e.localizedMessage}")
            }
        }
    }

    // Step 1: GetCapabilities → discover and rewrite XAddrs
    private suspend fun resolveXAddr(): Boolean {
        val envelope = """
            <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
                        xmlns:tds="http://www.onvif.org/ver10/device/wsdl">
              <s:Body>
                <tds:GetCapabilities>
                  <tds:Category>All</tds:Category>
                </tds:GetCapabilities>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        val resp = postSoap("$baseUrl/onvif/device_service", envelope)
        if (resp.isNullOrBlank()) {
            toastMain("GetCapabilities failed or empty response")
            return false
        }

        val internalMediaX = extractXAddr(resp, "Media")
        val internalPtzX   = extractXAddr(resp, "PTZ")
        if (internalMediaX.isNullOrBlank() || internalPtzX.isNullOrBlank()) {
            toastMain("No Media/PTZ service detected")
            return false
        }

        // Rewrite host:port to the external one we connected with
        mediaXAddr = rewriteHostPort(internalMediaX, ip, port)
        ptzXAddr   = rewriteHostPort(internalPtzX,   ip, port)

        Log.d("PTZController", "Rewritten MediaXAddr=$mediaXAddr, PTZXAddr=$ptzXAddr")
        toastMain("Using:\n$mediaXAddr\n$ptzXAddr")
        return true
    }

    // Step 2: GetProfiles → fetch ProfileToken with detailed logging
    private suspend fun fetchProfileToken() {
        val envelope = """
            <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
                        xmlns:trt="http://www.onvif.org/ver10/media/wsdl">
              <s:Body><trt:GetProfiles/></s:Body>
            </s:Envelope>
        """.trimIndent()

        Log.d("PTZController", "GetProfiles SOAP Request: $envelope")
        val resp = postSoap(mediaXAddr!!, envelope)
        if (resp.isNullOrBlank()) {
            toastMain("GetProfiles failed or empty response")
            return
        }
        Log.d("PTZController", "GetProfiles Response: $resp")

        try {
            val parser = XmlPullParserFactory.newInstance().newPullParser().apply { setInput(resp.reader()) }
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Profiles") {
                    profileToken = parser.getAttributeValue(null, "token")
                    Log.d("PTZController", "Parsed ProfileToken: $profileToken")
                    break
                }
                parser.next()
            }
            if (profileToken.isNullOrBlank()) {
                Log.e("PTZController", "No token attribute found in Profiles tag")
                toastMain("No token found in GetProfiles XML")
            } else {
                toastMain("PTZ token: $profileToken")
            }
        } catch (e: Exception) {
            Log.e("PTZController", "Error parsing GetProfiles response", e)
            toastMain("Parsing token error: ${e.localizedMessage}")
        }
    }

    // Step 3: ContinuousMove → then Stop
    fun moveLeft()  = sendMove(-1f, 0f, 0f)
    fun moveRight() = sendMove(1f, 0f, 0f)
    fun moveUp()    = sendMove(0f, 1f, 0f)
    fun moveDown()  = sendMove(0f, -1f, 0f)
    fun zoomIn()    = sendMove(0f, 0f, 1f)
    fun zoomOut()   = sendMove(0f, 0f, -1f)

    private fun sendMove(x: Float, y: Float, zoom: Float) {
        if (profileToken.isNullOrBlank()) {
            Toast.makeText(context, "No PTZ token – cannot move", Toast.LENGTH_SHORT).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val envelope = """
                <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
                            xmlns:tt="http://www.onvif.org/ver10/schema"
                            xmlns:tptz="http://www.onvif.org/ver20/ptz/wsdl">
                  <s:Body>
                    <tptz:ContinuousMove>
                      <tptz:ProfileToken>$profileToken</tptz:ProfileToken>
                      <tptz:Velocity>
                        <tt:PanTilt x="$x" y="$y"
                          space="http://www.onvif.org/ver10/tptz/PanTiltSpaces/VelocityGenericSpace"/>
                        <tt:Zoom x="$zoom"/>
                      </tptz:Velocity>
                    </tptz:ContinuousMove>
                  </s:Body>
                </s:Envelope>
            """.trimIndent()

            val moveResp = postSoap(ptzXAddr!!, envelope)
            toastMain("Move response: ${moveResp?.take(100) ?: "null"}")
            delay(1000)
            sendStop()
        }
    }

    // Step 4: Stop
    private fun sendStop() {
        if (profileToken.isNullOrBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            val envelope = """
                <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
                            xmlns:tptz="http://www.onvif.org/ver20/ptz/wsdl">
                  <s:Body>
                    <tptz:Stop>
                      <tptz:ProfileToken>$profileToken</tptz:ProfileToken>
                      <tptz:PanTilt>true</tptz:PanTilt>
                      <tptz:Zoom>true</tptz:Zoom>
                    </tptz:Stop>
                  </s:Body>
                </s:Envelope>
            """.trimIndent()

            val stopResp = postSoap(ptzXAddr!!, envelope)
            toastMain("Stop response: ${stopResp?.take(100) ?: "null"}")
        }
    }

    // Shared SOAP helper with fallback for ProtocolException
    private suspend fun postSoap(url: String, body: String): String? = withContext(Dispatchers.IO) {
        val authHeader = if (username.isNotBlank() && password.isNotBlank()) {
            "Basic " + Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)
        } else null

        val req = Request.Builder()
            .url(url)
            .apply {
                authHeader?.let { addHeader("Authorization", it) }
                addHeader("Connection", "close")
            }
            .post(RequestBody.create(soapType, body))
            .build()

        return@withContext try {
            client.newCall(req).execute().use { resp ->
                Log.d("PTZController", "$url → HTTP ${resp.code}")
                try {
                    resp.body?.string()
                } catch (pe: java.net.ProtocolException) {
                    Log.w("PTZController", "ProtocolException reading body, fallback", pe)
                    resp.body?.source()?.let { source ->
                        source.request(Long.MAX_VALUE)
                        source.buffer.clone().readUtf8()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PTZController", "postSoap error", e)
            toastMain("HTTP error: ${e.localizedMessage}")
            null
        }
    }

    // Extract XAddr from GetCapabilities
    private fun extractXAddr(xml: String, tag: String): String? {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply { setInput(xml.reader()) }
        var inside = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            val raw = parser.name
            if (raw != null) {
                val name = raw.substringAfter(':')
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name == tag) inside = true
                        if (name == "XAddr" && inside) return parser.nextText()
                    }
                    XmlPullParser.END_TAG -> if (name == tag) inside = false
                }
            }
            parser.next()
        }
        return null
    }

    // Rewrite discovered URL to external host:port
    private fun rewriteHostPort(url: String, newHost: String, newPort: Int): String {
        val u = Uri.parse(url)
        return u.buildUpon()
            .encodedAuthority("$newHost:$newPort")
            .build()
            .toString()
    }

    // Toast helper on main thread
    private suspend fun toastMain(text: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }
}