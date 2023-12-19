/*
 *   sonic-agent  Agent of Sonic Cloud Real Machine Platform.
 *   Copyright (C) 2022 SonicCloudOrg
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published
 *   by the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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

    /**
     * 解压缩下载完成的chromeDriver.zip文件
     *
     * @param source         原始的下载临时文件
     * @param version        完整的chrome版本
     * @param greaterThen114 是否>=115(大于115的场景下，使用的是google官方Chrome for Testing (CfT)下载路径)
     * @param systemName     系统类型(大于115的场景下，判断产物文件会使用到)
     * @return chromeDriver文件
     */
    public static File unZipChromeDriver(File source, String version, boolean greaterThen114, String systemName) {
        int BUFFER = 2048;
        File webview = new File("webview");
        if (!webview.exists()) {
            webview.mkdirs();
        }
        String system = System.getProperty("os.name").toLowerCase();
        String tail = "chromedriver";
        if (system.contains("win")) {
            tail += ".exe";
        }
        File driver = null;
        try {
            ZipFile zipFile = new ZipFile(source);
            Enumeration emu = zipFile.entries();
            while (emu.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) emu.nextElement();
                BufferedInputStream bis = new BufferedInputStream(zipFile.getInputStream(entry));
                // >=115之后的版本，entry.name字段有变更，带上了系统类型
                final String targetFileName = greaterThen114 ?
                        String.format("chromedriver-%s/%s", systemName, tail)
                        : tail;
                if (entry.getName().equals(targetFileName)) {
                    String fileName = version + "_" + tail;
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
            driver.setExecutable(true);
            driver.setWritable(true);
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
