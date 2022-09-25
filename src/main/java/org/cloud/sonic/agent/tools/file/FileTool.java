package org.cloud.sonic.agent.tools.file;

import java.io.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * @author ZhouYiXun
 * @des 压缩文件
 * @date 2022/3/29 23:38
 */
public class FileTool {
    public static void zip(File result, File inputFile) throws IOException {
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(
                result.getAbsoluteFile()));
        zip(out, inputFile, "");
        out.close();
    }

    public static void zip(ZipOutputStream out, File f, String base) throws IOException {
        if (f.isDirectory()) {
            File[] fl = f.listFiles();
            out.putNextEntry(new ZipEntry(base + "/"));
            base = base.length() == 0 ? "" : base + "/";
            for (int i = 0; i < fl.length; i++) {
                zip(out, fl[i], base + fl[i]);
            }
        } else {
            out.putNextEntry(new ZipEntry(base));
            FileInputStream in = new FileInputStream(f);
            int b;
            while ((b = in.read()) != -1) {
                out.write(b);
            }
            in.close();
        }
    }

    public static File unZipChromeDriver(File source, String version) {
        int BUFFER = 2048;
        File webview = new File("webview");
        if (!webview.exists()) {
            webview.mkdirs();
        }
        File driver = null;
        try {
            ZipFile zipFile = new ZipFile(source);
            Enumeration emu = zipFile.entries();
            while (emu.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) emu.nextElement();
                BufferedInputStream bis = new BufferedInputStream(zipFile.getInputStream(entry));
                if (entry.getName().equals("chromedriver.exe")) {
                    String fileName = version + "_chromedriver.exe";
                    File file = new File(webview + File.separator + fileName);
                    File parent = file.getParentFile();
                    if (parent != null && (!parent.exists())) {
                        parent.mkdirs();
                    }
                    FileOutputStream fos = new FileOutputStream(file);
                    BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER);
                    int count;
                    byte data[] = new byte[BUFFER];
                    while ((count = bis.read(data, 0, BUFFER)) != -1) {
                        bos.write(data, 0, count);
                    }
                    bos.flush();
                    bos.close();
                    driver = file;
                }
                bis.close();
                if (driver != null) {
                    break;
                }
            }
            zipFile.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (driver != null) {
            source.delete();
        }
        return driver;
    }

    public static void deleteDir(File file) {
        if (!file.exists()) {
            return;
        }
        File[] files = file.listFiles();
        for (File f : files) {
            if (f.isDirectory()) {
                deleteDir(f);
            } else {
                f.delete();
            }
        }
        file.delete();
    }
}
