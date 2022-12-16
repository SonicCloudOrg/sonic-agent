package org.cloud.sonic.agent.tools;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.cloud.sonic.agent.enums.OSType;

import lombok.extern.slf4j.Slf4j;

/**
 * @author qilululi
 * 用于命令的工具
 */
@Slf4j
public class CommandTool {
        /**
     * 输出无返回结果
     * <p/>
     * grep -n -e 'Begin installing package' -A 9999999999999  log.log > gqtest1.log
     *
     * @param command
     * @return
     */
    public static List<String> exec(String command) {
        List<String> result = null;

        if (org.apache.commons.lang3.StringUtils.isBlank(command)) {
            return result;
        }
        log.info("command:" + command, "INFO");

        String[] commands = null;
        OSType osType = getOsType();
        if (osType == null) {
            return result;
        }

        if (OSType.MacOS.name().equals(osType.name())) {
            commands = new String[]{"/bin/sh", "-c", command};
        } else if (OSType.Windows.name().equals(osType.name())) {
            commands = new String[]{"cmd", "/c", command};
        } else if (OSType.Linux.name().equals(osType.name())) {
            commands = new String[]{"/bin/sh", "-c", command};
        }

        if (commands == null) {
            return result;
        }

        Process process = null;
        BufferedReader in = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(commands);
            builder.redirectErrorStream(true);

            result = new ArrayList<>();

            process = builder.start();
            in = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            String line;
            while ((line = in.readLine()) != null) {
                result.add(line);
            }
            process.waitFor();
        } catch (Exception e) {
            log.error(e.getMessage(), new Throwable(e));
        } finally {
            if (process != null) {
                process.destroy();
            }

            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                    log.error(e.getMessage(), new Throwable(e));
                }
            }
        }

        return result;
    }

    /**
     * 系统命令执行
     *
     * @param commandList
     *
     *                    List<String> commandList = new ArrayList<String>();
     *                    commandList.add("wget");
     *                    commandList.add("-c");
     *                    commandList.add("-t");
     *                    commandList.add("5");
     *                    commandList.add("-T");
     *                    commandList.add("30");
     *                    commandList.add(sourceUrl);
     *                    commandList.add("-O");
     *                    commandList.add(outfullname);
     *
     * @return
     */
    public static List<String> exec(List<String> commandList) {
        List<String> result = null;

        if (commandList == null || commandList.size() == 0) {
            return result;
        }

        log.info("command:" + commandList, "INFO");

        Process process = null;
        BufferedReader in = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(commandList);
            builder.redirectErrorStream(true);
            result = new ArrayList<>();

            process = builder.start();
            in = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));

            String line;
            while ((line = in.readLine()) != null) {
                result.add(line);
            }

            process.waitFor();

            return result;
        } catch (Exception e) {
            log.error(e.getMessage(), new Throwable(e));
        } finally {
            if (process != null) {
                process.destroy();
            }

            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                    log.error(e.getMessage(), new Throwable(e));
                }
            }
        }

        return null;
    }

    /**
     * 获取系统类型
     *
     * @return
     */
    public static OSType getOsType() {
        OSType osType = null;

        String os = System.getProperty("os.name");

        if (os.toLowerCase().indexOf("mac") >= 0) {
            osType = OSType.MacOS;
        } else if (os.toLowerCase().indexOf("windows") >= 0) {
            osType = OSType.Windows;
        } else if (os.toLowerCase().indexOf("linux") >= 0) {
            osType = OSType.Linux;
        } else {
            log.error("os is invalid! os:" + os, "ERROR");
        }

        return osType;
    }
}
