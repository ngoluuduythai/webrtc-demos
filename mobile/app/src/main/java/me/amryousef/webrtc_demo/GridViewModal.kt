package me.amryousef.webrtc_demo

import org.webrtc.EglBase

// on below line we are creating a modal class.
data class GridViewModal(
    // we are creating a modal class with 2 member
    // one is course name as string and
    // other course img as int.
    val streamId: Int,
    val rootEglBase: EglBase
)