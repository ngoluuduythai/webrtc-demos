package me.amryousef.webrtc_demo;

import androidx.annotation.Nullable;

import org.webrtc.EglBase;
import org.webrtc.PlatformSoftwareVideoDecoderFactory;
import org.webrtc.SoftwareVideoDecoderFactory;
import org.webrtc.VideoCodecInfo;
import org.webrtc.VideoDecoder;
import org.webrtc.VideoDecoderFactory;

import java.util.Arrays;
import java.util.LinkedHashSet;

public class CustomVideoDecoderFactory implements org.webrtc.VideoDecoderFactory {
    private final org.webrtc.VideoDecoderFactory softwareVideoDecoderFactory = new SoftwareVideoDecoderFactory();
    @Nullable
    private final org.webrtc.VideoDecoderFactory platformSoftwareVideoDecoderFactory;

    public CustomVideoDecoderFactory(@Nullable EglBase.Context eglContext) {
        this.platformSoftwareVideoDecoderFactory = new PlatformSoftwareVideoDecoderFactory(eglContext);
    }

    CustomVideoDecoderFactory(VideoDecoderFactory hardwareVideoDecoderFactory) {
        this.platformSoftwareVideoDecoderFactory = null;
    }

    @Nullable
    public VideoDecoder createDecoder(VideoCodecInfo codecType) {
        VideoDecoder softwareDecoder = this.softwareVideoDecoderFactory.createDecoder(codecType);
        if (softwareDecoder == null && this.platformSoftwareVideoDecoderFactory != null) {
            softwareDecoder = this.platformSoftwareVideoDecoderFactory.createDecoder(codecType);
        }

        return softwareDecoder;
    }

    public VideoCodecInfo[] getSupportedCodecs() {
        LinkedHashSet<VideoCodecInfo> supportedCodecInfos = new LinkedHashSet();
        supportedCodecInfos.addAll(Arrays.asList(this.softwareVideoDecoderFactory.getSupportedCodecs()));
        if (this.platformSoftwareVideoDecoderFactory != null) {
            supportedCodecInfos.addAll(Arrays.asList(this.platformSoftwareVideoDecoderFactory.getSupportedCodecs()));
        }

        return (VideoCodecInfo[])supportedCodecInfos.toArray(new VideoCodecInfo[supportedCodecInfos.size()]);
    }
}
