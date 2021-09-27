package com.sonic.agent.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Calendar;

public class DownImageTool {
    public static File download(String urlString) throws IOException {
        URL url = new URL(urlString);
        URLConnection con = url.openConnection();
        InputStream is = con.getInputStream();
        byte[] bs = new byte[1024];
        int len;

        long time = Calendar.getInstance().getTimeInMillis();
        String tail = "";
        if (urlString.lastIndexOf(".") != -1) {
            tail = urlString.substring(urlString.lastIndexOf(".") + 1);
        }
        String filename = "test-output" + File.separator + "download-" + time + "." + tail;
        File file = new File(filename);
        FileOutputStream os = null;
        try {
            os = new FileOutputStream(file, true);
            while ((len = is.read(bs)) != -1) {
                os.write(bs, 0, len);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return file;
    }
}
