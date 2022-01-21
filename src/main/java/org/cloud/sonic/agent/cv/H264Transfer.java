package org.cloud.sonic.agent.cv;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FFmpegLogCallback;

import java.io.IOException;
import java.net.Socket;

public class H264Transfer {
    protected FFmpegFrameGrabber grabber = null;
    protected FFmpegFrameRecorder recorder = null;

    public void from() throws IOException {
        Socket socket = new Socket("localhost",8999);
        FFmpegLogCallback.set();
        grabber = new FFmpegFrameGrabber(socket.getInputStream(),0);
        grabber.setOption("stimeout", "1000");
        grabber.setFormat("mjpeg");
        grabber.start();
        System.out.println(1);
    }

    public static void main(String[] args) throws IOException {
        H264Transfer h264Transfer = new H264Transfer();
        h264Transfer.from();
    }
}
