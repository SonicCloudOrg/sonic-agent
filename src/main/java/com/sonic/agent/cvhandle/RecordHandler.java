package com.sonic.agent.cvhandle;

import com.sonic.agent.tools.UploadTools;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class RecordHandler {
    public static String record(File file, List<byte[]> images, int width, int height) throws FrameRecorder.Exception {
        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(file.getAbsoluteFile(), width, height);
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        recorder.setFrameRate(24);
        recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
        recorder.setFormat("mp4");
        try {
            recorder.start();
            Java2DFrameConverter converter = new Java2DFrameConverter();
            // 录制视频设置24帧
            int duration = images.size() / 24;
            for (int i = 0; i < duration; i++) {
                for (int j = 0; j < 24; j++) {
                    BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(images.get(i * 24 + j)));
                    recorder.record(converter.getFrame(bufferedImage));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            recorder.stop();
            recorder.release();
        }
        return UploadTools.uploadPatchRecord(file);
    }
}
