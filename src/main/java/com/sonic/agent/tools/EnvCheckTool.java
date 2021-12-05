package com.sonic.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * @author JayWenStar
 * @date 2021/12/5 02:36 上午
 */
@Component
public class EnvCheckTool implements ApplicationListener<ContextRefreshedEvent> {

    private final Logger log = LoggerFactory.getLogger(EnvCheckTool.class);

    public static String system;
    public static String adbPath;
    public static String adbVersion;
    public static String appiumPath;
    public static String appiumVersion;

    @Value("${modules.webview.chrome-driver-path}")
    public String chromeDriverPath;
    @Value("${modules.android.enable}")
    public boolean androidEnAble;
    @Value("${modules.ios.enable}")
    public boolean iosEnAble;
    @Value("${modules.webview.enable}")
    public boolean webviewEnAble;
    public static String chromeDriverVersion;

    static {
        system = System.getProperty("os.name").toLowerCase();
    }

    @Override
    public void onApplicationEvent(@NonNull ContextRefreshedEvent event) {
        ConfigurableApplicationContext context = (ConfigurableApplicationContext) event.getApplicationContext();
        try {
            if (androidEnAble) {
                checkAdb();
                checkAppium();
            }
            if (webviewEnAble) {
                checkChromeDriver();
            }
        } catch (Exception e) {
            log.error("环境配置出错，错误信息：{}", e.getMessage());
            context.close();
            System.exit(0);
        }
        log.info(this.toString());
    }

    /**
     * 检查chromedriver环境
     */
    public void checkChromeDriver() throws IOException, InterruptedException {
        log.info("开始检查chrome driver环境");
        if (system.contains("win")) {
            chromeDriverPath = "\"" + chromeDriverPath + "\"";
        } else {
            chromeDriverPath = StringUtils.replace(chromeDriverPath, " ", "\\ ");
        }
        String commandStr = chromeDriverPath + " -v";
        chromeDriverVersion = exeCmd(false, commandStr);
        if (!StringUtils.hasText(chromeDriverVersion)) {
            throw new RuntimeException(String.format("执行命令【%s】返回值为空，系统为：【%s】", commandStr, system));
        }

        log.info("检查chrome driver环境通过");
    }

    /**
     * 检查adb环境
     */
    public void checkAdb() throws IOException, InterruptedException {
        log.info("开始检查 adb 环境");

        String commandStr = "adb version";
        adbPath = findCommandPath("adb");
        adbVersion = exeCmd(false, commandStr);
        if (!StringUtils.hasText(adbVersion)) {
            throw new RuntimeException(String.format("执行命令【%s】返回值为空，系统为：【%s】", commandStr, system));
        }

        log.info("检查 adb 环境通过");
    }

    /**
     * 检查appium环境
     */
    public void checkAppium() throws IOException, InterruptedException {

        log.info("开始检查 appium 环境");

        String commandStr = "appium -v";
        appiumPath = findCommandPath("appium");
        appiumVersion = exeCmd(false, commandStr);
        if (!StringUtils.hasText(appiumVersion)) {
            throw new RuntimeException(String.format("执行命令【%s】返回值为空，系统为：【%s】", commandStr, system));
        }

        log.info("检查 appium 环境通过");
    }

    public String findCommandPath(String command) throws IOException, InterruptedException {

        String path = "";
        if (system.contains("win")) {
            path = exeCmd(false, "cmd", "/c", "where " +  command);
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


    public static String exeCmd(boolean getError, String commandStr) throws IOException, InterruptedException {

        if (system.contains("win")) {
            return exeCmd(getError, "cmd", "/c", commandStr);
        }
        if (system.contains("linux") || system.contains("mac")) {
            return exeCmd(getError, "sh", "-c", commandStr);
        }
        throw new RuntimeException("匹配系统失败，请联系开发者支持，当前系统为：" + system);
    }

    public static String exeCmd(boolean getError, String...commandStr) throws IOException, InterruptedException {

        String result = "";
        Process ps = Runtime.getRuntime().exec(commandStr);
        ps.waitFor();
        BufferedReader br = new BufferedReader(new InputStreamReader(ps.getInputStream(), Charset.forName("GBK")));;
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
        return "当前环境信息:\n" +
                "adb path: " + adbPath +
                "adb version: " + adbVersion +
                "chrome driver path: " + chromeDriverPath + "\n" +
                "chrome driver version: " + chromeDriverVersion +
                "appium path: " + appiumPath +
                "appium version: " + appiumVersion +
                "system: " + system + "\n";
    }
}