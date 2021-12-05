package com.sonic.agent.tools;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

/**
 * 检查环境
 *
 * @author JayWenStar, Eason
 * @date 2021/12/5 15:00
 */
@Component
public class EnvCheckTool implements ApplicationListener<ContextRefreshedEvent> {

    public static String system;
    public static String javaPath;
    public static String sdkPath;
    public static String adbPath;
    public static String adbVersion;
    public static String nodePath;
    public static String nodeVersion;
    public static String npmPath;
    public static String npmVersion;
    public static String appiumPath;
    public static String appiumVersion;

    @Value("${modules.webview.chrome-driver-path}")
    public String chromeDriverPath;
    @Value("${modules.android.enable}")
    public boolean androidEnAble;
    @Value("${modules.android.use-adbkit}")
    public boolean adbkitEnAble;
    @Value("${modules.ios.enable}")
    public boolean iosEnAble;
    @Value("${modules.appium.enable}")
    public boolean appiumEnAble;
    @Value("${modules.webview.enable}")
    public boolean webviewEnAble;
    public static String chromeDriverVersion;

    static {
        system = System.getProperty("os.name").toLowerCase();
    }

    @Override
    public void onApplicationEvent(@NonNull ContextRefreshedEvent event) {
        ConfigurableApplicationContext context = (ConfigurableApplicationContext) event.getApplicationContext();
        System.out.println("===================== 开始检查配置环境 =====================");
        try {
            if (androidEnAble) {
                checkSDK();
                checkAdb();
            }
            if (appiumEnAble) {
                checkJavaHome();
                checkAppium();
            }
            if (adbkitEnAble || appiumEnAble) {
                checkNode();
                checkNpm();
            }
            if (webviewEnAble) {
                checkChromeDriver();
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println("===================== 配置环境检查结束 =====================");
            context.close();
            System.exit(0);
        }
        System.out.println("===================== 配置环境检查结果 =====================");
        System.out.println(this);
        System.out.println("===================== 配置环境检查结束 =====================");
    }

    /**
     * 检查java环境
     */
    public void checkJavaHome() {
        String type = "检查 JAVA_HOME 环境变量";
        javaPath = System.getenv("JAVA_HOME");
        if (!StringUtils.hasText(javaPath)) {
            printFail(type);
            throw new RuntimeException("系统变量【JAVA_HOME】返回值为空，" +
                    "可前往https://www.oracle.com/java/technologies/downloads/下载jdk并设置JAVA_HOME系统变量");
        }
        printPass(type);
    }

    /**
     * 检查chromedriver环境
     */
    public void checkChromeDriver() throws IOException, InterruptedException {
        String type = "检查 chromeDriver 环境";
        if (system.contains("win")) {
            chromeDriverPath = "\"" + chromeDriverPath + "\"";
        } else {
            chromeDriverPath = StringUtils.replace(chromeDriverPath, " ", "\\ ");
        }
        String commandStr = chromeDriverPath + " -v";
        chromeDriverVersion = exeCmd(false, commandStr);
        if (!StringUtils.hasText(chromeDriverVersion)) {
            printFail(type);
            throw new RuntimeException(String.format("执行命令【%s】返回值为空，可前往http://npm.taobao.org/mirrors/chromedriver/下载" +
                    "与Agent的谷歌浏览器版本对应的driver到谷歌浏览器安装目录下（谷歌浏览器地址栏输入chrome://version可看到安装目录）", commandStr));
        }
        printPass(type);
    }

    /**
     * 检查sdk环境
     */
    public void checkSDK() throws IOException, InterruptedException {
        String type = "检查 ANDROID_HOME 环境变量";
        sdkPath = findLocalEnvPath("ANDROID_HOME");
        if (!StringUtils.hasText(sdkPath)) {
            printFail(type);
            throw new RuntimeException(String.format("系统变量【%s】返回值为空，可参考https://www.cnblogs.com/nebie/p/9145627.html" +
                    "下载安卓SDK并设置ANDROID_HOME环境变量", sdkPath));
        }
        printPass(type);
    }

    /**
     * 检查adb环境
     */
    public void checkAdb() throws IOException, InterruptedException {
        String type = "检查 ADB 环境";
        String commandStr = "adb version";
        adbPath = findCommandPath("adb");
        adbVersion = exeCmd(false, commandStr);
        if (!StringUtils.hasText(adbVersion)) {
            printFail(type);
            throw new RuntimeException(String.format("执行命令【%s】返回值为空，请确保安卓SDK目录下的platform-tools有adb工具", commandStr));
        }
        printPass(type);
    }

    /**
     * 检查node环境
     */
    public void checkNode() throws IOException, InterruptedException {
        String type = "检查 Node 环境";
        String commandStr = "node -v";
        nodePath = findCommandPath("node");
        nodeVersion = exeCmd(false, commandStr);
        if (!StringUtils.hasText(nodeVersion)) {
            printFail(type);
            throw new RuntimeException(String.format("执行命令【%s】返回值为空，可前往https://nodejs.org/zh-cn/下载", commandStr));
        }
        printPass(type);
    }

    /**
     * 检查npm环境
     */
    public void checkNpm() throws IOException, InterruptedException {
        String type = "检查 npm 环境";
        String commandStr = "npm -v";
        npmPath = findCommandPath("npm");
        npmVersion = exeCmd(false, commandStr);
        if (!StringUtils.hasText(adbVersion)) {
            printFail(type);
            throw new RuntimeException(String.format("执行命令【%s】返回值为空，可前往https://nodejs.org/zh-cn/下载", commandStr));
        }
        printPass(type);
    }

    /**
     * 检查appium环境
     */
    public void checkAppium() throws IOException, InterruptedException {
        String type = "检查 Appium 环境";
        String commandStr = "appium -v";
        appiumPath = findCommandPath("appium");
        appiumVersion = exeCmd(false, commandStr);
        if (!StringUtils.hasText(appiumVersion)) {
            printFail(type);
            throw new RuntimeException(String.format("执行命令【%s】返回值为空，可使用npm i -g appium命令安装", commandStr));
        }
        printPass(type);
    }

    public String findLocalEnvPath(String name) throws IOException, InterruptedException {

        String path = "";
        if (system.contains("win")) {
            path = exeCmd(false, "cmd", "/c", "echo %" + name + "%");
        } else if (system.contains("linux") || system.contains("mac")) {
            path = exeCmd(false, "sh", "-c", "echo $" + name);
        } else {
            throw new RuntimeException("匹配系统失败，请联系开发者支持，当前系统为：" + system);
        }

        return path;
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
            throw new RuntimeException(String.format("获取【%s】命令路径失败，请检查环境配置，当前系统为%s", command, system));
        }

        return path;
    }

    public void printPass(String s) {
        System.out.println("\33[32;1m" + s + "通过 ✔\033[0m");
    }

    public void printFail(String s) {
        System.out.println("\33[31;1m" + s + "不通过 ❌\033[0m");
    }

    public String printInfo(String s) {
        return "\33[34;1m" + s + "\033[0m";
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

        return result;
    }

    @Override
    public String toString() {
        return printInfo("JAVA_HOME: ") + javaPath +
                printInfo("ANDROID_HOME: ") + sdkPath +
                printInfo("ADB path: ") + adbPath +
                printInfo("ADB version: ") + adbVersion +
                printInfo("chromeDriver path: ") + chromeDriverPath + "\n" +
                printInfo("chromeDriver version: ") + chromeDriverVersion +
                printInfo("Node path: ") + nodePath +
                printInfo("Node version: ") + nodeVersion +
                printInfo("npm path: ") + npmPath +
                printInfo("npm version: ") + npmVersion +
                printInfo("Appium path: ") + appiumPath +
                printInfo("Appium version: ") + appiumVersion +
                printInfo("System: ") + system;
    }
}