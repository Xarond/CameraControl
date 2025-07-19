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
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*

class PTZController(
    private val ip: String,
    private val port: Int,
    private val context: Context,
    private val username: String = "",
    private val password: String = ""
) {
    private val client = OkHttpClient()
    private val soap12Type = "application/soap+xml; charset=utf-8".toMediaType()
    private val soap11Type = "text/xml; charset=utf-8".toMediaType()
    private val baseUrl = "http://$ip:$port"

    private var mediaXAddr: String? = null
    private var ptzXAddr:   String? = null
    private var profileToken: String? = null

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

    /** STEP 1: GetCapabilities → discover and normalize XAddrs **/
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

        val resp = postSoap(baseUrl + "/onvif/device_service", envelope)
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

        mediaXAddr = normalizeEndpoint(rewriteHostPort(internalMediaX, ip, port), "media_service")
        ptzXAddr   = normalizeEndpoint(rewriteHostPort(internalPtzX,   ip, port), "ptz_service")

        Log.d("PTZController", "Using endpoints: Media=$mediaXAddr, PTZ=$ptzXAddr")
        toastMain("Using:\n$mediaXAddr\n$ptzXAddr")
        return true
    }

    /** STEP 2: GetProfiles → fetch ProfileToken via SOAP 1.1 + WS-Security **/
    private suspend fun fetchProfileToken() {
        val envelope = """
            <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
                        xmlns:trt="http://www.onvif.org/ver10/media/wsdl">
              <s:Body><trt:GetProfiles/></s:Body>
            </s:Envelope>
        """.trimIndent()

        Log.d("PTZController", "GetProfiles SOAP Request: $envelope")
        val resp = postSoap11(
            mediaXAddr!!,
            envelope,
            "http://www.onvif.org/ver10/media/wsdl/GetProfiles"
        )
        if (resp.isNullOrBlank()) {
            toastMain("GetProfiles failed or empty response")
            return
        }
        Log.d("PTZController", "GetProfiles Response: $resp")

        try {
            val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                setInput(resp.reader())
            }
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    val localName = parser.name.substringAfter(':')
                    if (localName == "Profiles") {
                        profileToken = parser.getAttributeValue(null, "token")
                        Log.d("PTZController", "Parsed ProfileToken: $profileToken")
                        break
                    }
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

    /** STEP 3: ContinuousMove → then Stop **/
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
                        <tt:PanTilt x="$x" y="$y" space="http://www.onvif.org/ver10/tptz/PanTiltSpaces/VelocityGenericSpace"/>
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

    /** STEP 4: Stop **/
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

    /** Build WS‑Security UsernameToken header **/
    private fun buildSecurityHeader(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val created = sdf.format(Date())
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val nonceB64 = Base64.encodeToString(nonce, Base64.NO_WRAP)
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(nonce + created.toByteArray(Charsets.UTF_8) + password.toByteArray(Charsets.UTF_8))
        val digestB64 = Base64.encodeToString(digest, Base64.NO_WRAP)

        return """
            <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
                           xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
              <wsse:UsernameToken wsu:Id="UsernameToken-1">
                <wsse:Username>$username</wsse:Username>
                <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest">
                  $digestB64
                </wsse:Password>
                <wsse:Nonce EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary">
                  $nonceB64
                </wsse:Nonce>
                <wsu:Created>$created</wsu:Created>
              </wsse:UsernameToken>
            </wsse:Security>
        """.trimIndent()
    }

    /** SOAP 1.2 POST with WS‑Security header injection **/
    private suspend fun postSoap(url: String, body: String): String? = withContext(Dispatchers.IO) {
        val full = body.replaceFirst("<s:Body>", "<s:Header>${buildSecurityHeader()}</s:Header><s:Body>")
        val builder = Request.Builder()
            .url(url)
            .apply {
                if (username.isNotBlank()) addHeader("Authorization",
                    "Basic " + Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)
                )
                addHeader("Connection","close")
                addHeader("Content-Type", soap12Type.toString())
            }
            .post(RequestBody.create(soap12Type, full))

        try {
            client.newCall(builder.build()).execute().use { resp ->
                Log.d("PTZController", "$url → HTTP ${resp.code}")
                // czytamy cały strumień na raz jako bajty:
                val bytes = resp.body!!.bytes()
                val text  = bytes.toString(Charsets.UTF_8)
                Log.d("PTZController", "-- RAW RESPONSE --\n$text")
                return@withContext text
            }
        } catch (e: Exception) {
            Log.e("PTZController", "postSoap error", e)
            toastMain("HTTP error: ${e.localizedMessage}")
            return@withContext null
        }
    }

    /** SOAP 1.1 POST with SOAPAction + WS‑Security injection **/
    private suspend fun postSoap11(url: String, body: String, action: String): String? = withContext(Dispatchers.IO) {
        val full = body.replaceFirst("<s:Body>", "<s:Header>${buildSecurityHeader()}</s:Header><s:Body>")
        val builder = Request.Builder()
            .url(url)
            .apply {
                if (username.isNotBlank()) addHeader("Authorization",
                    "Basic " + Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)
                )
                addHeader("Connection","close")
                addHeader("Content-Type", soap11Type.toString())
                addHeader("SOAPAction", "\"$action\"")
            }
            .post(RequestBody.create(soap11Type, full))

        try {
            client.newCall(builder.build()).execute().use { resp ->
                Log.d("PTZController", "$url → HTTP ${resp.code} (SOAP 1.1)")
                val bytes = resp.body!!.bytes()
                val text  = bytes.toString(Charsets.UTF_8)
                Log.d("PTZController", "-- RAW RESPONSE --\n$text")
                return@withContext text
            }
        } catch (e: Exception) {
            Log.e("PTZController", "postSoap11 error", e)
            toastMain("HTTP11 error: ${e.localizedMessage}")
            return@withContext null
        }
    }

    /** Extract XAddr from GetCapabilities **/
    private fun extractXAddr(xml: String, tag: String): String? {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(xml.reader())
        }
        var inside = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            parser.name?.let { raw ->
                val name = raw.substringAfter(':')
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> if (name == tag) inside = true
                    else if (inside && name == "XAddr") return parser.nextText()
                    XmlPullParser.END_TAG   -> if (name == tag) inside = false
                }
            }
            parser.next()
        }
        return null
    }

    /** Rewrite discovered URL to external host:port **/
    private fun rewriteHostPort(url: String, newHost: String, newPort: Int): String {
        val u = Uri.parse(url)
        return u.buildUpon()
            .encodedAuthority("$newHost:$newPort")
            .build()
            .toString()
    }

    /** Normalize ONVIF endpoint path **/
    private fun normalizeEndpoint(xaddr: String, svc: String): String {
        val u = Uri.parse(xaddr)
        return u.buildUpon()
            .encodedPath("/onvif/$svc")
            .build()
            .toString()
    }

    /** Toast helper on main thread **/
    private suspend fun toastMain(text: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }
}
