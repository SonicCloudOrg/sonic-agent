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
package org.cloud.sonic.agent.tests.script;

import org.cloud.sonic.agent.common.interfaces.StepType;
import org.cloud.sonic.agent.tests.LogUtil;
import org.cloud.sonic.agent.tests.RunStepThread;
import org.cloud.sonic.agent.tests.handlers.AndroidStepHandler;
import org.cloud.sonic.agent.tests.handlers.IOSStepHandler;

import java.io.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PythonScriptImpl implements ScriptRunner {
    @Override
    public void runAndroid(AndroidStepHandler androidStepHandler, String script) {
        if (execIsFailed(
                androidStepHandler.log,
                script,
                androidStepHandler.getAndroidDriver().getSessionId(),
                androidStepHandler.getiDevice().getSerialNumber(),
                androidStepHandler.getGlobalParams().toJSONString()
        )) {
            throw new RuntimeException("Run script failed");
        }
    }

    @Override
    public void runIOS(IOSStepHandler iosStepHandler, String script) {
        if (execIsFailed(
                iosStepHandler.log,
                script,
                iosStepHandler.getDriver().getSessionId(),
                iosStepHandler.getUdId(),
                iosStepHandler.getGlobalParams().toJSONString()
        )) {
            throw new RuntimeException("Run script failed");
        }
    }

    private boolean execIsFailed(LogUtil log, String script, String... params) {
        final int BUFFER_SIZE = 0x1000;
        final RunStepThread currentThread = (RunStepThread) Thread.currentThread();
        Process process = null;
        try {
            File temp = new File("test-output" + File.separator + UUID.randomUUID() + ".py");
            temp.createNewFile();
            FileWriter fileWriter = new FileWriter(temp);
            fileWriter.write(script);
            fileWriter.close();
            String[] command = new String[params.length + 2];
            command[0] = "python";
            command[1] = temp.getAbsolutePath();
            System.arraycopy(params, 0, command, 2, params.length);
            process = Runtime.getRuntime().exec(command);
            char[] buffer = new char[BUFFER_SIZE];
            int len;
            try (BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                try (BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    while (!currentThread.isStopped()) {
                        boolean exited = process.waitFor(1, TimeUnit.SECONDS);
                        while ((len = stdout.read(buffer, 0 , BUFFER_SIZE)) > 0) {
                            log.sendStepLog(StepType.INFO, "Script stdout", new String(buffer, 0, len));
                        }
                        while ((len = stderr.read(buffer, 0 , BUFFER_SIZE)) > 0) {
                            log.sendStepLog(StepType.WARN, "Script stderr", new String(buffer, 0, len));
                        }
                        if (exited) {
                            if (process.exitValue() == 0) {
                                log.sendStepLog(StepType.INFO, "Script exit", "0");
                                return false;
                            } else {
                                log.sendStepLog(StepType.ERROR, "Script exit", String.valueOf(process.exitValue()));
                                return !currentThread.isStopped();
                            }
                        }
                    }
                }
            }
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException("Run script failed", e);
        } finally {
            if (null != process && process.isAlive()) {
                process.destroyForcibly();
                log.sendStepLog(StepType.WARN, "Script force killed", "");
            }
        }
        return !currentThread.isStopped();
    }

}
