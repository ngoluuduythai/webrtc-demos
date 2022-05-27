package me.amryousef.webrtc_demo

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.websocket.*
import io.ktor.http.cio.websocket.*
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

@ExperimentalCoroutinesApi
@KtorExperimentalAPI
class SignallingClient(
    private val listener: SignallingClientListener
) : CoroutineScope {

    companion object {
        private const val HOST_ADDRESS = "webrtc.nirbheek.in"
        private const val HOST_ADDRESS_LOCAL = "192.168.1.18"
    }

    private val job = Job()

    private val gson = Gson()

    override val coroutineContext = Dispatchers.IO + job

    private val client = HttpClient(CIO) {
        install(WebSockets)
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
    }

    private val sendChannel = ConflatedBroadcastChannel<String>()

    init {
        connect()
    }

    private fun connect() = launch {

        client.wss(host = HOST_ADDRESS, port = 8443) {
            listener.onConnectionEstablished()
            val sendData = sendChannel.openSubscription()
            try {
                while (true) {

                    sendData.poll()?.let {
                        Log.v(this@SignallingClient.javaClass.simpleName, "Sending: $it")
                        outgoing.send(Frame.Text(it))
                    }
                    incoming.poll()?.let { frame ->
                        Log.v(this@SignallingClient.javaClass.simpleName, "Received: frame $frame")

                        if (frame is Frame.Text) {
                            val data = frame.readText()

                            Log.v(this@SignallingClient.javaClass.simpleName, "Received: $data")
                            if(data == "HELLO" || data == "OFFER_REQUEST" || data == "SESSION_OK") {
                                Log.v(this@SignallingClient.javaClass.simpleName, "Registered with server")
                            } else {
                                val jsonObject = gson.fromJson(data, JsonObject::class.java)
                                Log.v(this@SignallingClient.javaClass.simpleName, "Received: jsonObject $jsonObject")

                                withContext(Dispatchers.Main) {
                                    if (jsonObject.has("ice")) {
                                        listener.onIceCandidateReceived(gson.fromJson(jsonObject, IceMessage::class.java))
                                    } else if (jsonObject.has("sdp")){
                                        val sdpData = gson.fromJson(jsonObject, SDPMessage::class.java)
                                        when(sdpData.sdp.type) {
                                            "offer" -> listener.onOfferReceived(sdpData)
                                            "answer" -> listener.onAnswerReceived(sdpData)
                                            else -> {
                                                Log.v(this@SignallingClient.javaClass.simpleName, "Unknown SDP Received: jsonObject $jsonObject")
                                            }
                                        }
                                    } else {
                                        Log.v(this@SignallingClient.javaClass.simpleName, "Unknown Received: jsonObject $jsonObject")
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (exception: Throwable) {
                Log.e("asd","asd",exception)
            }
        }
    }

    fun send(dataObject: Any?) = runBlocking {
        val data = gson.toJson(dataObject)
        println("send data $data")
        sendChannel.send(data)
    }

    fun send(textData: String) = runBlocking {
        sendChannel.send(textData)
    }

    fun destroy() {
        //client.close()
        //job.complete()
    }
}


@Serializable
data class SDPMessage(
    val sdp: SDPData
)

@Serializable
data class SDPData(
    val type: String,
    val sdp: String)

@Serializable
data class IceMessage(
    val ice: IceData
)

@Serializable
data class IceData(
    val candidate: String,
    val sdpMid: String,
    val sdpMLineIndex: Int
 )



//{"sdp":{"type":"offer","sdp":"v=0\r\no=-"}}
// {"ice":{"candidate":"candidate:918459","sdpMid":"2","sdpMLineIndex":2}}