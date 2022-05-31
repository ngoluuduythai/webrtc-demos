//package me.amryousef.webrtc_demo
//
//import android.app.Application
//import android.content.Context
//import android.util.Log
//import android.view.LayoutInflater
//import android.view.SurfaceView
//import android.view.View
//import android.view.ViewGroup
//import android.widget.BaseAdapter
//import android.widget.ImageView
//import android.widget.TextView
//import androidx.core.view.isGone
//import kotlinx.android.synthetic.main.activity_main.*
//import org.webrtc.*
//
//// on below line we are creating an
//// adapter class for our grid view.
//internal class GridRVAdapter(
//    // on below line we are creating two
//    // variables for course list and context
//    private val courseList: List<GridViewModal>,
//    private val context: Context,
//    private val signallingClient: SignallingClient,
//    private val application: Application
//) :
//    BaseAdapter() {
//    // in base adapter class we are creating variables
//    // for layout inflater, course image view and course text view.
//    private var layoutInflater: LayoutInflater? = null
//    private lateinit var webrtcView: SurfaceViewRenderer
//    private lateinit var rtcClient: RTCClient
//
//
//    // below method is use to return the count of course list
//    override fun getCount(): Int {
//        return courseList.size
//    }
//
//    // below function is use to return the item of grid view.
//    override fun getItem(position: Int): Any? {
//        return null
//    }
//
//    // below function is use to return item id of grid view.
//    override fun getItemId(position: Int): Long {
//        return 0
//    }
//
//    // in below function we are getting indivisual item of grid view.
//    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
//        var convertView = convertView
//        // on blow line we are checking if layout inflater
//        // is null, if it is null we are initializing it.
//        if (layoutInflater == null) {
//            layoutInflater =
//                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
//        }
//        // on the below line we are checking if convert view is null.
//        // If it is null we are initializing it.
//        if (convertView == null) {
//            // on below line we are passing the layout file
//            // which we have to inflate for each item of grid view.
//            convertView = layoutInflater?.inflate(R.layout.webrtc_view, null)
//        }
//        // on below line we are initializing our course image view
//        // and course text view with their ids.
//        webrtcView = convertView!!.findViewById(R.id.remote_view)
//        // on below line we are setting image for our course image view.
//        // on below line we are setting text in our course text view.
//       // courseTV.setText(courseList.get(position).courseName)
//        createRTCClient(application, webrtcView)
//
//        return convertView
//    }
//
//
//    private fun createRTCClient(application: Application, webrtcView: SurfaceViewRenderer){
//        rtcClient = RTCClient(
//            application,
//            object : PeerConnectionObserver() {
//                override fun onIceCandidate(p0: IceCandidate?) {
//                    super.onIceCandidate(p0)
//                    println("onIceCandidate  ${p0?.sdp}")
//                    Log.v(this@GridRVAdapter.javaClass.simpleName, "Send onIceCandidate ${p0?.sdp}")
//
//                    val iceData = IceMessage(IceData(
//                        candidate = p0?.sdp!!,
//                        sdpMid = p0?.sdpMid,
//                        sdpMLineIndex = p0?.sdpMLineIndex )
//                    )
//
//                    signallingClient.send(iceData)
//                    rtcClient.addIceCandidate(p0)
//
//                }
//
//                override fun onAddStream(p0: MediaStream?) {
//                    super.onAddStream(p0)
//                    p0?.videoTracks?.get(0)?.addSink(webrtcView)
//                }
//            }, rootEglBase = rootEglBase
//        )
//
//        //rtcClient.initSurfaceView(webrtcView)
//        //rtcClient.initSurfaceView(local_view)
//        //rtcClient.startLocalVideoCapture(local_view)
//        webrtcView?.run {
//            setMirror(true)
//            setEnableHardwareScaler(true)
//            //init(rootEglBase.eglBaseContext, null)
//        }
//
//        rtcClient.customInitSurfaceView(webrtcView)
//
//        rtcClient.addTransceiver()
//
//        signallingClient.send("HELLO " +1122)
//    }
//
//
//    private val sdpObserver = object : AppSdpObserver() {
//        override fun onCreateSuccess(p0: SessionDescription?) {
//            super.onCreateSuccess(p0)
//            println("sdp onCreateSuccess ${p0?.description.toString()}")
//
//            val sdpData = if(p0?.type == SessionDescription.Type.OFFER) {
//                Log.v(this@GridRVAdapter.javaClass.simpleName, "Send offer ${p0?.description}")
//
//                SDPMessage(SDPData(type = "offer", sdp = p0.description!!))
//            } else {
//                Log.v(this@GridRVAdapter.javaClass.simpleName, "Send anwser ${p0?.description}")
//                SDPMessage(SDPData(type = "answer", sdp = p0?.description!!))
//            }
//
//            signallingClient.send(sdpData)
//        }
//    }
//
//
//    private fun createSignallingClientListener() = object : SignallingClientListener {
//        override fun onConnectionEstablished() {
//            //call_button.isClickable = true
//        }
//
//        override fun onOfferReceived(sdpData: SDPMessage) {
//            Log.v(this@GridRVAdapter.javaClass.simpleName, "Received onOfferReceived ${sdpData.sdp.sdp}")
//
//            val description = SessionDescription(SessionDescription.Type.OFFER, sdpData.sdp.sdp)
//            rtcClient.onRemoteSessionReceived(description)
//            rtcClient.answer(sdpObserver)
//            //remote_view_loading.isGone = true
//        }
//
//        override fun onAnswerReceived(sdpData: SDPMessage) {
//            Log.v(this@GridRVAdapter.javaClass.simpleName, "Received onAnswerReceived ${sdpData.sdp.sdp}")
//
//            val description = SessionDescription(SessionDescription.Type.ANSWER, sdpData.sdp.sdp)
//            rtcClient.onRemoteSessionReceived(description)
//            //remote_view_loading.isGone = true
//        }
//
//        override fun onIceCandidateReceived(iceMessage: IceMessage) {
//            Log.v(this@GridRVAdapter.javaClass.simpleName, "Received onIceCandidateReceived ${iceMessage.ice.candidate}")
//
//            var sdpMid = iceMessage.ice.sdpMid
//
//            println("sdpMid  $sdpMid")
//            if(sdpMid == "null" || sdpMid == "" || sdpMid == null){
//                sdpMid = "0"
//            }
//
//            val iceCandidate =
//                IceCandidate(sdpMid,
//                    iceMessage.ice.sdpMLineIndex,
//                    iceMessage.ice.candidate
//                )
//            rtcClient.addIceCandidate(iceCandidate)
//        }
//    }
//
//}
