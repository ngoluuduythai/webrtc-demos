package me.amryousef.webrtc_demo

import org.webrtc.EglBase

data class TrackPeerMap(
    val peerID: Int,
    val rootEglBase: EglBase
)