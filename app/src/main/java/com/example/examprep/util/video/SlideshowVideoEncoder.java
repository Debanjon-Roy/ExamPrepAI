package com.example.examprep.util.video;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class SlideshowVideoEncoder {

    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;
    public static final int FRAME_RATE = 30;
    public static final int VIDEO_BITRATE = 2_500_000; // 2.5 Mbps
    public static final int AUDIO_BITRATE = 128_000;   // 128 kbps

    public interface EncoderProgressListener {
        void onProgress(int currentSlide, int totalSlides, String status);
    }

    public static class SlideMedia {
        public final Bitmap bitmap;
        public final File audioWavFile;
        public final long durationMs;

        public SlideMedia(Bitmap bitmap, File audioWavFile, long durationMs) {
            this.bitmap = bitmap;
            this.audioWavFile = audioWavFile;
            this.durationMs = Math.max(2500, durationMs); // at least 2.5s per slide
        }
    }

    public static void encodeSlideshow(
            @NonNull List<SlideMedia> slides,
            @NonNull File outputFile,
            @NonNull EncoderProgressListener listener
    ) throws Exception {
        if (slides.isEmpty()) {
            throw new IllegalArgumentException("No slides to encode");
        }

        // 1. Inspect first audio WAV header to get sample rate and channels
        int audioSampleRate = 22050;
        int audioChannels = 1;

        for (SlideMedia sm : slides) {
            if (sm.audioWavFile != null && sm.audioWavFile.exists() && sm.audioWavFile.length() > 44) {
                WavHeader info = parseWavHeader(sm.audioWavFile);
                if (info != null && info.sampleRate > 0) {
                    audioSampleRate = info.sampleRate;
                    audioChannels = info.channels;
                    break;
                }
            }
        }

        MediaMuxer muxer = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        // Configure Video Encoder (H.264 / AVC)
        MediaFormat videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        MediaCodec videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        Surface inputSurface = videoEncoder.createInputSurface();
        videoEncoder.start();

        // Configure Audio Encoder (AAC)
        MediaFormat audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, audioSampleRate, audioChannels);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);

        MediaCodec audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        audioEncoder.start();

        int[] videoTrackIndex = new int[]{-1};
        int[] audioTrackIndex = new int[]{-1};
        boolean[] muxerStarted = new boolean[]{false};

        MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();

        long globalVideoPtsUs = 0;
        long globalAudioPtsUs = 0;
        Rect srcRect = new Rect(0, 0, WIDTH, HEIGHT);
        Rect dstRect = new Rect(0, 0, WIDTH, HEIGHT);

        int totalSlides = slides.size();

        try {
            for (int s = 0; s < totalSlides; s++) {
                SlideMedia slide = slides.get(s);
                listener.onProgress(s + 1, totalSlides, "Encoding Slide " + (s + 1) + " of " + totalSlides + "...");

                long slideDurationMs = slide.durationMs;
                int frameCount = (int) ((slideDurationMs * FRAME_RATE) / 1000);
                if (frameCount < 30) frameCount = 30; // minimum 1 second

                long frameDurationUs = 1_000_000L / FRAME_RATE;

                // A. Pump Audio for this slide
                feedSlideAudio(slide.audioWavFile, audioEncoder, audioSampleRate, audioChannels,
                        globalAudioPtsUs, slideDurationMs);

                // Drain audio encoder until needed
                drainAudio(audioEncoder, muxer, audioTrackIndex, videoTrackIndex, muxerStarted, audioBufferInfo, false);

                // B. Render Video frames for this slide
                for (int f = 0; f < frameCount; f++) {
                    // Draw to hardware canvas of input surface
                    Canvas canvas = inputSurface.lockHardwareCanvas();
                    if (canvas != null) {
                        canvas.drawBitmap(slide.bitmap, srcRect, dstRect, null);
                        inputSurface.unlockCanvasAndPost(canvas);
                    }

                    // Drain video
                    drainVideo(videoEncoder, muxer, videoTrackIndex, audioTrackIndex, muxerStarted, videoBufferInfo, false);

                    globalVideoPtsUs += frameDurationUs;
                }

                globalAudioPtsUs += (slideDurationMs * 1000L);
            }

            // Signal end of streams
            videoEncoder.signalEndOfInputStream();
            drainVideo(videoEncoder, muxer, videoTrackIndex, audioTrackIndex, muxerStarted, videoBufferInfo, true);

            sendAudioEndOfStream(audioEncoder, globalAudioPtsUs);
            drainAudio(audioEncoder, muxer, audioTrackIndex, videoTrackIndex, muxerStarted, audioBufferInfo, true);

        } finally {
            try {
                if (muxerStarted[0]) {
                    muxer.stop();
                }
            } catch (Exception ignored) {}
            try {
                muxer.release();
            } catch (Exception ignored) {}

            try {
                videoEncoder.stop();
                videoEncoder.release();
            } catch (Exception ignored) {}

            try {
                audioEncoder.stop();
                audioEncoder.release();
            } catch (Exception ignored) {}

            if (inputSurface != null) {
                inputSurface.release();
            }
        }
    }

    private static void drainVideo(
            MediaCodec encoder,
            MediaMuxer muxer,
            int[] videoTrack,
            int[] audioTrack,
            boolean[] muxerStarted,
            MediaCodec.BufferInfo bufferInfo,
            boolean endOfStream
    ) {
        final int TIMEOUT_USEC = 10000;
        while (true) {
            int status = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break;
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (videoTrack[0] == -1) {
                    MediaFormat newFormat = encoder.getOutputFormat();
                    videoTrack[0] = muxer.addTrack(newFormat);
                    checkStartMuxer(muxer, videoTrack, audioTrack, muxerStarted);
                }
            } else if (status >= 0) {
                ByteBuffer encodedData = encoder.getOutputBuffer(status);
                if (encodedData != null && bufferInfo.size > 0 && muxerStarted[0]) {
                    encodedData.position(bufferInfo.offset);
                    encodedData.limit(bufferInfo.offset + bufferInfo.size);
                    muxer.writeSampleData(videoTrack[0], encodedData, bufferInfo);
                }
                encoder.releaseOutputBuffer(status, false);
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            }
        }
    }

    private static void drainAudio(
            MediaCodec encoder,
            MediaMuxer muxer,
            int[] audioTrack,
            int[] videoTrack,
            boolean[] muxerStarted,
            MediaCodec.BufferInfo bufferInfo,
            boolean endOfStream
    ) {
        final int TIMEOUT_USEC = 10000;
        while (true) {
            int status = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break;
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (audioTrack[0] == -1) {
                    MediaFormat newFormat = encoder.getOutputFormat();
                    audioTrack[0] = muxer.addTrack(newFormat);
                    checkStartMuxer(muxer, videoTrack, audioTrack, muxerStarted);
                }
            } else if (status >= 0) {
                ByteBuffer encodedData = encoder.getOutputBuffer(status);
                if (encodedData != null && bufferInfo.size > 0 && muxerStarted[0]) {
                    encodedData.position(bufferInfo.offset);
                    encodedData.limit(bufferInfo.offset + bufferInfo.size);
                    muxer.writeSampleData(audioTrack[0], encodedData, bufferInfo);
                }
                encoder.releaseOutputBuffer(status, false);
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            }
        }
    }

    private static void checkStartMuxer(
            MediaMuxer muxer,
            int[] videoTrack,
            int[] audioTrack,
            boolean[] muxerStarted
    ) {
        if (!muxerStarted[0] && videoTrack[0] != -1 && audioTrack[0] != -1) {
            muxer.start();
            muxerStarted[0] = true;
        }
    }

    private static void feedSlideAudio(
            File wavFile,
            MediaCodec audioEncoder,
            int sampleRate,
            int channels,
            long startPtsUs,
            long durationMs
    ) throws IOException {
        byte[] pcmBytes = null;
        if (wavFile != null && wavFile.exists() && wavFile.length() > 44) {
            long pcmLength = wavFile.length() - 44;
            pcmBytes = new byte[(int) pcmLength];
            try (FileInputStream fis = new FileInputStream(wavFile)) {
                fis.skip(44); // skip WAV header
                fis.read(pcmBytes);
            }
        }

        // If no audio or too short, generate silence PCM to match duration
        if (pcmBytes == null || pcmBytes.length == 0) {
            int bytesNeeded = (int) ((durationMs * sampleRate * channels * 2) / 1000);
            pcmBytes = new byte[bytesNeeded];
        }

        int offset = 0;
        int totalBytes = pcmBytes.length;
        int bytesPerSec = sampleRate * channels * 2;
        long pts = startPtsUs;

        while (offset < totalBytes) {
            int inputIndex = audioEncoder.dequeueInputBuffer(10000);
            if (inputIndex >= 0) {
                ByteBuffer inputBuffer = audioEncoder.getInputBuffer(inputIndex);
                inputBuffer.clear();

                int chunkSize = Math.min(inputBuffer.remaining(), totalBytes - offset);
                inputBuffer.put(pcmBytes, offset, chunkSize);

                audioEncoder.queueInputBuffer(inputIndex, 0, chunkSize, pts, 0);

                offset += chunkSize;
                pts += (chunkSize * 1_000_000L) / bytesPerSec;
            }
        }
    }

    private static void sendAudioEndOfStream(MediaCodec audioEncoder, long lastPtsUs) {
        int inputIndex = audioEncoder.dequeueInputBuffer(10000);
        if (inputIndex >= 0) {
            audioEncoder.queueInputBuffer(inputIndex, 0, 0, lastPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        }
    }

    private static class WavHeader {
        int sampleRate;
        int channels;
    }

    private static WavHeader parseWavHeader(File wavFile) {
        try (FileInputStream fis = new FileInputStream(wavFile)) {
            byte[] header = new byte[44];
            int read = fis.read(header);
            if (read >= 44) {
                WavHeader info = new WavHeader();
                info.channels = (header[22] & 0xFF) | ((header[23] & 0xFF) << 8);
                info.sampleRate = (header[24] & 0xFF) |
                        ((header[25] & 0xFF) << 8) |
                        ((header[26] & 0xFF) << 16) |
                        ((header[27] & 0xFF) << 24);
                return info;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
