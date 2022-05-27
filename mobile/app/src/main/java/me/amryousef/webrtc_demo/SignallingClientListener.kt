package me.amryousef.webrtc_demo

import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

interface SignallingClientListener {
    fun onConnectionEstablished()
    fun onOfferReceived(description: SDPMessage)
    fun onAnswerReceived(description: SDPMessage)
    fun onIceCandidateReceived(iceCandidate: IceMessage)
}