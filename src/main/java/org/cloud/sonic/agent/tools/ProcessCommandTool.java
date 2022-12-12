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
