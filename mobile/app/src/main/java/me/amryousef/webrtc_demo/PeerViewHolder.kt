package me.amryousef.webrtc_demo

import android.app.Application
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
        application: Application
    ) {

            val webrtcView = itemView.findViewById<SurfaceViewRenderer>(R.id.remote_view)
            this.signallingClient = SignallingClient(createSignallingClientListener())


            createRTCClient(application, webrtcView, trackPeerMap)

        itemView.findViewById<TextView>(R.id.peerName).text = trackPeerMap.peerID.toString()
    }


    @UseExperimental(KtorExperimentalAPI::class)
    private fun createRTCClient(
        application: Application,
        webrtcView: SurfaceViewRenderer,
        trackPeerMap: TrackPeerMap
    ) {
        rtcClient = RTCClient(
            application,
            object : PeerConnectionObserver() {
                override fun onIceCandidate(p0: IceCandidate?) {
                    super.onIceCandidate(p0)
                    println("onIceCandidate  ${p0?.sdp}")
                    Log.v(
                        this@PeerViewHolder.javaClass.simpleName,
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

                override fun onAddStream(p0: MediaStream?) {
                    super.onAddStream(p0)
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
        val newPeerId = trackPeerMap.peerID + 10

        signallingClient.send("HELLO " + newPeerId)


        Thread.sleep(5000)
        signallingClient.send("SESSION " +trackPeerMap.peerID)
        rtcClient.call(sdpObserver)

    }


    private val sdpObserver = object : AppSdpObserver() {
        override fun onCreateSuccess(p0: SessionDescription?) {
            super.onCreateSuccess(p0)
            println("sdp onCreateSuccess ${p0?.description.toString()}")

            val sdpData = if (p0?.type == SessionDescription.Type.OFFER) {
                Log.v(this@PeerViewHolder.javaClass.simpleName, "Send offer ${p0?.description}")

                SDPMessage(SDPData(type = "offer", sdp = p0.description!!))
            } else {
                Log.v(this@PeerViewHolder.javaClass.simpleName, "Send anwser ${p0?.description}")
                SDPMessage(SDPData(type = "answer", sdp = p0?.description!!))
            }

            signallingClient.send(sdpData)
        }
    }


    private fun createSignallingClientListener() = object : SignallingClientListener {
        override fun onConnectionEstablished() {
            //call_button.isClickable = true
        }

        override fun onOfferReceived(sdpData: SDPMessage) {
            Log.v(
                this@PeerViewHolder.javaClass.simpleName,
                "Received onOfferReceived ${sdpData.sdp.sdp}"
            )

            val description = SessionDescription(SessionDescription.Type.OFFER, sdpData.sdp.sdp)
            rtcClient.onRemoteSessionReceived(description)
            rtcClient.answer(sdpObserver)
            //remote_view_loading.isGone = true
        }

        override fun onAnswerReceived(sdpData: SDPMessage) {
            Log.v(
                this@PeerViewHolder.javaClass.simpleName,
                "Received onAnswerReceived ${sdpData.sdp.sdp}"
            )

            val description = SessionDescription(SessionDescription.Type.ANSWER, sdpData.sdp.sdp)
            rtcClient.onRemoteSessionReceived(description)
            //remote_view_loading.isGone = true
        }

        override fun onIceCandidateReceived(iceMessage: IceMessage) {
            Log.v(
                this@PeerViewHolder.javaClass.simpleName,
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