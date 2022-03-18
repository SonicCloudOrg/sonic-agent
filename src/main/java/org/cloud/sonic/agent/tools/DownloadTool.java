package org.cloud.sonic.agent.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Calendar;

public class DownloadTool {
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

    public static void update(String version) throws IOException {
        URL url = new URL(String.format("https://sonic-cloud.sonic-download.workers.dev/https://github.com/SonicCloudOrg/sonic-agent/releases/download/%s/sonic-agent-%s.zip", version, version));
        URLConnection con = url.openConnection();
        InputStream is = con.getInputStream();
        byte[] bs = new byte[1024];
        int len;
        File upgrade = new File("upgrade");
        if (!upgrade.exists()) {
            upgrade.mkdirs();
        }
        String filename = upgrade.getAbsolutePath() + File.separator + "sonic-agent-" + version + ".zip";
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
    }
}
