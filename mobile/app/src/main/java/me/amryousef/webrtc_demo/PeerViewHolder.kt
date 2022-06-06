package me.amryousef.webrtc_demo

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Base64.encodeToString
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.ktor.util.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.webrtc.*
import java.util.*
import android.util.Base64
import androidx.annotation.MainThread
import com.google.gson.Gson
import io.nats.client.*
import kotlinx.coroutines.*
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.flow.*

@UseExperimental(ExperimentalCoroutinesApi::class)
class PeerViewHolder(view: View, private val getItem: (Int) -> TrackPeerMap) :
    RecyclerView.ViewHolder(view) {
    private val TAG = PeerViewHolder::class.java.simpleName
    private var sinkAdded = false
    private lateinit var signallingClient: SignallingClient
    private lateinit var rtcClient: RTCClient

    private lateinit var application: Application
    private lateinit var webrtcView: SurfaceViewRenderer
    private lateinit var trackPeerMap: TrackPeerMap
    private lateinit var context: Context
    private lateinit var localView: SurfaceViewRenderer
    private lateinit var  natsConn: Connection
    private var count: Int = 0

    init {
        itemView.findViewById<SurfaceViewRenderer>(R.id.remote_view).apply {
            setEnableHardwareScaler(true)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        }
    }

    fun startSurfaceView() {
        if (!sinkAdded) {
//            itemView.findViewById<SurfaceViewRenderer>(R.id.remote_view).apply {
//
//                getItem(adapterPosition).videoTrack?.let { hmsVideoTrack ->
//                    init(SharedEglContext.context, null)
//                    hmsVideoTrack.addSink(this)
//                    sinkAdded = true
//                }
//            }
        }
    }

    fun stopSurfaceView() {
        // If the sink was added, AND there was a video
        //  then remove the sink and release
        itemView.findViewById<SurfaceViewRenderer>(R.id.remote_view).apply {

//            if (sinkAdded && adapterPosition != -1) {
//                getItem(adapterPosition).videoTrack?.let {
//                    it.removeSink(this)
//                    release()
//                    sinkAdded = false
//                }
//            }
        }
    }

    @UseExperimental(ExperimentalStdlibApi::class)
    fun bind(
        trackPeerMap: TrackPeerMap,
        application: Application,
        context: Context
    ) {

            val webrtcView = itemView.findViewById<SurfaceViewRenderer>(R.id.remote_view)
            localView = itemView.findViewById<SurfaceViewRenderer>(R.id.local_view)
            this.application = application
            this.webrtcView = webrtcView
            this.trackPeerMap = trackPeerMap
            this.context = context

            this.signallingClient = SignallingClient(createSignallingClientListener())



        itemView.findViewById<TextView>(R.id.peerName).text = trackPeerMap.peerID.toString()
        CoroutineScope(Dispatchers.IO).async {
            println("Nats.connect")

            val natsConnectionProperties = BasicAuthNatsConnectionProperties()
            natsConnectionProperties.host = "nats://demo.nats.io:4222"
            natsConnectionProperties.connectionName = "Nats EyeCast"
            natsConnectionProperties.connectionTimeout = 5
            natsConnectionProperties.maxReconnects = -1
            natsConnectionProperties.pingInterval = 10
            natsConnectionProperties.reconnectWait = 1
            natsConnectionProperties.password = ""
            natsConnectionProperties.username = ""


            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, TLSSocketFactory.trustAllCertificatesTrustManagers, null)

            var builder  = Options.Builder()
                .server(natsConnectionProperties.host)
                .maxReconnects(natsConnectionProperties.maxReconnects)
                .connectionName(natsConnectionProperties.connectionName)
                .sslContext(sslContext)
                .traceConnection()

            builder =
                builder.connectionListener { _: Connection?, type: ConnectionListener.Events ->
                    println(
                        "Status change $type"
                    )
                }

            builder = builder.errorListener(object : ErrorListener {
                override fun slowConsumerDetected(conn: Connection, consumer: Consumer) {
                    println("NATS connection slow consumer detected")
                }

                override fun exceptionOccurred(conn: Connection, exp: Exception) {
                    println("NATS connection exception occurred")
                    exp.printStackTrace()
                }

                override fun errorOccurred(conn: Connection, error: String) {
                    println("NATS connection error occurred $error")
                }
            })

            val op =  builder.build()
            natsConn = Nats.connect(op)

            val natsDispatcher = natsConn.createDispatcher { message ->
                println("xxxx message ${message.data.decodeToString()}")
            }

            natsDispatcher.subscribe("test_topic")
            natsConn.publish("test_topic", "Hello world".toByteArray())



            withContext(Dispatchers.Main){
                createRTCClient(application, webrtcView, trackPeerMap)
            }
        }
    }


    @UseExperimental(KtorExperimentalAPI::class, ExperimentalStdlibApi::class)
    private fun createRTCClient(
        application: Application,
        webrtcView: SurfaceViewRenderer,
        trackPeerMap: TrackPeerMap
    ) {
        println("SignallingClient createRTCClient")

        rtcClient = RTCClient(
            application,
            object : PeerConnectionObserver() {
                override fun onIceCandidate(p0: IceCandidate?) {
                    super.onIceCandidate(p0)

                    println("onIceCandidate  ${p0?.sdp}")
                    Log.v(
                        "SignallingClient",
                        "Send onIceCandidate ${p0?.sdp}"
                    )

                    val iceData = IceMessage(
                        IceData(
                            candidate = p0?.sdp!!,
                            sdpMid = p0?.sdpMid,
                            sdpMLineIndex = p0?.sdpMLineIndex
                        )
                    )

                    val iceDataParse = RTCIceCandidateInit(
                        candidate= iceData.ice.candidate,
                        sdp_mid=iceData.ice.sdpMid,
                        sdp_mline_index=iceData.ice.sdpMLineIndex,
                        username_fragment=""
                    )

                    //signallingClient.send(iceData)
                    rtcClient.addIceCandidate(p0)


                    val bytes = Gson().toJson(iceDataParse)

                    val de = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
                    println("xxxx sending thai_webrtc_ice ${bytes}")
                   // natsConn.publish("thai_webrtc_offer", de.toByteArray())
                    natsConn.publish("thai_webrtc_ice", de.toByteArray())

                }

                override fun onIceConnectionReceivingChange(p0: Boolean) {
                    Log.v(
                        "SignallingClient",
                        "onIceConnectionReceivingChange"
                    )
                }

                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
                    Log.v(
                        "SignallingClient",
                        "onIceConnectionChange  ${p0?.name}"
                    )
                }

                override fun onIceGatheringChange(onIceGatheringChange: PeerConnection.IceGatheringState?) {
                    Log.v(
                        "SignallingClient",
                        "onIceGatheringChange  ${onIceGatheringChange?.name}"
                    )

                    if(onIceGatheringChange == PeerConnection.IceGatheringState.COMPLETE) {
                       // rtcClient.call(sdpObserver);
                    }
                }

                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
                    Log.v(
                        "SignallingClient",
                        "onSignalingChange  ${p0?.name}"
                    )
                }

                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
                    Log.v(
                        "SignallingClient",
                        "onIceCandidatesRemoved  ${p0?.size}"
                    )
                }

                override fun onRemoveStream(p0: MediaStream?) {
                    Log.v(
                        "SignallingClient",
                        "onRemoveStream"
                    )
                    webrtcView.release()
                }

                override fun onRenegotiationNeeded() {
                    Log.v(
                        "SignallingClient",
                        "onRenegotiationNeeded"
                    )
                }

                override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
                    Log.v(
                        "SignallingClient",
                        "onAddTrack"
                    )
                }

                override fun onAddStream(p0: MediaStream?) {
                    super.onAddStream(p0)
                    Log.v(
                        "SignallingClient",
                        "onAddStream"
                    )
                    p0?.videoTracks?.get(0)?.addSink(webrtcView)
                }
            }, trackPeerMap.rootEglBase
        )


        //rtcClient.initSurfaceView(webrtcView)
        //rtcClient.initSurfaceView(local_view)
        //rtcClient.startLocalVideoCapture(local_view)
