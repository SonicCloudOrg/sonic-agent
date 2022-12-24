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
package org.cloud.sonic.agent.tools;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * 检查环境
 *
 * @author JayWenStar, Eason
 * @date 2021/12/5 15:00
 */
@Component
public class EnvCheckTool {

    public static String system;
    public static String sdkPath = "unknown \n";
    public static String adbPath = "unknown \n";
    public static String adbVersion = "unknown \n";

    @Value("${modules.android.enable}")
    public boolean androidEnAble;

    static {
        system = System.getProperty("os.name").toLowerCase();
    }

    @Bean
    public boolean checkEnv(ConfigurableApplicationContext context) {
        System.out.println("===================== Checking the Environment =====================");
        try {
            if (androidEnAble) {
                checkSDK();
                checkAdb();
            }
            checkFiles();
        } catch (Exception e) {
            System.out.println(printInfo(e.getMessage()));
            System.out.println("========================== Check Completed ==========================");
            context.close();
            System.exit(0);
        }
        System.out.println("=========================== Check results ===========================");
        System.out.println(this);
        System.out.println("========================== Check Completed ==========================");
        return true;
    }

    /**
     * 检查本地文件
     */
    public void checkFiles() {
        String type = "Check local resource";
        File config = new File("config/application-sonic-agent.yml");
        File mini = new File("mini");
        File plugins = new File("plugins");
        // fixme 本地测试请关闭授权
        if (system.contains("linux") || system.contains("mac")) {
            try {
                Runtime.getRuntime().exec(new String[]{"sh", "-c", String.format("chmod -R 777 %s", new File("").getAbsolutePath())});
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (config.exists()
                && mini.exists()
                && plugins.exists()) {
            printPass(type);
        } else {
            printFail(type);
            throw new RuntimeException("提示：请确保当前目录下有config(内含application-sonic-agent.yml)、mini、plugins文件夹");
        }
    }

    /**
     * 检查sdk环境
     */
    public void checkSDK() {
        String type = "Check ANDROID_HOME Path";
        sdkPath = System.getenv("ANDROID_HOME");
        if (!StringUtils.hasText(sdkPath)) {
            System.out.println("系统变量【ANDROID_HOME】返回值为空！");
            printFail(type);
            throw new RuntimeException(String.format("提示：可参考 https://sonic-cloud.cn/deploy?tag=agent " +
                    "下载安卓SDK并设置ANDROID_HOME环境变量"));
        }
        printPass(type);
    }

    /**
     * 检查adb环境
     */
    public void checkAdb() throws IOException, InterruptedException {
        String type = "Check ADB env";
        String commandStr = "adb version";
        try {
            adbPath = findCommandPath("adb");
            adbVersion = exeCmd(false, commandStr);
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
            printFail(type);
            throw new RuntimeException(String.format("提示：请确保安卓SDK目录下的platform-tools有adb工具"));
        }
        printPass(type);
    }

    public String findCommandPath(String command) throws IOException, InterruptedException {

        String path = "";
        if (system.contains("win")) {
            path = exeCmd(false, "cmd", "/c", "where " + command);
        } else if (system.contains("linux") || system.contains("mac")) {
            path = exeCmd(false, "sh", "-c", "which " + command);
        } else {
            throw new RuntimeException("匹配系统失败，请联系开发者支持，当前系统为：" + system);
        }

        if (!StringUtils.hasText(path)) {
            throw new RuntimeException(String.format("获取【%s】路径出错！", command));
        }

        return path;
    }

    public void printPass(String s) {
        if (system.contains("win")) {
            System.out.println("→ " + s + " Pass √");
        } else {
            System.out.println("\33[32;1m👉 " + s + " Pass ✔\033[0m");
        }
    }

    public void printFail(String s) {
        if (system.contains("win")) {
            System.out.println("→ " + s + " Fail ×");
        } else {
            System.out.println("\33[31;1m👉 " + s + " Fail ❌\033[0m");
        }
    }

    public String printInfo(String s) {
        if (system.contains("win")) {
            return "· " + s;
        } else {
            return "\33[34;1m" + s + "\033[0m";
        }
    }

    public static String exeCmd(boolean getError, String commandStr) throws IOException, InterruptedException {

        if (system.contains("win")) {
            return exeCmd(getError, "cmd", "/c", commandStr);
        }
        if (system.contains("linux") || system.contains("mac")) {
            return exeCmd(getError, "sh", "-c", commandStr);
        }
        throw new RuntimeException("匹配系统失败，请联系开发者支持，当前系统为：" + system);
    }

    public static String exeCmd(boolean getError, String... commandStr) throws IOException, InterruptedException {

        String result = "";
        Process ps = Runtime.getRuntime().exec(commandStr);
        ps.waitFor();
        BufferedReader br = new BufferedReader(new InputStreamReader(ps.getInputStream(), Charset.forName("GBK")));
        ;
        if (getError && ps.getErrorStream().available() > 0) {
            br = new BufferedReader(new InputStreamReader(ps.getErrorStream(), Charset.forName("GBK")));
        }
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line).append("\n");
        }
        result = sb.toString();

        if (!StringUtils.hasText(result)) {
            Object[] c = Arrays.stream(commandStr).toArray();
            throw new RuntimeException(String.format("执行【%s】命令出错！", c.length > 0 ? c[c.length - 1] : "unknown"));
        }
        return result;
    }

    @Override
    public String toString() {
        return printInfo("ANDROID_HOME: ") + sdkPath + "\n" +
                printInfo("ADB path: ") + adbPath +
                printInfo("ADB version: ") + adbVersion +
                printInfo("System: ") + system;
    }
}