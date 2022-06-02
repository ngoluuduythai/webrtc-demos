package me.amryousef.webrtc_demo

import android.app.Application
import android.content.Context
import android.util.Log
import org.webrtc.*
import org.webrtc.PeerConnection.RTCConfiguration
import java.util.*


class RTCClient(
    context: Application,
    observer: PeerConnection.Observer,
    private val rootEglBase: EglBase
) {

    companion object {
        private const val LOCAL_TRACK_ID = "local_track"
        private const val LOCAL_STREAM_ID = "local_track"
    }


    init {
        initPeerConnectionFactory(context)
    }

    private val iceServer = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        //PeerConnection.IceServer.builder("turn:stun.tel4vn.com:3478").createIceServer(),
        //PeerConnection.IceServer.builder("turn:stun.tel4vn.com:5349").createIceServer()

        PeerConnection.IceServer
            .builder("turn:turn..com:5349?transport=tcp")
            .setUsername("")
            .setPassword(".COM")
            .setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK)
            .createIceServer()
    )

    private val peerConnectionFactory by lazy { buildPeerConnectionFactory() }
    private val videoCapturer by lazy { getVideoCapturer(context) }
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private val peerConnection by lazy { buildPeerConnection(observer) }

    private fun initPeerConnectionFactory(context: Application) {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    private fun buildPeerConnectionFactory(): PeerConnectionFactory {
        return PeerConnectionFactory
            .builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, false, true))
            .setOptions(PeerConnectionFactory.Options().apply {
                //disableEncryption = true
                disableNetworkMonitor = true
            })
            .createPeerConnectionFactory()
    }

    private fun buildPeerConnection(observer: PeerConnection.Observer): PeerConnection? {
//        val rtcConfiguration = RTCConfiguration(iceServer)
//        rtcConfiguration.iceTransportsType = PeerConnection.IceTransportsType.ALL
//        //rtcConfiguration.iceCandidatePoolSize = 2
//        rtcConfiguration.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
//       // rtcConfiguration.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
//        rtcConfiguration.continualGatheringPolicy =
//            PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
//        rtcConfiguration.candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.ALL


        val rtcConfiguration = RTCConfiguration(iceServer).apply {
            //tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            //rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            //keyType = PeerConnection.KeyType.ECDSA
            //enableDtlsSrtp = true
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }


        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
//        rtcConfiguration.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
//        rtcConfiguration.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
//        rtcConfiguration.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
//        rtcConfiguration.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
//        rtcConfiguration.keyType = PeerConnection.KeyType.ECDSA;
        // Enable DTLS for normal calls and disable for loopback calls.
//        rtcConfiguration.enableDtlsSrtp = !peerConnectionParameters.loopback;
//        rtcConfiguration.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

      return  peerConnectionFactory.createPeerConnection(
            rtcConfiguration,
            observer
        )
    }

    private fun getVideoCapturer(context: Context) =
        Camera2Enumerator(context).run {
            deviceNames.find {
                isFrontFacing(it)
            }?.let {
                createCapturer(it, null)
            } ?: throw IllegalStateException()
        }

    fun initSurfaceView(view: SurfaceViewRenderer) = view.run {
        setMirror(true)
        setEnableHardwareScaler(true)
        init(rootEglBase.eglBaseContext, null)
    }

    fun customInitSurfaceView(view: SurfaceViewRenderer) = view.run {
        init(rootEglBase.eglBaseContext, null)
    }

    fun startLocalVideoCapture(localVideoOutput: SurfaceViewRenderer) {
        val surfaceTextureHelper = SurfaceTextureHelper.create(Thread.currentThread().name, rootEglBase.eglBaseContext)
        (videoCapturer as VideoCapturer).initialize(surfaceTextureHelper, localVideoOutput.context, localVideoSource.capturerObserver)
        videoCapturer.startCapture(320, 240, 60)
        val localVideoTrack = peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID, localVideoSource)
        localVideoTrack.addSink(localVideoOutput)
        val localStream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID)
        localStream.addTrack(localVideoTrack)
        peerConnection?.addStream(localStream)
    }

    fun addTransceiver() {
//        peerConnection?.addTransceiver(
//            MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
//            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
//        )
        peerConnection?.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        )


    }

    fun getStats(){
        peerConnection?.getStats { p0 ->
            p0?.statsMap?.entries?.forEach {
                Log.v("SignallingClient", "getStats  key ${it.key} value ${it.value}")
            }
        }
    }

    private fun PeerConnection.call(sdpObserver: SdpObserver) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        createOffer(object : SdpObserver by sdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {

                setLocalDescription(object : SdpObserver {
                    override fun onSetFailure(p0: String?) {
                    }

                    override fun onSetSuccess() {
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {
                        println("SessionDescription ${p0?.description.toString()}")
                    }

                    override fun onCreateFailure(p0: String?) {
                    }
                }, desc)
                sdpObserver.onCreateSuccess(desc)
            }
        }, constraints)
    }

    private fun PeerConnection.answer(sdpObserver: SdpObserver) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        createAnswer(object : SdpObserver by sdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {
                setLocalDescription(object : SdpObserver {
                    override fun onSetFailure(p0: String?) {
                    }

                    override fun onSetSuccess() {
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {
                    }

                    override fun onCreateFailure(p0: String?) {
                    }
                }, p0)
                sdpObserver.onCreateSuccess(p0)
            }
        }, constraints)
    }

    fun call(sdpObserver: SdpObserver) = peerConnection?.call(sdpObserver)

    fun answer(sdpObserver: SdpObserver) = peerConnection?.answer(sdpObserver)

    fun onRemoteSessionReceived(sessionDescription: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetFailure(p0: String?) {
            }

            override fun onSetSuccess() {
            }

            override fun onCreateSuccess(p0: SessionDescription?) {
            }

            override fun onCreateFailure(p0: String?) {
            }
        }, sessionDescription)
    }

    fun addIceCandidate(iceCandidate: IceCandidate?) {
        peerConnection?.addIceCandidate(iceCandidate)
    }
}