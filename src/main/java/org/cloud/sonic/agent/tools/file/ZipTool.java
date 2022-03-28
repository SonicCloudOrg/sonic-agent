package org.cloud.sonic.agent.tools.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author ZhouYiXun
 * @des 压缩文件
 * @date 2022/3/29 23:38
 */
public class ZipTool {
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
}
