/*
 *  Copyright (C) [SonicCloudOrg] Sonic Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
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

    /**
     * 全局环境变量的JAVA_HOME，被appium使用，绝大多数情况下路径都能反映版本
     */
    public static String javaPath = "unknown \n";

    /**
     * 运行时的Java Version，假如上面的JAVA_HOME指向JDK 16，而启动的时候用 /{path}/JDK 17/java -jar 启动agent
     * 则此处javaVersion=JDK 17
     */
    public static String javaVersion = "unknown \n";

    public static String system;
    public static String sdkPath = "unknown \n";
    public static String adbPath = "unknown \n";
    public static String adbVersion = "unknown \n";
    public static String nodePath = "unknown \n";
    public static String nodeVersion = "unknown \n";
    public static String npmPath = "unknown \n";
    public static String npmVersion = "unknown \n";
    public static String appiumPath = "unknown \n";
    public static String appiumVersion = "unknown \n";
    public static String adbKitPath = "unknown \n";
    public static String adbKitVersion = "unknown \n";

    @Value("${modules.android.enable}")
    public boolean androidEnAble;
    @Value("${modules.android.use-adbkit}")
    public boolean adbkitEnAble;
    @Value("${modules.appium.enable}")
    public boolean appiumEnAble;

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
            if (adbkitEnAble || appiumEnAble) {
                checkNode();
                checkNpm();
            }
            if (appiumEnAble) {
                checkJavaHome();
                checkAppium();
            }
            if (adbkitEnAble) {
                checkAdbKit();
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
        File webview = new File("webview");
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
        if (webview.exists()
                && config.exists()
                && mini.exists()
                && plugins.exists()) {
            printPass(type);
        } else {
            printFail(type);
            throw new RuntimeException("提示：请确保当前目录下有webview、config(内含application-prod.yml)、mini、plugins文件夹");
        }
    }

    /**
     * 检查java环境
     */
    public void checkJavaHome() {
        String type = "Check JAVA_HOME Path";
        javaPath = System.getenv("JAVA_HOME");
        javaVersion = System.getProperty("java.version");
        if (!StringUtils.hasText(javaPath)) {
            System.out.println("系统变量【JAVA_HOME】返回值为空！");
            printFail(type);
            throw new RuntimeException("提示：可前往https://www.oracle.com/java/technologies/downloads/下载jdk并设置JAVA_HOME系统变量");
        }
        printPass(type);
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
            throw new RuntimeException(String.format("提示：可参考https://www.cnblogs.com/nebie/p/9145627.html" +
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

    /**
     * 检查adbkit环境
     */
    public void checkAdbKit() throws IOException, InterruptedException {
        String type = "Check adbkit env (Next version deprecated) ";
        String commandStr = "adbkit -V";
        try {
            adbKitPath = findCommandPath("adbkit");
            adbKitVersion = exeCmd(false, commandStr);
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
            printFail(type);
            throw new RuntimeException(String.format("提示：可使用[npm i -g adbkit]安装"));
        }
        printPass(type);
    }

    /**
     * 检查node环境
     */
    public void checkNode() throws IOException, InterruptedException {
        String type = "Check Node env (Next version deprecated) ";
        String commandStr = "node -v";
        try {
            nodePath = findCommandPath("node");
            nodeVersion = exeCmd(false, commandStr);
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
            printFail(type);
            throw new RuntimeException(String.format("提示：可前往https://nodejs.org/zh-cn/下载"));
        }
        printPass(type);
    }

    /**
     * 检查npm环境
     */
    public void checkNpm() throws IOException, InterruptedException {
        String type = "Check npm env (Next version deprecated) ";
        String commandStr = "npm -v";
        try {
            npmPath = findCommandPath("npm");
            npmVersion = exeCmd(false, commandStr);
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
            printFail(type);
            throw new RuntimeException(String.format("提示：可前往https://nodejs.org/zh-cn/下载"));
        }
        printPass(type);
    }

    /**
     * 检查appium环境
     */
    public void checkAppium() throws IOException, InterruptedException {
        String type = "Check Appium env (Next version deprecated) ";
        String commandStr = "appium -v";
        try {
            appiumPath = findCommandPath("appium");
            appiumVersion = exeCmd(false, commandStr);
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
            printFail(type);
            throw new RuntimeException(String.format("提示：可使用[npm i -g appium]命令安装"));
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
        return printInfo("JAVA_HOME: ") + javaPath + "\n" +
                printInfo("java version: ") + javaVersion + "\n" +
                printInfo("ANDROID_HOME: ") + sdkPath + "\n" +
                printInfo("ADB path: ") + adbPath +
                printInfo("ADB version: ") + adbVersion +
                printInfo("Node path: ") + nodePath +
                printInfo("Node version: ") + nodeVersion +
                printInfo("npm path: ") + npmPath +
                printInfo("npm version: ") + npmVersion +
                printInfo("adbkit path: ") + adbKitPath +
                printInfo("adbkit version: ") + adbKitVersion +
                printInfo("Appium path: ") + appiumPath +
                printInfo("Appium version: ") + appiumVersion +
                printInfo("System: ") + system;
    }
}