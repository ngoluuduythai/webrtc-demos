package me.amryousef.webrtc_demo

import org.webrtc.MediaStream
import org.webrtc.EglBase

class ParticipantDisplayItem(
    var session: String, var mediaStream: MediaStream,
    var streamType: String, var isStreamEnabled: Boolean, var rootEglBase: EglBase
) {
    var isAudioEnabled = false
    override fun toString(): String {
        return "ParticipantSession{" +
                ", session='" + session + '\'' +
                ", mediaStream=" + mediaStream +
                ", streamType='" + streamType + '\'' +
                ", streamEnabled=" + isStreamEnabled +
                ", rootEglBase=" + rootEglBase +
                '}'
    }
}