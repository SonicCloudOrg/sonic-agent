package org.cloud.sonic.agent.tools;

import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.List;

public class ProcessCommandTool {

    public static String getProcessLocalCommandStr(String commandLine) {
        List<String> processLocalCommand = getProcessLocalCommand(commandLine);
        StringBuilder stringBuilder = new StringBuilder("");
        for (String s : processLocalCommand) {
            stringBuilder.append(s);
        }
        return stringBuilder.toString();
    }

    public static List<String> getProcessLocalCommand(String commandLine) {
        Process process = null;
        InputStreamReader inputStreamReader = null;
        InputStreamReader errorStreamReader;
        LineNumberReader consoleInput = null;
        LineNumberReader consoleError = null;
        String consoleInputLine;
        String consoleErrorLine;
        List<String> sdrResult = new ArrayList<String>();
        List<String> sdrErrorResult = new ArrayList<String>();
        try {
            String system = System.getProperty("os.name").toLowerCase();
            if (system.contains("win")) {
                process = Runtime.getRuntime().exec(new String[]{"cmd", "/c", commandLine});
            } else {
                process = Runtime.getRuntime().exec(new String[]{"sh", "-c", commandLine});
            }
            inputStreamReader = new InputStreamReader(process.getInputStream());
            consoleInput = new LineNumberReader(inputStreamReader);
            while ((consoleInputLine = consoleInput.readLine()) != null) {
                sdrResult.add(consoleInputLine);
            }
            errorStreamReader = new InputStreamReader(process.getErrorStream());
            consoleError = new LineNumberReader(errorStreamReader);
            while ((consoleErrorLine = consoleError.readLine()) != null) {
                sdrErrorResult.add(consoleErrorLine);
            }

            int resultCode = process.waitFor();
            if (resultCode > 0 && sdrErrorResult.size() > 0) {
                return sdrErrorResult;
            } else {
                return sdrResult;
            }
        } catch (Exception e) {
            return new ArrayList<String>();
        } finally {
            try {
                if (null != consoleInput) {
                    consoleInput.close();
                }
                if (null != consoleError) {
                    consoleError.close();
                }
                if (null != inputStreamReader) {
                    inputStreamReader.close();
                }
                if (null != process) {
                    process.destroy();
                }
            } catch (Exception e) {
            }
        }
    }
}
