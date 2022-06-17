package me.amryousef.webrtc_demo

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.ktor.util.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.webrtc.*
import java.util.*

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

    fun bind(
        trackPeerMap: TrackPeerMap,
        application: Application,
        context: Context
    ) {

            val webrtcView = itemView.findViewById<SurfaceViewRenderer>(R.id.remote_view)
            this.application = application
            this.webrtcView = webrtcView
            this.trackPeerMap = trackPeerMap
            this.context = context

            this.signallingClient = SignallingClient(createSignallingClientListener())





        itemView.findViewById<TextView>(R.id.peerName).text = trackPeerMap.peerID.toString()
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

                    signallingClient.send(iceData)
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


            val sdpLines = p0?.description?.split("\r\n")  as MutableList<String>

            sdpLines.removeIf { it == "a=fmtp:100 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f"}
            val dataSdp = sdpLines.joinToString("\r\n")

            val sdpData = if (p0?.type == SessionDescription.Type.OFFER) {
                Log.v("SignallingClient", "Send offer ${dataSdp}")

                SDPMessage(SDPData(type = "offer", sdp = dataSdp))
            } else {
                Log.v("SignallingClient", "Send anwser ${dataSdp}")
                SDPMessage(SDPData(type = "answer", sdp = dataSdp))
            }

            signallingClient.send(sdpData)
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
                createRTCClient(application, webrtcView, trackPeerMap)
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

}