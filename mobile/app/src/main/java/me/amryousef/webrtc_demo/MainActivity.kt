package me.amryousef.webrtc_demo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import io.ktor.util.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.webrtc.*
import java.io.*


@ExperimentalCoroutinesApi
@KtorExperimentalAPI
class MainActivity : AppCompatActivity() {

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1
        private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
    }

    private lateinit var rtcClient: RTCClient
    private lateinit var signallingClient: SignallingClient

    private val sdpObserver = object : AppSdpObserver() {
        override fun onCreateSuccess(p0: SessionDescription?) {
            super.onCreateSuccess(p0)
            println("sdp onCreateSuccess ${p0?.description.toString()}")

            val sdpData = if(p0?.type == SessionDescription.Type.OFFER) {
                Log.v(this@MainActivity.javaClass.simpleName, "Send offer ${p0?.description}")

                SDPMessage(SDPData(type = "offer", sdp = p0.description!!))
            } else {
                Log.v(this@MainActivity.javaClass.simpleName, "Send anwser ${p0?.description}")
                SDPMessage(SDPData(type = "answer", sdp = p0?.description!!))
            }

            signallingClient.send(sdpData)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission()
        } else {
            onCameraPermissionGranted()
        }
    }

    private fun onCameraPermissionGranted() {
        rtcClient = RTCClient(
            application,
            object : PeerConnectionObserver() {
                override fun onIceCandidate(p0: IceCandidate?) {
                    super.onIceCandidate(p0)
                    println("onIceCandidate  ${p0?.sdp}")
                    Log.v(this@MainActivity.javaClass.simpleName, "Send onIceCandidate ${p0?.sdp}")

                    val iceData = IceMessage(IceData(
                        candidate = p0?.sdp!!,
                        sdpMid = p0?.sdpMid,
                        sdpMLineIndex = p0?.sdpMLineIndex )
                    )

                    signallingClient.send(iceData)
                    rtcClient.addIceCandidate(p0)

                }

                override fun onAddStream(p0: MediaStream?) {
                    super.onAddStream(p0)
                    p0?.videoTracks?.get(0)?.addSink(remote_view)
                }
            }
        )

        rtcClient.initSurfaceView(remote_view)
        //rtcClient.initSurfaceView(local_view)
        //rtcClient.startLocalVideoCapture(local_view)
        rtcClient.addTransceiver()

        signallingClient = SignallingClient(createSignallingClientListener())
        signallingClient.send("HELLO " +1122)

        call_button.setOnClickListener {
            signallingClient.send("SESSION " +1212)
            rtcClient.call(sdpObserver)
        }
    }

    private fun createSignallingClientListener() = object : SignallingClientListener {
        override fun onConnectionEstablished() {
            call_button.isClickable = true
        }

        override fun onOfferReceived(sdpData: SDPMessage) {
            Log.v(this@MainActivity.javaClass.simpleName, "Received onOfferReceived ${sdpData.sdp.sdp}")

            val description = SessionDescription(SessionDescription.Type.OFFER, sdpData.sdp.sdp)
            rtcClient.onRemoteSessionReceived(description)
            rtcClient.answer(sdpObserver)
            remote_view_loading.isGone = true
        }

        override fun onAnswerReceived(sdpData: SDPMessage) {
            Log.v(this@MainActivity.javaClass.simpleName, "Received onAnswerReceived ${sdpData.sdp.sdp}")

            val description = SessionDescription(SessionDescription.Type.ANSWER, sdpData.sdp.sdp)
            rtcClient.onRemoteSessionReceived(description)
            remote_view_loading.isGone = true
        }

        override fun onIceCandidateReceived(iceMessage: IceMessage) {
            Log.v(this@MainActivity.javaClass.simpleName, "Received onIceCandidateReceived ${iceMessage.ice.candidate}")

            var sdpMid = iceMessage.ice.sdpMid

            println("sdpMid  $sdpMid")
            if(sdpMid == "null" || sdpMid == "" || sdpMid == null){
                sdpMid = "0"
            }

            val iceCandidate =
                IceCandidate(sdpMid,
                iceMessage.ice.sdpMLineIndex,
                iceMessage.ice.candidate
                )
            rtcClient.addIceCandidate(iceCandidate)
        }
    }

    private fun requestCameraPermission(dialogShown: Boolean = false) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, CAMERA_PERMISSION) && !dialogShown) {
            showPermissionRationaleDialog()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(CAMERA_PERMISSION), CAMERA_PERMISSION_REQUEST_CODE)
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Camera Permission Required")
            .setMessage("This app need the camera to function")
            .setPositiveButton("Grant") { dialog, _ ->
                dialog.dismiss()
                requestCameraPermission(true)
            }
            .setNegativeButton("Deny") { dialog, _ ->
                dialog.dismiss()
                onCameraPermissionDenied()
            }
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            onCameraPermissionGranted()
        } else {
            onCameraPermissionDenied()
        }
    }

    private fun onCameraPermissionDenied() {
        Toast.makeText(this, "Camera Permission Denied", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        signallingClient.destroy()
        super.onDestroy()
    }
}
