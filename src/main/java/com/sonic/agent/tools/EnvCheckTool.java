package com.sonic.agent.tools;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

/**
 * 检查环境
 *
 * @author JayWenStar, Eason
 * @date 2021/12/5 15:00
 */
@Component
public class EnvCheckTool  {

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
    public static String tidevicePath = "unknown \n";
    public static String tideviceVersion = "unknown \n";

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

    @Bean
    public boolean checkEnv(ConfigurableApplicationContext context) {
        System.out.println("===================== 开始检查配置环境 =====================");
        try {
            if (androidEnAble) {
                checkSDK();
                checkAdb();
            }
            if (iosEnAble) {
                checkTIDevice();
            }
            //adbkit和appium依赖node服务
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
            if (webviewEnAble) {
                checkChromeDriver();
            }
        } catch (Exception e) {
            System.out.println(printInfo(e.getMessage()));
            System.out.println("===================== 配置环境检查结束 =====================");
            context.close();
            System.exit(0);
        }
        System.out.println("===================== 配置环境检查结果 =====================");
        System.out.println(this);
        System.out.println("===================== 配置环境检查结束 =====================");
        return true;
    }

    /**
     * 检查java环境
     */
    public void checkJavaHome() throws IOException, InterruptedException {
        String type = "👉 检查 JAVA_HOME 环境变量";
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
     * 检查chromedriver环境
     */
    public void checkChromeDriver() throws IOException, InterruptedException {
        String type = "👉 检查 chromeDriver 环境";
        if (system.contains("win")) {
            chromeDriverPath = "\"" + chromeDriverPath + "\"";
        } else {
            chromeDriverPath = StringUtils.replace(chromeDriverPath, " ", "\\ ");
        }
        String commandStr = chromeDriverPath + " -v";
        try {
            chromeDriverVersion = exeCmd(false, commandStr);
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
            printFail(type);
            throw new RuntimeException(String.format("提示：可前往http://npm.taobao.org/mirrors/chromedriver/下载" +
                    "与Agent的谷歌浏览器版本对应的driver到谷歌浏览器安装目录下（谷歌浏览器地址栏输入chrome://version可看到安装目录）"));
        }
        printPass(type);
    }

    /**
     * 检查sdk环境
     */
    public void checkSDK() {
        String type = "👉 检查 ANDROID_HOME 环境变量";
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
        String type = "👉 检查 ADB 环境";
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
     * 检查tidevice环境
     */
    public void checkTIDevice() throws IOException, InterruptedException {
        String type = "👉 检查 tidevice 环境";
        String commandStr = "tidevice -v";
        try {
            tidevicePath = findCommandPath("tidevice");
            tideviceVersion = exeCmd(false, commandStr);
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
            printFail(type);
            throw new RuntimeException(String.format("提示：可前往https://github.com/alibaba/taobao-iphone-device查看安装方式"));
        }
        printPass(type);
    }

    /**
     * 检查adbkit环境
     */
    public void checkAdbKit() throws IOException, InterruptedException {
        String type = "👉 检查 adbkit 环境";
        String commandStr = "adbkit -v";
        try {
            adbKitPath = findCommandPath("adbkit");
            adbKitVersion = exeCmd(false, commandStr);
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
            printFail(type);
            throw new RuntimeException(String.format("提示：可使用npm i -g adbkit安装"));
        }
        printPass(type);
    }

    /**
     * 检查node环境
     */
    public void checkNode() throws IOException, InterruptedException {
        String type = "👉 检查 Node 环境";
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
        String type = "👉 检查 npm 环境";
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
        String type = "👉 检查 Appium 环境";
        String commandStr = "appium -v";
        try {
            appiumPath = findCommandPath("appium");
            appiumVersion = exeCmd(false, commandStr);
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
            printFail(type);
            throw new RuntimeException(String.format("提示：可使用npm i -g appium命令安装"));
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

        if (!StringUtils.hasText(result)) {
            List<String> c = Arrays.stream(commandStr).toList();
            throw new RuntimeException(String.format("执行【%s】命令出错！", c.get(c.size() - 1)));
        }
        return result;
    }

    @Override
    public String toString() {
        return printInfo("JAVA_HOME（系统PATH环境变量）: ") + javaPath + "\n" +
                printInfo("java version（运行当前jar的java版本）: ") + javaVersion + "\n" +
                printInfo("ANDROID_HOME（系统PATH环境变量）: ") + sdkPath + "\n" +
                printInfo("ADB path: ") + adbPath +
                printInfo("ADB version: ") + adbVersion +
                printInfo("chromeDriver path: ") + chromeDriverPath + "\n" +
                printInfo("chromeDriver version: ") + chromeDriverVersion +
                printInfo("Node path: ") + nodePath +
                printInfo("Node version: ") + nodeVersion +
                printInfo("npm path: ") + npmPath +
                printInfo("npm version: ") + npmVersion +
                printInfo("adbkit path: ") + adbKitPath +
                printInfo("adbkit version: ") + adbKitVersion +
                printInfo("Appium path: ") + appiumPath +
                printInfo("Appium version: ") + appiumVersion +
                printInfo("tidevice path: ") + tidevicePath +
                printInfo("tidevice version: ") + tideviceVersion +
                printInfo("System: ") + system;
    }
}