//        webrtcView?.run {
//            setMirror(true)
//            setEnableHardwareScaler(true)
//            //init(rootEglBase.eglBaseContext, null)
//        }

        //rtcClient.customInitSurfaceView(webrtcView)
        rtcClient.initSurfaceView(webrtcView)

        rtcClient.addTransceiver()
        rtcClient.getStats()
       // rtcClient.initSurfaceView(localView)
       // rtcClient.startLocalVideoCapture(localView)
        rtcClient.call(sdpObserver)
        //sendHeartBeat()

        CoroutineScope(Dispatchers.IO).async {

            val natsDispatcher = natsConn.createDispatcher { message ->
                println("xxxx message ${message.data.decodeToString()}")
                val bytes = message.data.toUByteArray()
                val de = String(Base64.decode(bytes.toByteArray(), Base64.NO_WRAP))
                println("xxxx message answer ${de}")
                val sdpDataJSON: SDPData = Gson().fromJson(de, SDPData::class.java)

                val description = SessionDescription(SessionDescription.Type.ANSWER, sdpDataJSON.sdp)
                rtcClient.onRemoteSessionReceived(description)
            }

            natsDispatcher.subscribe("thai_webrtc_answer")
        }


        CoroutineScope(Dispatchers.IO).async {
            val natsDispatcher = natsConn.createDispatcher { message ->
                println("xxxx message thai_webrtc_ice_offer ${message.data.decodeToString()}")
                val bytes = message.data.toUByteArray()
                val de = String(Base64.decode(bytes.toByteArray(), Base64.NO_WRAP))
                println("xxxx message thai_webrtc_ice_offer ${de}")
                val rtcIceCandidateInit: RTCIceCandidateInit = Gson().fromJson(de, RTCIceCandidateInit::class.java)

                val iceCandidate =
                    IceCandidate(
                        rtcIceCandidateInit.sdp_mid,
                        rtcIceCandidateInit.sdp_mline_index,
                        rtcIceCandidateInit.candidate
                    )

              rtcClient.addIceCandidate(iceCandidate)
            }
            natsDispatcher.subscribe("thai_webrtc_ice_offer")
        }


        CoroutineScope(Dispatchers.IO).async {

            val natsDispatcher = natsConn.createDispatcher { message ->
                println("xxxx message ${message.data.decodeToString()}")
            }
            natsDispatcher.subscribe("thai_webrtc_offer")
        }

    }


    private val sdpObserver = object : AppSdpObserver() {
        override fun onCreateSuccess(p0: SessionDescription?) {
            super.onCreateSuccess(p0)
            println("sdp onCreateSuccess ${p0?.description.toString()}")

            val sdpData = if (p0?.type == SessionDescription.Type.OFFER) {
                Log.v("SignallingClient", "Send offer ${p0?.description}")

                //SDPMessage(SDPData(type = "offer", sdp = p0.description!!))

                SDPData(type = "offer", sdp = p0.description!!)
            } else {
                Log.v("SignallingClient", "Send anwser ${p0?.description}")
                //SDPMessage(SDPData(type = "answer", sdp = p0?.description!!))
                SDPData(type = "answer", sdp = p0?.description!!)
            }

            val bytes = Gson().toJson(sdpData)
            val de = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
            println("SignallingClient Base64 $de")


            CoroutineScope(Dispatchers.IO).async {

                if(count != 0) {
                    println("xxxx sending offer $bytes")
                    //val dummy = "eyJ0eXBlIjoib2ZmZXIiLCJzZHAiOiJ2PTBcclxubz0tIDIxMzU0NTEyOTA3MzY2NjQ4OTIgMiBJTiBJUDQgMTI3LjAuMC4xXHJcbnM9LVxyXG50PTAgMFxyXG5hPWdyb3VwOkJVTkRMRSAwIDEgMlxyXG5hPWV4dG1hcC1hbGxvdy1taXhlZFxyXG5hPW1zaWQtc2VtYW50aWM6IFdNU1xyXG5tPWF1ZGlvIDYxNjE5IFVEUC9UTFMvUlRQL1NBVlBGIDExMSA2MyAxMDMgMTA0IDkgMCA4IDEwNiAxMDUgMTMgMTEwIDExMiAxMTMgMTI2XHJcbmM9SU4gSVA0IDEwMy4xMjcuMjA2LjEzNVxyXG5hPXJ0Y3A6OSBJTiBJUDQgMC4wLjAuMFxyXG5hPWNhbmRpZGF0ZToyMTExNTY4MjEgMSB1ZHAgMjExMzkzNzE1MSAyZTJiOTk3Zi05NzQxLTRhYjUtYmNjYy0wOGRmYzY4ODJhMGYubG9jYWwgNTU5ODYgdHlwIGhvc3QgZ2VuZXJhdGlvbiAwIG5ldHdvcmstY29zdCA5OTlcclxuYT1jYW5kaWRhdGU6Mzk4NzcxODA0NCAxIHVkcCAyMTEzOTM5NzExIDFhMjM4NTEyLTg5ZGMtNDVmOC05YmNhLTlmNTVkYTgwNTMzMy5sb2NhbCA1NTgzNyB0eXAgaG9zdCBnZW5lcmF0aW9uIDAgbmV0d29yay1jb3N0IDk5OVxyXG5hPWNhbmRpZGF0ZTo4NDIxNjMwNDkgMSB1ZHAgMTY3NzcyOTUzNSAxMTcuMy4yMjIuOTAgNTU5ODYgdHlwIHNyZmx4IHJhZGRyIDAuMC4wLjAgcnBvcnQgMCBnZW5lcmF0aW9uIDAgbmV0d29yay1jb3N0IDk5OVxyXG5hPWNhbmRpZGF0ZToyMDk3NjQ2MDc2IDEgdWRwIDE2Nzg1MTUxIDEwMy4xMjcuMjA2LjEzNSA2MTYxOSB0eXAgcmVsYXkgcmFkZHIgMTE3LjMuMjIyLjkwIHJwb3J0IDY1MDI5IGdlbmVyYXRpb24gMCBuZXR3b3JrLWNvc3QgOTk5XHJcbmE9aWNlLXVmcmFnOnNCS0pcclxuYT1pY2UtcHdkOkgxVG03a0hPWW03aTR3MGcwRWZWcFE3SVxyXG5hPWljZS1vcHRpb25zOnRyaWNrbGVcclxuYT1maW5nZXJwcmludDpzaGEtMjU2IDBCOkRDOjUzOjE1OjVFOjlGOjAwOjkwOjRFOjlEOkU2OkYwOjNCOjIyOjMxOkExOjZGOjQ4OjBFOjY1OjZGOkVBOkMwOjU1OjMyOjUxOjdCOkRGOjRDOkYzOkM5OjQ4XHJcbmE9c2V0dXA6YWN0cGFzc1xyXG5hPW1pZDowXHJcbmE9ZXh0bWFwOjEgdXJuOmlldGY6cGFyYW1zOnJ0cC1oZHJleHQ6c3NyYy1hdWRpby1sZXZlbFxyXG5hPWV4dG1hcDoyIGh0dHA6Ly93d3cud2VicnRjLm9yZy9leHBlcmltZW50cy9ydHAtaGRyZXh0L2Ficy1zZW5kLXRpbWVcclxuYT1leHRtYXA6MyBodHRwOi8vd3d3LmlldGYub3JnL2lkL2RyYWZ0LWhvbG1lci1ybWNhdC10cmFuc3BvcnQtd2lkZS1jYy1leHRlbnNpb25zLTAxXHJcbmE9ZXh0bWFwOjQgdXJuOmlldGY6cGFyYW1zOnJ0cC1oZHJleHQ6c2RlczptaWRcclxuYT1yZWN2b25seVxyXG5hPXJ0Y3AtbXV4XHJcbmE9cnRwbWFwOjExMSBvcHVzLzQ4MDAwLzJcclxuYT1ydGNwLWZiOjExMSB0cmFuc3BvcnQtY2NcclxuYT1mbXRwOjExMSBtaW5wdGltZT0xMDt1c2VpbmJhbmRmZWM9MVxyXG5hPXJ0cG1hcDo2MyByZWQvNDgwMDAvMlxyXG5hPWZtdHA6NjMgMTExLzExMVxyXG5hPXJ0cG1hcDoxMDMgSVNBQy8xNjAwMFxyXG5hPXJ0cG1hcDoxMDQgSVNBQy8zMjAwMFxyXG5hPXJ0cG1hcDo5IEc3MjIvODAwMFxyXG5hPXJ0cG1hcDowIFBDTVUvODAwMFxyXG5hPXJ0cG1hcDo4IFBDTUEvODAwMFxyXG5hPXJ0cG1hcDoxMDYgQ04vMzIwMDBcclxuYT1ydHBtYXA6MTA1IENOLzE2MDAwXHJcbmE9cnRwbWFwOjEzIENOLzgwMDBcclxuYT1ydHBtYXA6MTEwIHRlbGVwaG9uZS1ldmVudC80ODAwMFxyXG5hPXJ0cG1hcDoxMTIgdGVsZXBob25lLWV2ZW50LzMyMDAwXHJcbmE9cnRwbWFwOjExMyB0ZWxlcGhvbmUtZXZlbnQvMTYwMDBcclxuYT1ydHBtYXA6MTI2IHRlbGVwaG9uZS1ldmVudC84MDAwXHJcbm09dmlkZW8gNTU4NDMgVURQL1RMUy9SVFAvU0FWUEYgOTYgOTcgOTggOTkgMTAwIDEwMSAxMDIgMTIyIDEyNyAxMjEgMTI1IDEwNyAxMDggMTA5IDEyNCAxMjAgMTIzIDExOSAzNSAzNiAzNyAzOCAzOSA0MCA0MSA0MiAxMTQgMTE1IDExNiAxMTcgMTE4IDQzXHJcbmM9SU4gSVA0IDEwMy4xMjcuMjA2LjEzNVxyXG5hPXJ0Y3A6OSBJTiBJUDQgMC4wLjAuMFxyXG5hPWNhbmRpZGF0ZToyMTExNTY4MjEgMSB1ZHAgMjExMzkzNzE1MSAyZTJiOTk3Zi05NzQxLTRhYjUtYmNjYy0wOGRmYzY4ODJhMGYubG9jYWwgNTEwMTUgdHlwIGhvc3QgZ2VuZXJhdGlvbiAwIG5ldHdvcmstY29zdCA5OTlcclxuYT1jYW5kaWRhdGU6Mzk4NzcxODA0NCAxIHVkcCAyMTEzOTM5NzExIDFhMjM4NTEyLTg5ZGMtNDVmOC05YmNhLTlmNTVkYTgwNTMzMy5sb2NhbCA2NTQ3NiB0eXAgaG9zdCBnZW5lcmF0aW9uIDAgbmV0d29yay1jb3N0IDk5OVxyXG5hPWNhbmRpZGF0ZTo4NDIxNjMwNDkgMSB1ZHAgMTY3NzcyOTUzNSAxMTcuMy4yMjIuOTAgNTEwMTUgdHlwIHNyZmx4IHJhZGRyIDAuMC4wLjAgcnBvcnQgMCBnZW5lcmF0aW9uIDAgbmV0d29yay1jb3N0IDk5OVxyXG5hPWNhbmRpZGF0ZToyMDk3NjQ2MDc2IDEgdWRwIDE2Nzg1MTUxIDEwMy4xMjcuMjA2LjEzNSA1NTg0MyB0eXAgcmVsYXkgcmFkZHIgMTE3LjMuMjIyLjkwIHJwb3J0IDY1MDMwIGdlbmVyYXRpb24gMCBuZXR3b3JrLWNvc3QgOTk5XHJcbmE9aWNlLXVmcmFnOnNCS0pcclxuYT1pY2UtcHdkOkgxVG03a0hPWW03aTR3MGcwRWZWcFE3SVxyXG5hPWljZS1vcHRpb25zOnRyaWNrbGVcclxuYT1maW5nZXJwcmludDpzaGEtMjU2IDBCOkRDOjUzOjE1OjVFOjlGOjAwOjkwOjRFOjlEOkU2OkYwOjNCOjIyOjMxOkExOjZGOjQ4OjBFOjY1OjZGOkVBOkMwOjU1OjMyOjUxOjdCOkRGOjRDOkYzOkM5OjQ4XHJcbmE9c2V0dXA6YWN0cGFzc1xyXG5hPW1pZDoxXHJcbmE9ZXh0bWFwOjE0IHVybjppZXRmOnBhcmFtczpydHAtaGRyZXh0OnRvZmZzZXRcclxuYT1leHRtYXA6MiBodHRwOi8vd3d3LndlYnJ0Yy5vcmcvZXhwZXJpbWVudHMvcnRwLWhkcmV4dC9hYnMtc2VuZC10aW1lXHJcbmE9ZXh0bWFwOjEzIHVybjozZ3BwOnZpZGVvLW9yaWVudGF0aW9uXHJcbmE9ZXh0bWFwOjMgaHR0cDovL3d3dy5pZXRmLm9yZy9pZC9kcmFmdC1ob2xtZXItcm1jYXQtdHJhbnNwb3J0LXdpZGUtY2MtZXh0ZW5zaW9ucy0wMVxyXG5hPWV4dG1hcDo1IGh0dHA6Ly93d3cud2VicnRjLm9yZy9leHBlcmltZW50cy9ydHAtaGRyZXh0L3BsYXlvdXQtZGVsYXlcclxuYT1leHRtYXA6NiBodHRwOi8vd3d3LndlYnJ0Yy5vcmcvZXhwZXJpbWVudHMvcnRwLWhkcmV4dC92aWRlby1jb250ZW50LXR5cGVcclxuYT1leHRtYXA6NyBodHRwOi8vd3d3LndlYnJ0Yy5vcmcvZXhwZXJpbWVudHMvcnRwLWhkcmV4dC92aWRlby10aW1pbmdcclxuYT1leHRtYXA6OCBodHRwOi8vd3d3LndlYnJ0Yy5vcmcvZXhwZXJpbWVudHMvcnRwLWhkcmV4dC9jb2xvci1zcGFjZVxyXG5hPWV4dG1hcDo0IHVybjppZXRmOnBhcmFtczpydHAtaGRyZXh0OnNkZXM6bWlkXHJcbmE9ZXh0bWFwOjEwIHVybjppZXRmOnBhcmFtczpydHAtaGRyZXh0OnNkZXM6cnRwLXN0cmVhbS1pZFxyXG5hPWV4dG1hcDoxMSB1cm46aWV0ZjpwYXJhbXM6cnRwLWhkcmV4dDpzZGVzOnJlcGFpcmVkLXJ0cC1zdHJlYW0taWRcclxuYT1yZWN2b25seVxyXG5hPXJ0Y3AtbXV4XHJcbmE9cnRjcC1yc2l6ZVxyXG5hPXJ0cG1hcDo5NiBWUDgvOTAwMDBcclxuYT1ydGNwLWZiOjk2IGdvb2ctcmVtYlxyXG5hPXJ0Y3AtZmI6OTYgdHJhbnNwb3J0LWNjXHJcbmE9cnRjcC1mYjo5NiBjY20gZmlyXHJcbmE9cnRjcC1mYjo5NiBuYWNrXHJcbmE9cnRjcC1mYjo5NiBuYWNrIHBsaVxyXG5hPXJ0cG1hcDo5NyBydHgvOTAwMDBcclxuYT1mbXRwOjk3IGFwdD05NlxyXG5hPXJ0cG1hcDo5OCBWUDkvOTAwMDBcclxuYT1ydGNwLWZiOjk4IGdvb2ctcmVtYlxyXG5hPXJ0Y3AtZmI6OTggdHJhbnNwb3J0LWNjXHJcbmE9cnRjcC1mYjo5OCBjY20gZmlyXHJcbmE9cnRjcC1mYjo5OCBuYWNrXHJcbmE9cnRjcC1mYjo5OCBuYWNrIHBsaVxyXG5hPWZtdHA6OTggcHJvZmlsZS1pZD0wXHJcbmE9cnRwbWFwOjk5IHJ0eC85MDAwMFxyXG5hPWZtdHA6OTkgYXB0PTk4XHJcbmE9cnRwbWFwOjEwMCBWUDkvOTAwMDBcclxuYT1ydGNwLWZiOjEwMCBnb29nLXJlbWJcclxuYT1ydGNwLWZiOjEwMCB0cmFuc3BvcnQtY2NcclxuYT1ydGNwLWZiOjEwMCBjY20gZmlyXHJcbmE9cnRjcC1mYjoxMDAgbmFja1xyXG5hPXJ0Y3AtZmI6MTAwIG5hY2sgcGxpXHJcbmE9Zm10cDoxMDAgcHJvZmlsZS1pZD0yXHJcbmE9cnRwbWFwOjEwMSBydHgvOTAwMDBcclxuYT1mbXRwOjEwMSBhcHQ9MTAwXHJcbmE9cnRwbWFwOjEwMiBWUDkvOTAwMDBcclxuYT1ydGNwLWZiOjEwMiBnb29nLXJlbWJcclxuYT1ydGNwLWZiOjEwMiB0cmFuc3BvcnQtY2NcclxuYT1ydGNwLWZiOjEwMiBjY20gZmlyXHJcbmE9cnRjcC1mYjoxMDIgbmFja1xyXG5hPXJ0Y3AtZmI6MTAyIG5hY2sgcGxpXHJcbmE9Zm10cDoxMDIgcHJvZmlsZS1pZD0xXHJcbmE9cnRwbWFwOjEyMiBydHgvOTAwMDBcclxuYT1mbXRwOjEyMiBhcHQ9MTAyXHJcbmE9cnRwbWFwOjEyNyBIMjY0LzkwMDAwXHJcbmE9cnRjcC1mYjoxMjcgZ29vZy1yZW1iXHJcbmE9cnRjcC1mYjoxMjcgdHJhbnNwb3J0LWNjXHJcbmE9cnRjcC1mYjoxMjcgY2NtIGZpclxyXG5hPXJ0Y3AtZmI6MTI3IG5hY2tcclxuYT1ydGNwLWZiOjEyNyBuYWNrIHBsaVxyXG5hPWZtdHA6MTI3IGxldmVsLWFzeW1tZXRyeS1hbGxvd2VkPTE7cGFja2V0aXphdGlvbi1tb2RlPTE7cHJvZmlsZS1sZXZlbC1pZD00MjAwMWZcclxuYT1ydHBtYXA6MTIxIHJ0eC85MDAwMFxyXG5hPWZtdHA6MTIxIGFwdD0xMjdcclxuYT1ydHBtYXA6MTI1IEgyNjQvOTAwMDBcclxuYT1ydGNwLWZiOjEyNSBnb29nLXJlbWJcclxuYT1ydGNwLWZiOjEyNSB0cmFuc3BvcnQtY2NcclxuYT1ydGNwLWZiOjEyNSBjY20gZmlyXHJcbmE9cnRjcC1mYjoxMjUgbmFja1xyXG5hPXJ0Y3AtZmI6MTI1IG5hY2sgcGxpXHJcbmE9Zm10cDoxMjUgbGV2ZWwtYXN5bW1ldHJ5LWFsbG93ZWQ9MTtwYWNrZXRpemF0aW9uLW1vZGU9MDtwcm9maWxlLWxldmVsLWlkPTQyMDAxZlxyXG5hPXJ0cG1hcDoxMDcgcnR4LzkwMDAwXHJcbmE9Zm10cDoxMDcgYXB0PTEyNVxyXG5hPXJ0cG1hcDoxMDggSDI2NC85MDAwMFxyXG5hPXJ0Y3AtZmI6MTA4IGdvb2ctcmVtYlxyXG5hPXJ0Y3AtZmI6MTA4IHRyYW5zcG9ydC1jY1xyXG5hPXJ0Y3AtZmI6MTA4IGNjbSBmaXJcclxuYT1ydGNwLWZiOjEwOCBuYWNrXHJcbmE9cnRjcC1mYjoxMDggbmFjayBwbGlcclxuYT1mbXRwOjEwOCBsZXZlbC1hc3ltbWV0cnktYWxsb3dlZD0xO3BhY2tldGl6YXRpb24tbW9kZT0xO3Byb2ZpbGUtbGV2ZWwtaWQ9NDJlMDFmXHJcbmE9cnRwbWFwOjEwOSBydHgvOTAwMDBcclxuYT1mbXRwOjEwOSBhcHQ9MTA4XHJcbmE9cnRwbWFwOjEyNCBIMjY0LzkwMDAwXHJcbmE9cnRjcC1mYjoxMjQgZ29vZy1yZW1iXHJcbmE9cnRjcC1mYjoxMjQgdHJhbnNwb3J0LWNjXHJcbmE9cnRjcC1mYjoxMjQgY2NtIGZpclxyXG5hPXJ0Y3AtZmI6MTI0IG5hY2tcclxuYT1ydGNwLWZiOjEyNCBuYWNrIHBsaVxyXG5hPWZtdHA6MTI0IGxldmVsLWFzeW1tZXRyeS1hbGxvd2VkPTE7cGFja2V0aXphdGlvbi1tb2RlPTA7cHJvZmlsZS1sZXZlbC1pZD00MmUwMWZcclxuYT1ydHBtYXA6MTIwIHJ0eC85MDAwMFxyXG5hPWZtdHA6MTIwIGFwdD0xMjRcclxuYT1ydHBtYXA6MTIzIEgyNjQvOTAwMDBcclxuYT1ydGNwLWZiOjEyMyBnb29nLXJlbWJcclxuYT1ydGNwLWZiOjEyMyB0cmFuc3BvcnQtY2NcclxuYT1ydGNwLWZiOjEyMyBjY20gZmlyXHJcbmE9cnRjcC1mYjoxMjMgbmFja1xyXG5hPXJ0Y3AtZmI6MTIzIG5hY2sgcGxpXHJcbmE9Zm10cDoxMjMgbGV2ZWwtYXN5bW1ldHJ5LWFsbG93ZWQ9MTtwYWNrZXRpemF0aW9uLW1vZGU9MTtwcm9maWxlLWxldmVsLWlkPTRkMDAxZlxyXG5hPXJ0cG1hcDoxMTkgcnR4LzkwMDAwXHJcbmE9Zm10cDoxMTkgYXB0PTEyM1xyXG5hPXJ0cG1hcDozNSBIMjY0LzkwMDAwXHJcbmE9cnRjcC1mYjozNSBnb29nLXJlbWJcclxuYT1ydGNwLWZiOjM1IHRyYW5zcG9ydC1jY1xyXG5hPXJ0Y3AtZmI6MzUgY2NtIGZpclxyXG5hPXJ0Y3AtZmI6MzUgbmFja1xyXG5hPXJ0Y3AtZmI6MzUgbmFjayBwbGlcclxuYT1mbXRwOjM1IGxldmVsLWFzeW1tZXRyeS1hbGxvd2VkPTE7cGFja2V0aXphdGlvbi1tb2RlPTA7cHJvZmlsZS1sZXZlbC1pZD00ZDAwMWZcclxuYT1ydHBtYXA6MzYgcnR4LzkwMDAwXHJcbmE9Zm10cDozNiBhcHQ9MzVcclxuYT1ydHBtYXA6MzcgSDI2NC85MDAwMFxyXG5hPXJ0Y3AtZmI6MzcgZ29vZy1yZW1iXHJcbmE9cnRjcC1mYjozNyB0cmFuc3BvcnQtY2NcclxuYT1ydGNwLWZiOjM3IGNjbSBmaXJcclxuYT1ydGNwLWZiOjM3IG5hY2tcclxuYT1ydGNwLWZiOjM3IG5hY2sgcGxpXHJcbmE9Zm10cDozNyBsZXZlbC1hc3ltbWV0cnktYWxsb3dlZD0xO3BhY2tldGl6YXRpb24tbW9kZT0xO3Byb2ZpbGUtbGV2ZWwtaWQ9ZjQwMDFmXHJcbmE9cnRwbWFwOjM4IHJ0eC85MDAwMFxyXG5hPWZtdHA6MzggYXB0PTM3XHJcbmE9cnRwbWFwOjM5IEgyNjQvOTAwMDBcclxuYT1ydGNwLWZiOjM5IGdvb2ctcmVtYlxyXG5hPXJ0Y3AtZmI6MzkgdHJhbnNwb3J0LWNjXHJcbmE9cnRjcC1mYjozOSBjY20gZmlyXHJcbmE9cnRjcC1mYjozOSBuYWNrXHJcbmE9cnRjcC1mYjozOSBuYWNrIHBsaVxyXG5hPWZtdHA6MzkgbGV2ZWwtYXN5bW1ldHJ5LWFsbG93ZWQ9MTtwYWNrZXRpemF0aW9uLW1vZGU9MDtwcm9maWxlLWxldmVsLWlkPWY0MDAxZlxyXG5hPXJ0cG1hcDo0MCBydHgvOTAwMDBcclxuYT1mbXRwOjQwIGFwdD0zOVxyXG5hPXJ0cG1hcDo0MSBBVjEvOTAwMDBcclxuYT1ydGNwLWZiOjQxIGdvb2ctcmVtYlxyXG5hPXJ0Y3AtZmI6NDEgdHJhbnNwb3J0LWNjXHJcbmE9cnRjcC1mYjo0MSBjY20gZmlyXHJcbmE9cnRjcC1mYjo0MSBuYWNrXHJcbmE9cnRjcC1mYjo0MSBuYWNrIHBsaVxyXG5hPXJ0cG1hcDo0MiBydHgvOTAwMDBcclxuYT1mbXRwOjQyIGFwdD00MVxyXG5hPXJ0cG1hcDoxMTQgSDI2NC85MDAwMFxyXG5hPXJ0Y3AtZmI6MTE0IGdvb2ctcmVtYlxyXG5hPXJ0Y3AtZmI6MTE0IHRyYW5zcG9ydC1jY1xyXG5hPXJ0Y3AtZmI6MTE0IGNjbSBmaXJcclxuYT1ydGNwLWZiOjExNCBuYWNrXHJcbmE9cnRjcC1mYjoxMTQgbmFjayBwbGlcclxuYT1mbXRwOjExNCBsZXZlbC1hc3ltbWV0cnktYWxsb3dlZD0xO3BhY2tldGl6YXRpb24tbW9kZT0xO3Byb2ZpbGUtbGV2ZWwtaWQ9NjQwMDFmXHJcbmE9cnRwbWFwOjExNSBydHgvOTAwMDBcclxuYT1mbXRwOjExNSBhcHQ9MTE0XHJcbmE9cnRwbWFwOjExNiByZWQvOTAwMDBcclxuYT1ydHBtYXA6MTE3IHJ0eC85MDAwMFxyXG5hPWZtdHA6MTE3IGFwdD0xMTZcclxuYT1ydHBtYXA6MTE4IHVscGZlYy85MDAwMFxyXG5hPXJ0cG1hcDo0MyBmbGV4ZmVjLTAzLzkwMDAwXHJcbmE9cnRjcC1mYjo0MyBnb29nLXJlbWJcclxuYT1ydGNwLWZiOjQzIHRyYW5zcG9ydC1jY1xyXG5hPWZtdHA6NDMgcmVwYWlyLXdpbmRvdz0xMDAwMDAwMFxyXG5tPXZpZGVvIDU4ODI1IFVEUC9UTFMvUlRQL1NBVlBGIDk2IDk3IDk4IDk5IDEwMCAxMDEgMTAyIDEyMiAxMjcgMTIxIDEyNSAxMDcgMTA4IDEwOSAxMjQgMTIwIDEyMyAxMTkgMzUgMzYgMzcgMzggMzkgNDAgNDEgNDIgMTE0IDExNSAxMTYgMTE3IDExOCA0M1xyXG5jPUlOIElQNCAxMDMuMTI3LjIwNi4xMzVcclxuYT1ydGNwOjkgSU4gSVA0IDAuMC4wLjBcclxuYT1jYW5kaWRhdGU6MjExMTU2ODIxIDEgdWRwIDIxMTM5MzcxNTEgMmUyYjk5N2YtOTc0MS00YWI1LWJjY2MtMDhkZmM2ODgyYTBmLmxvY2FsIDU3MzcwIHR5cCBob3N0IGdlbmVyYXRpb24gMCBuZXR3b3JrLWNvc3QgOTk5XHJcbmE9Y2FuZGlkYXRlOjM5ODc3MTgwNDQgMSB1ZHAgMjExMzkzOTcxMSAxYTIzODUxMi04OWRjLTQ1ZjgtOWJjYS05ZjU1ZGE4MDUzMzMubG9jYWwgNjI0OTggdHlwIGhvc3QgZ2VuZXJhdGlvbiAwIG5ldHdvcmstY29zdCA5OTlcclxuYT1jYW5kaWRhdGU6ODQyMTYzMDQ5IDEgdWRwIDE2Nzc3Mjk1MzUgMTE3LjMuMjIyLjkwIDU3MzcwIHR5cCBzcmZseCByYWRkciAwLjAuMC4wIHJwb3J0IDAgZ2VuZXJhdGlvbiAwIG5ldHdvcmstY29zdCA5OTlcclxuYT1jYW5kaWRhdGU6MjA5NzY0NjA3NiAxIHVkcCAxNjc4NTE1MSAxMDMuMTI3LjIwNi4xMzUgNTg4MjUgdHlwIHJlbGF5IHJhZGRyIDExNy4zLjIyMi45MCBycG9ydCA2NTAzMSBnZW5lcmF0aW9uIDAgbmV0d29yay1jb3N0IDk5OVxyXG5hPWljZS11ZnJhZzpzQktKXHJcbmE9aWNlLXB3ZDpIMVRtN2tIT1ltN2k0dzBnMEVmVnBRN0lcclxuYT1pY2Utb3B0aW9uczp0cmlja2xlXHJcbmE9ZmluZ2VycHJpbnQ6c2hhLTI1NiAwQjpEQzo1MzoxNTo1RTo5RjowMDo5MDo0RTo5RDpFNjpGMDozQjoyMjozMTpBMTo2Rjo0ODowRTo2NTo2RjpFQTpDMDo1NTozMjo1MTo3QjpERjo0QzpGMzpDOTo0OFxyXG5hPXNldHVwOmFjdHBhc3NcclxuYT1taWQ6MlxyXG5hPWV4dG1hcDoxNCB1cm46aWV0ZjpwYXJhbXM6cnRwLWhkcmV4dDp0b2Zmc2V0XHJcbmE9ZXh0bWFwOjIgaHR0cDovL3d3dy53ZWJydGMub3JnL2V4cGVyaW1lbnRzL3J0cC1oZHJleHQvYWJzLXNlbmQtdGltZVxyXG5hPWV4dG1hcDoxMyB1cm46M2dwcDp2aWRlby1vcmllbnRhdGlvblxyXG5hPWV4dG1hcDozIGh0dHA6Ly93d3cuaWV0Zi5vcmcvaWQvZHJhZnQtaG9sbWVyLXJtY2F0LXRyYW5zcG9ydC13aWRlLWNjLWV4dGVuc2lvbnMtMDFcclxuYT1leHRtYXA6NSBodHRwOi8vd3d3LndlYnJ0Yy5vcmcvZXhwZXJpbWVudHMvcnRwLWhkcmV4dC9wbGF5b3V0LWRlbGF5XHJcbmE9ZXh0bWFwOjYgaHR0cDovL3d3dy53ZWJydGMub3JnL2V4cGVyaW1lbnRzL3J0cC1oZHJleHQvdmlkZW8tY29udGVudC10eXBlXHJcbmE9ZXh0bWFwOjcgaHR0cDovL3d3dy53ZWJydGMub3JnL2V4cGVyaW1lbnRzL3J0cC1oZHJleHQvdmlkZW8tdGltaW5nXHJcbmE9ZXh0bWFwOjggaHR0cDovL3d3dy53ZWJydGMub3JnL2V4cGVyaW1lbnRzL3J0cC1oZHJleHQvY29sb3Itc3BhY2VcclxuYT1leHRtYXA6NCB1cm46aWV0ZjpwYXJhbXM6cnRwLWhkcmV4dDpzZGVzOm1pZFxyXG5hPWV4dG1hcDoxMCB1cm46aWV0ZjpwYXJhbXM6cnRwLWhkcmV4dDpzZGVzOnJ0cC1zdHJlYW0taWRcclxuYT1leHRtYXA6MTEgdXJuOmlldGY6cGFyYW1zOnJ0cC1oZHJleHQ6c2RlczpyZXBhaXJlZC1ydHAtc3RyZWFtLWlkXHJcbmE9cmVjdm9ubHlcclxuYT1ydGNwLW11eFxyXG5hPXJ0Y3AtcnNpemVcclxuYT1ydHBtYXA6OTYgVlA4LzkwMDAwXHJcbmE9cnRjcC1mYjo5NiBnb29nLXJlbWJcclxuYT1ydGNwLWZiOjk2IHRyYW5zcG9ydC1jY1xyXG5hPXJ0Y3AtZmI6OTYgY2NtIGZpclxyXG5hPXJ0Y3AtZmI6OTYgbmFja1xyXG5hPXJ0Y3AtZmI6OTYgbmFjayBwbGlcclxuYT1ydHBtYXA6OTcgcnR4LzkwMDAwXHJcbmE9Zm10cDo5NyBhcHQ9OTZcclxuYT1ydHBtYXA6OTggVlA5LzkwMDAwXHJcbmE9cnRjcC1mYjo5OCBnb29nLXJlbWJcclxuYT1ydGNwLWZiOjk4IHRyYW5zcG9ydC1jY1xyXG5hPXJ0Y3AtZmI6OTggY2NtIGZpclxyXG5hPXJ0Y3AtZmI6OTggbmFja1xyXG5hPXJ0Y3AtZmI6OTggbmFjayBwbGlcclxuYT1mbXRwOjk4IHByb2ZpbGUtaWQ9MFxyXG5hPXJ0cG1hcDo5OSBydHgvOTAwMDBcclxuYT1mbXRwOjk5IGFwdD05OFxyXG5hPXJ0cG1hcDoxMDAgVlA5LzkwMDAwXHJcbmE9cnRjcC1mYjoxMDAgZ29vZy1yZW1iXHJcbmE9cnRjcC1mYjoxMDAgdHJhbnNwb3J0LWNjXHJcbmE9cnRjcC1mYjoxMDAgY2NtIGZpclxyXG5hPXJ0Y3AtZmI6MTAwIG5hY2tcclxuYT1ydGNwLWZiOjEwMCBuYWNrIHBsaVxyXG5hPWZtdHA6MTAwIHByb2ZpbGUtaWQ9MlxyXG5hPXJ0cG1hcDoxMDEgcnR4LzkwMDAwXHJcbmE9Zm10cDoxMDEgYXB0PTEwMFxyXG5hPXJ0cG1hcDoxMDIgVlA5LzkwMDAwXHJcbmE9cnRjcC1mYjoxMDIgZ29vZy1yZW1iXHJcbmE9cnRjcC1mYjoxMDIgdHJhbnNwb3J0LWNjXHJcbmE9cnRjcC1mYjoxMDIgY2NtIGZpclxyXG5hPXJ0Y3AtZmI6MTAyIG5hY2tcclxuYT1ydGNwLWZiOjEwMiBuYWNrIHBsaVxyXG5hPWZtdHA6MTAyIHByb2ZpbGUtaWQ9MVxyXG5hPXJ0cG1hcDoxMjIgcnR4LzkwMDAwXHJcbmE9Zm10cDoxMjIgYXB0PTEwMlxyXG5hPXJ0cG1hcDoxMjcgSDI2NC85MDAwMFxyXG5hPXJ0Y3AtZmI6MTI3IGdvb2ctcmVtYlxyXG5hPXJ0Y3AtZmI6MTI3IHRyYW5zcG9ydC1jY1xyXG5hPXJ0Y3AtZmI6MTI3IGNjbSBmaXJcclxuYT1ydGNwLWZiOjEyNyBuYWNrXHJcbmE9cnRjcC1mYjoxMjcgbmFjayBwbGlcclxuYT1mbXRwOjEyNyBsZXZlbC1hc3ltbWV0cnktYWxsb3dlZD0xO3BhY2tldGl6YXRpb24tbW9kZT0xO3Byb2ZpbGUtbGV2ZWwtaWQ9NDIwMDFmXHJcbmE9cnRwbWFwOjEyMSBydHgvOTAwMDBcclxuYT1mbXRwOjEyMSBhcHQ9MTI3XHJcbmE9cnRwbWFwOjEyNSBIMjY0LzkwMDAwXHJcbmE9cnRjcC1mYjoxMjUgZ29vZy1yZW1iXHJcbmE9cnRjcC1mYjoxMjUgdHJhbnNwb3J0LWNjXHJcbmE9cnRjcC1mYjoxMjUgY2NtIGZpclxyXG5hPXJ0Y3AtZmI6MTI1IG5hY2tcclxuYT1ydGNwLWZiOjEyNSBuYWNrIHBsaVxyXG5hPWZtdHA6MTI1IGxldmVsLWFzeW1tZXRyeS1hbGxvd2VkPTE7cGFja2V0aXphdGlvbi1tb2RlPTA7cHJvZmlsZS1sZXZlbC1pZD00MjAwMWZcclxuYT1ydHBtYXA6MTA3IHJ0eC85MDAwMFxyXG5hPWZtdHA6MTA3IGFwdD0xMjVcclxuYT1ydHBtYXA6MTA4IEgyNjQvOTAwMDBcclxuYT1ydGNwLWZiOjEwOCBnb29nLXJlbWJcclxuYT1ydGNwLWZiOjEwOCB0cmFuc3BvcnQtY2NcclxuYT1ydGNwLWZiOjEwOCBjY20gZmlyXHJcbmE9cnRjcC1mYjoxMDggbmFja1xyXG5hPXJ0Y3AtZmI6MTA4IG5hY2sgcGxpXHJcbmE9Zm10cDoxMDggbGV2ZWwtYXN5bW1ldHJ5LWFsbG93ZWQ9MTtwYWNrZXRpemF0aW9uLW1vZGU9MTtwcm9maWxlLWxldmVsLWlkPTQyZTAxZlxyXG5hPXJ0cG1hcDoxMDkgcnR4LzkwMDAwXHJcbmE9Zm10cDoxMDkgYXB0PTEwOFxyXG5hPXJ0cG1hcDoxMjQgSDI2NC85MDAwMFxyXG5hPXJ0Y3AtZmI6MTI0IGdvb2ctcmVtYlxyXG5hPXJ0Y3AtZmI6MTI0IHRyYW5zcG9ydC1jY1xyXG5hPXJ0Y3AtZmI6MTI0IGNjbSBmaXJcclxuYT1ydGNwLWZiOjEyNCBuYWNrXHJcbmE9cnRjcC1mYjoxMjQgbmFjayBwbGlcclxuYT1mbXRwOjEyNCBsZXZlbC1hc3ltbWV0cnktYWxsb3dlZD0xO3BhY2tldGl6YXRpb24tbW9kZT0wO3Byb2ZpbGUtbGV2ZWwtaWQ9NDJlMDFmXHJcbmE9cnRwbWFwOjEyMCBydHgvOTAwMDBcclxuYT1mbXRwOjEyMCBhcHQ9MTI0XHJcbmE9cnRwbWFwOjEyMyBIMjY0LzkwMDAwXHJcbmE9cnRjcC1mYjoxMjMgZ29vZy1yZW1iXHJcbmE9cnRjcC1mYjoxMjMgdHJhbnNwb3J0LWNjXHJcbmE9cnRjcC1mYjoxMjMgY2NtIGZpclxyXG5hPXJ0Y3AtZmI6MTIzIG5hY2tcclxuYT1ydGNwLWZiOjEyMyBuYWNrIHBsaVxyXG5hPWZtdHA6MTIzIGxldmVsLWFzeW1tZXRyeS1hbGxvd2VkPTE7cGFja2V0aXphdGlvbi1tb2RlPTE7cHJvZmlsZS1sZXZlbC1pZD00ZDAwMWZcclxuYT1ydHBtYXA6MTE5IHJ0eC85MDAwMFxyXG5hPWZtdHA6MTE5IGFwdD0xMjNcclxuYT1ydHBtYXA6MzUgSDI2NC85MDAwMFxyXG5hPXJ0Y3AtZmI6MzUgZ29vZy1yZW1iXHJcbmE9cnRjcC1mYjozNSB0cmFuc3BvcnQtY2NcclxuYT1ydGNwLWZiOjM1IGNjbSBmaXJcclxuYT1ydGNwLWZiOjM1IG5hY2tcclxuYT1ydGNwLWZiOjM1IG5hY2sgcGxpXHJcbmE9Zm10cDozNSBsZXZlbC1hc3ltbWV0cnktYWxsb3dlZD0xO3BhY2tldGl6YXRpb24tbW9kZT0wO3Byb2ZpbGUtbGV2ZWwtaWQ9NGQwMDFmXHJcbmE9cnRwbWFwOjM2IHJ0eC85MDAwMFxyXG5hPWZtdHA6MzYgYXB0PTM1XHJcbmE9cnRwbWFwOjM3IEgyNjQvOTAwMDBcclxuYT1ydGNwLWZiOjM3IGdvb2ctcmVtYlxyXG5hPXJ0Y3AtZmI6MzcgdHJhbnNwb3J0LWNjXHJcbmE9cnRjcC1mYjozNyBjY20gZmlyXHJcbmE9cnRjcC1mYjozNyBuYWNrXHJcbmE9cnRjcC1mYjozNyBuYWNrIHBsaVxyXG5hPWZtdHA6MzcgbGV2ZWwtYXN5bW1ldHJ5LWFsbG93ZWQ9MTtwYWNrZXRpemF0aW9uLW1vZGU9MTtwcm9maWxlLWxldmVsLWlkPWY0MDAxZlxyXG5hPXJ0cG1hcDozOCBydHgvOTAwMDBcclxuYT1mbXRwOjM4IGFwdD0zN1xyXG5hPXJ0cG1hcDozOSBIMjY0LzkwMDAwXHJcbmE9cnRjcC1mYjozOSBnb29nLXJlbWJcclxuYT1ydGNwLWZiOjM5IHRyYW5zcG9ydC1jY1xyXG5hPXJ0Y3AtZmI6MzkgY2NtIGZpclxyXG5hPXJ0Y3AtZmI6MzkgbmFja1xyXG5hPXJ0Y3AtZmI6MzkgbmFjayBwbGlcclxuYT1mbXRwOjM5IGxldmVsLWFzeW1tZXRyeS1hbGxvd2VkPTE7cGFja2V0aXphdGlvbi1tb2RlPTA7cHJvZmlsZS1sZXZlbC1pZD1mNDAwMWZcclxuYT1ydHBtYXA6NDAgcnR4LzkwMDAwXHJcbmE9Zm10cDo0MCBhcHQ9MzlcclxuYT1ydHBtYXA6NDEgQVYxLzkwMDAwXHJcbmE9cnRjcC1mYjo0MSBnb29nLXJlbWJcclxuYT1ydGNwLWZiOjQxIHRyYW5zcG9ydC1jY1xyXG5hPXJ0Y3AtZmI6NDEgY2NtIGZpclxyXG5hPXJ0Y3AtZmI6NDEgbmFja1xyXG5hPXJ0Y3AtZmI6NDEgbmFjayBwbGlcclxuYT1ydHBtYXA6NDIgcnR4LzkwMDAwXHJcbmE9Zm10cDo0MiBhcHQ9NDFcclxuYT1ydHBtYXA6MTE0IEgyNjQvOTAwMDBcclxuYT1ydGNwLWZiOjExNCBnb29nLXJlbWJcclxuYT1ydGNwLWZiOjExNCB0cmFuc3BvcnQtY2NcclxuYT1ydGNwLWZiOjExNCBjY20gZmlyXHJcbmE9cnRjcC1mYjoxMTQgbmFja1xyXG5hPXJ0Y3AtZmI6MTE0IG5hY2sgcGxpXHJcbmE9Zm10cDoxMTQgbGV2ZWwtYXN5bW1ldHJ5LWFsbG93ZWQ9MTtwYWNrZXRpemF0aW9uLW1vZGU9MTtwcm9maWxlLWxldmVsLWlkPTY0MDAxZlxyXG5hPXJ0cG1hcDoxMTUgcnR4LzkwMDAwXHJcbmE9Zm10cDoxMTUgYXB0PTExNFxyXG5hPXJ0cG1hcDoxMTYgcmVkLzkwMDAwXHJcbmE9cnRwbWFwOjExNyBydHgvOTAwMDBcclxuYT1mbXRwOjExNyBhcHQ9MTE2XHJcbmE9cnRwbWFwOjExOCB1bHBmZWMvOTAwMDBcclxuYT1ydHBtYXA6NDMgZmxleGZlYy0wMy85MDAwMFxyXG5hPXJ0Y3AtZmI6NDMgZ29vZy1yZW1iXHJcbmE9cnRjcC1mYjo0MyB0cmFuc3BvcnQtY2NcclxuYT1mbXRwOjQzIHJlcGFpci13aW5kb3c9MTAwMDAwMDBcclxuIn0="
                    natsConn.publish("thai_webrtc_offer", de.toByteArray())
                }
            }

            count++

            signallingClient.send(de)
        }
    }


    @UseExperimental(KtorExperimentalAPI::class)
    private fun createSignallingClientListener() = object : SignallingClientListener {
        override fun onConnectionEstablished() {
            Log.v(
                "SignallingClient",
                "onConnectionEstablished"
            )
            //call_button.isClickable = true
            val newPeerId = trackPeerMap.peerID + 10
            signallingClient.send("HELLO " + newPeerId)
        }

        override fun onSessionIsOK() {
            (context as MainActivity).runOnUiThread(Runnable {
               // createRTCClient(application, webrtcView, trackPeerMap)
            })
        }

        override fun onRegisteredWithServe() {
            signallingClient.send("SESSION " +trackPeerMap.peerID)
        }

        override fun onOfferReceived(sdpData: SDPMessage) {
            Log.v(
                "SignallingClient",
                "Received onOfferReceived ${sdpData.sdp.sdp}"
            )

            val description = SessionDescription(SessionDescription.Type.OFFER, sdpData.sdp.sdp)
            rtcClient.onRemoteSessionReceived(description)
            rtcClient.answer(sdpObserver)
            //remote_view_loading.isGone = true
        }

        override fun onAnswerReceived(sdpData: SDPMessage) {
            Log.v(
                "SignallingClient",
                "Received onAnswerReceived ${sdpData.sdp.sdp}"
            )

            val description = SessionDescription(SessionDescription.Type.ANSWER, sdpData.sdp.sdp)
            rtcClient.onRemoteSessionReceived(description)
            //remote_view_loading.isGone = true
        }

        override fun onIceCandidateReceived(iceMessage: IceMessage) {
            Log.v(
                "SignallingClient",
                "Received onIceCandidateReceived ${iceMessage.ice.candidate}"
            )

            var sdpMid = iceMessage.ice.sdpMid

            println("sdpMid  $sdpMid")
            if (sdpMid == "null" || sdpMid == "" || sdpMid == null) {
                sdpMid = "0"
            }

            val iceCandidate =
                IceCandidate(
                    sdpMid,
                    iceMessage.ice.sdpMLineIndex,
                    iceMessage.ice.candidate
                )
            rtcClient.addIceCandidate(iceCandidate)
        }
    }


    @ObsoleteCoroutinesApi
    private fun sendHeartBeat() {
        val bgScope = CoroutineScope(Dispatchers.IO)

        try {
            val tickerChannel = ticker(delayMillis = 2000, initialDelayMillis = 0)

            bgScope.launch {
                for (event in tickerChannel) {
                    // the 'event' variable is of type Unit, so we don't really care about it
                    println("nat sendHeartBeat ${rtcClient.getIceGatheringState()}")
                    if(rtcClient.getIceGatheringState() == PeerConnection.IceGatheringState.COMPLETE) {
                        rtcClient.call(sdpObserver);
                    }
                }
            }
        } catch (e: Exception) {

            e.printStackTrace()
        }
    }
}

open class NatsConnectionProperties {
    lateinit var host: String
    var pingInterval: Long = 0
    //    var maxPingsOut: Int = 0
    var maxReconnects: Int = 0
    var reconnectWait: Long = 0
    var connectionTimeout: Long = 0
    lateinit var connectionName: String
}

class BasicAuthNatsConnectionProperties : NatsConnectionProperties() {
    lateinit var username: String
    lateinit var password: String
}

class StringAuthNatsConnectionProperties : NatsConnectionProperties() {
    lateinit var stringAuthHandler: AuthHandler
}