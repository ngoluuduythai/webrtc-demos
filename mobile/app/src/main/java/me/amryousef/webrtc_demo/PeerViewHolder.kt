@file:UseExperimental(ExperimentalStdlibApi::class)

package me.amryousef.webrtc_demo

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import io.ktor.util.*
import io.nats.client.Connection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.webrtc.*
import java.util.*
import com.google.gson.JsonObject


@UseExperimental(ExperimentalCoroutinesApi::class)
class PeerViewHolder(view: View, private val getItem: (Int) -> TrackPeerMap) :
    RecyclerView.ViewHolder(view) {
    private val TAG = PeerViewHolder::class.java.simpleName
    private var sinkAdded = false
    private lateinit var signallingClient: Connection
    private lateinit var rtcClient: RTCClient

    private lateinit var application: Application
    private lateinit var webrtcView: SurfaceViewRenderer
    private lateinit var trackPeerMap: TrackPeerMap
    private lateinit var context: Context

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

    @UseExperimental(KtorExperimentalAPI::class)
    fun bind(
        trackPeerMap: TrackPeerMap,
        application: Application,
        context: Context,
        natClient: NatClient
    ) {

            val webrtcView = itemView.findViewById<SurfaceViewRenderer>(R.id.remote_view)
            this.application = application
            this.webrtcView = webrtcView
            this.trackPeerMap = trackPeerMap
            this.context = context

            //this.signallingClient = SignallingClient(createSignallingClientListener())


            itemView.findViewById<TextView>(R.id.peerName).text = trackPeerMap.peerID.toString()
            createRTCClient(application, webrtcView, trackPeerMap)
            this.signallingClient = natClient.natsConn!!

        val gson = Gson()

        val natsDispatcher = signallingClient.createDispatcher { message ->
            println("xxxx message ${message.data.decodeToString()}")

            val jsonObject = gson.fromJson(message.data.decodeToString(), JsonObject::class.java)
            Log.v("SignallingClient", "Received: jsonObject $jsonObject")

                if (jsonObject.has("ice")) {
                    onIceCandidateReceived(gson.fromJson(jsonObject, IceMessage::class.java))
                } else if (jsonObject.has("sdp")){
                    val sdpData = gson.fromJson(jsonObject, SDPMessage::class.java)
                    when(sdpData.sdp.type) {
                        "offer" -> onOfferReceived(sdpData)
                        "answer" -> onAnswerReceived(sdpData)
                        else -> {
                            Log.v("SignallingClient", "Unknown SDP Received: jsonObject $jsonObject")
                        }
                    }
                } else {
                    Log.v("SignallingClient", "Unknown Received: jsonObject $jsonObject")
                }
        }

        val clientId = trackPeerMap.peerID + 10
        natsDispatcher?.subscribe("device_${clientId}")

        signallingClient.publish("cam_${trackPeerMap.peerID.toString()}", "Hello cam_${trackPeerMap.peerID.toString()}".toByteArray())
    }

     fun onOfferReceived(sdpData: SDPMessage) {
        Log.v(
            "SignallingClient",
            "Received onOfferReceived ${sdpData.sdp.sdp}"
        )

        val description = SessionDescription(SessionDescription.Type.OFFER, sdpData.sdp.sdp)
        rtcClient.onRemoteSessionReceived(description)
        rtcClient.answer(sdpObserver)
        //remote_view_loading.isGone = true
    }

     fun onAnswerReceived(sdpData: SDPMessage) {
        Log.v(
            "SignallingClient",
            "Received onAnswerReceived ${sdpData.sdp.sdp}"
        )

        val description = SessionDescription(SessionDescription.Type.ANSWER, sdpData.sdp.sdp)
        rtcClient.onRemoteSessionReceived(description)
        //remote_view_loading.isGone = true
    }

     fun onIceCandidateReceived(iceMessage: IceMessage) {
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


    @UseExperimental(KtorExperimentalAPI::class)
    private fun createRTCClient(
        application: Application,
        webrtcView: SurfaceViewRenderer,
        trackPeerMap: TrackPeerMap
    ) {
        println("createRTCClient")

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

                    //signallingClient.send(iceData)
                    val data = Gson().toJson(iceData)
                    signallingClient.publish("cam_${trackPeerMap.peerID.toString()}", data.toByteArray())

                    rtcClient.addIceCandidate(p0)

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

                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
                    Log.v(
                        "SignallingClient",
                        "onIceGatheringChange  ${p0?.name}"
                    )
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
        rtcClient.call(sdpObserver)

    }


    private val sdpObserver = object : AppSdpObserver() {
        override fun onCreateSuccess(p0: SessionDescription?) {
            super.onCreateSuccess(p0)
            println("sdp onCreateSuccess ${p0?.description.toString()}")

            val sdpData = if (p0?.type == SessionDescription.Type.OFFER) {
                Log.v("SignallingClient", "Send offer ${p0?.description}")

                SDPMessage(SDPData(type = "offer", sdp = p0.description!!))
            } else {
                Log.v("SignallingClient", "Send anwser ${p0?.description}")
                SDPMessage(SDPData(type = "answer", sdp = p0?.description!!))
            }

            val data = Gson().toJson(sdpData)
            signallingClient.publish("cam_${trackPeerMap.peerID.toString()}", data.toByteArray())
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
            //signallingClient.send("HELLO " + newPeerId)
        }

        override fun onSessionIsOK() {
            (context as MainActivity).runOnUiThread(Runnable {
                createRTCClient(application, webrtcView, trackPeerMap)
            })
        }

        override fun onRegisteredWithServe() {
           // signallingClient.send("SESSION " +trackPeerMap.peerID)
        }

        override fun onOfferReceived(description: SDPMessage) {
            TODO("Not yet implemented")
        }

        override fun onAnswerReceived(description: SDPMessage) {
            TODO("Not yet implemented")
        }

        override fun onIceCandidateReceived(iceCandidate: IceMessage) {
            TODO("Not yet implemented")
        }


    }

}