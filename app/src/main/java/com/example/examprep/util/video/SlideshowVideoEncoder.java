package com.example.examprep.util.video;

import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.view.Surface;
import androidx.annotation.NonNull;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

public class SlideshowVideoEncoder {
    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;
    public static final int FRAME_RATE = EncodingTimeline.FPS;
    public static final int VIDEO_BITRATE = 2_500_000;
    public static final int AUDIO_BITRATE = 128_000;

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
            this.durationMs = Math.max(2500, durationMs);
        }
    }

    public static void encodeSlideshow(@NonNull List<SlideMedia> slides, @NonNull File outputFile,
            @NonNull EncoderProgressListener listener) throws Exception {
        encodeSlideshow(slides, outputFile, listener, () -> false);
    }

    public static void encodeSlideshow(@NonNull List<SlideMedia> slides, @NonNull File outputFile,
            @NonNull EncoderProgressListener listener, @NonNull BooleanSupplier cancelled) throws Exception {
        if (slides.isEmpty()) throw new IllegalArgumentException("No slides to encode");
        File folder = outputFile.getAbsoluteFile().getParentFile();
        File audio = null, video = null;
        boolean complete = false;
        try {
            audio = File.createTempFile("examforge-audio-", ".mp4", folder);
            video = File.createTempFile("examforge-video-", ".mp4", folder);
            // Separate streams prevent losing early packets while waiting for the other format.
            encodeAudio(slides, audio, listener, cancelled);
            encodeVideo(slides, video, listener, cancelled);
            listener.onProgress(slides.size(), slides.size(), "Combining video and narration...");
            muxTracks(video, audio, outputFile, cancelled);
            complete = true;
        } finally {
            if (audio != null) audio.delete();
            if (video != null) video.delete();
            if (!complete) outputFile.delete();
        }
    }

    private static void encodeAudio(List<SlideMedia> slides, File target,
            EncoderProgressListener listener, BooleanSupplier cancelled) throws Exception {
        int rate = 22050, channels = 1;
        for (SlideMedia slide : slides) {
            if (hasNarration(slide)) {
                try (PcmWavReader wav = new PcmWavReader(slide.audioWavFile)) {
                    rate = wav.sampleRate;
                    channels = wav.channels;
                }
                break;
            }
        }
        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, channels);
        format.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
        MediaCodec codec = null;
        TrackWriter writer = null;
        boolean started = false;
        CodecProgressWatchdog watchdog = new CodecProgressWatchdog(cancelled);
        try {
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            writer = new TrackWriter(target);
            codec.start();
            started = true;
            long samples = 0, totalVideoFrames = 0;
            byte[] pcm = new byte[16384];
            int bytesPerSample = channels * 2;
            for (int slideIndex = 0; slideIndex < slides.size(); slideIndex++) {
                SlideMedia slide = slides.get(slideIndex);
                listener.onProgress(slideIndex + 1, slides.size(), "Encoding narration " + (slideIndex + 1) + " of " + slides.size());
                totalVideoFrames += EncodingTimeline.framesForDuration(slide.durationMs);
                long endSample = EncodingTimeline.samplesAtFrame(totalVideoFrames, rate);
                try (PcmWavReader wav = hasNarration(slide) ? new PcmWavReader(slide.audioWavFile) : null) {
                    if (wav != null && (wav.sampleRate != rate || wav.channels != channels)) {
                        throw new IOException("The speech engine changed its audio format between slides. Please retry.");
                    }
                    while (samples < endSample) {
                        watchdog.check();
                        writer.drain(codec, watchdog, 0);
                        int index = codec.dequeueInputBuffer(10_000);
                        if (index < 0) continue;
                        ByteBuffer input = codec.getInputBuffer(index);
                        if (input == null) throw new IOException("Audio encoder returned no input buffer");
                        input.clear();
                        int count = (int) Math.min(Math.min(input.remaining(), pcm.length) / bytesPerSample,
                                endSample - samples) * bytesPerSample;
                        if (count == 0) throw new IOException("Audio encoder input buffer is too small");
                        Arrays.fill(pcm, 0, count, (byte) 0);
                        if (wav != null) wav.read(pcm, count); // Zero padding fills the breathing pause.
                        input.put(pcm, 0, count);
                        codec.queueInputBuffer(index, 0, count, EncodingTimeline.sampleTimeUs(samples, rate), 0);
                        samples += count / bytesPerSample;
                        // Drain after every PCM chunk; never fill a whole narration before draining.
                        writer.drain(codec, watchdog, 0);
                    }
                }
            }
            boolean sentEos = false;
            while (!writer.eos) {
                watchdog.check();
                writer.drain(codec, watchdog, 10_000);
                if (!sentEos) {
                    int index = codec.dequeueInputBuffer(10_000);
                    if (index >= 0) {
                        codec.queueInputBuffer(index, 0, 0, EncodingTimeline.sampleTimeUs(samples, rate),
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        sentEos = true;
                    }
                }
            }
            writer.finish();
        } finally {
            releaseCodec(codec, started);
            if (writer != null) writer.close();
        }
    }

    private static void encodeVideo(List<SlideMedia> slides, File target,
            EncoderProgressListener listener, BooleanSupplier cancelled) throws Exception {
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        MediaCodec codec = null;
        Surface surface = null;
        EncoderInputSurface renderer = null;
        TrackWriter writer = null;
        boolean started = false;
        CodecProgressWatchdog watchdog = new CodecProgressWatchdog(cancelled);
        try {
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface = codec.createInputSurface();
            renderer = new EncoderInputSurface(surface, WIDTH, HEIGHT);
            writer = new TrackWriter(target);
            codec.start();
            started = true;
            long globalFrame = 0;
            for (int s = 0; s < slides.size(); s++) {
                SlideMedia slide = slides.get(s);
                if (slide.bitmap == null || slide.bitmap.isRecycled()) throw new IOException("Slide image is unavailable");
                renderer.setSlide(slide.bitmap);
                long frames = EncodingTimeline.framesForDuration(slide.durationMs);
                for (long frame = 0; frame < frames; frame++) {
                    watchdog.check();
                    // Free codec output before asking EGL to submit another input frame.
                    writer.drain(codec, watchdog, 0);
                    renderer.drawFrame(EncodingTimeline.frameTimeNs(globalFrame++));
                    writer.drain(codec, watchdog, 0);
                    if (frame % FRAME_RATE == 0 || frame + 1 == frames) {
                        int percent = (int) ((frame + 1) * 100 / frames);
                        listener.onProgress(s + 1, slides.size(), "Encoding slide " + (s + 1)
                                + " of " + slides.size() + " (" + percent + "%)");
                    }
                }
            }
            codec.signalEndOfInputStream();
            while (!writer.eos) {
                watchdog.check();
                writer.drain(codec, watchdog, 10_000);
            }
            writer.finish();
        } finally {
            if (renderer != null) renderer.close();
            if (surface != null) surface.release();
            releaseCodec(codec, started);
            if (writer != null) writer.close();
        }
    }

    private static boolean hasNarration(SlideMedia slide) {
        return slide.audioWavFile != null && slide.audioWavFile.isFile() && slide.audioWavFile.length() > 0;
    }
    private static void releaseCodec(MediaCodec codec, boolean started) {
        if (codec == null) return;
        try { if (started) codec.stop(); } catch (RuntimeException ignored) {}
        try { codec.release(); } catch (RuntimeException ignored) {}
    }

    /** One muxer per encoder: output is written immediately and buffers always released. */
    private static final class TrackWriter implements AutoCloseable {
        private final MediaMuxer muxer;
        private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        private int track = -1;
        private boolean started;
        private long samples;
        boolean eos;
        TrackWriter(File file) throws IOException {
            muxer = new MediaMuxer(file.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        }
        void drain(MediaCodec codec, CodecProgressWatchdog watchdog, long waitUs) throws IOException {
            while (!eos) {
                watchdog.check();
                int index = codec.dequeueOutputBuffer(info, waitUs);
                waitUs = 0;
                if (index == MediaCodec.INFO_TRY_AGAIN_LATER) return;
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (started) throw new IOException("Encoder changed its output format twice");
                    track = muxer.addTrack(codec.getOutputFormat());
                    muxer.start();
                    started = true;
                    watchdog.progress();
                } else if (index >= 0) {
                    try {
                        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            if (!started) throw new IOException("Encoder emitted samples before its format");
                            ByteBuffer buffer = codec.getOutputBuffer(index);
                            if (buffer == null) throw new IOException("Encoder returned no output buffer");
                            buffer.position(info.offset);
                            buffer.limit(info.offset + info.size);
                            muxer.writeSampleData(track, buffer, info);
                            samples++;
                        }
                        eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        watchdog.progress();
                    } finally {
                        codec.releaseOutputBuffer(index, false);
                    }
                }
            }
        }
        void finish() throws IOException {
            if (!started || !eos || samples == 0) throw new IOException("Encoder produced an incomplete media stream");
            muxer.stop();
            started = false;
        }
        @Override public void close() {
            try { if (started) muxer.stop(); } catch (RuntimeException ignored) {}
            try { muxer.release(); } catch (RuntimeException ignored) {}
        }
    }

    private static void muxTracks(File video, File audio, File target, BooleanSupplier cancelled) throws IOException {
        MediaExtractor videoInput = new MediaExtractor();
        MediaExtractor audioInput = new MediaExtractor();
        MediaMuxer muxer = null;
        boolean started = false;
        try {
            videoInput.setDataSource(video.getAbsolutePath());
            audioInput.setDataSource(audio.getAbsolutePath());
            if (videoInput.getTrackCount() != 1 || audioInput.getTrackCount() != 1) throw new IOException("Missing encoded track");
            videoInput.selectTrack(0);
            audioInput.selectTrack(0);
            muxer = new MediaMuxer(target.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int videoTrack = muxer.addTrack(videoInput.getTrackFormat(0));
            int audioTrack = muxer.addTrack(audioInput.getTrackFormat(0));
            muxer.start();
            started = true;
            ByteBuffer buffer = ByteBuffer.allocateDirect(4 * 1024 * 1024);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            CodecProgressWatchdog watchdog = new CodecProgressWatchdog(cancelled);
            while (videoInput.getSampleTrackIndex() >= 0 || audioInput.getSampleTrackIndex() >= 0) {
                watchdog.check();
                boolean useVideo = videoInput.getSampleTrackIndex() >= 0 && (audioInput.getSampleTrackIndex() < 0
                        || videoInput.getSampleTime() <= audioInput.getSampleTime());
                MediaExtractor input = useVideo ? videoInput : audioInput;
                buffer.clear();
                int size = input.readSampleData(buffer, 0);
                if (size < 0) throw new IOException("Unexpected end of encoded media");
                info.set(0, size, input.getSampleTime(),
                        (input.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0);
                buffer.position(0);
                buffer.limit(size);
                muxer.writeSampleData(useVideo ? videoTrack : audioTrack, buffer, info);
                input.advance();
                watchdog.progress();
            }
            muxer.stop();
            started = false;
        } finally {
            videoInput.release();
            audioInput.release();
            if (muxer != null) {
                try { if (started) muxer.stop(); } catch (RuntimeException ignored) {}
                muxer.release();
            }
        }
    }
}
