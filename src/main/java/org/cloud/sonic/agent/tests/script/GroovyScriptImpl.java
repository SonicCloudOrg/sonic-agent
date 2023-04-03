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

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.cloud.sonic.agent.common.interfaces.StepType;
import org.cloud.sonic.agent.tests.LogUtil;
import org.cloud.sonic.agent.tests.RunStepThread;
import org.cloud.sonic.agent.tests.handlers.AndroidStepHandler;
import org.cloud.sonic.agent.tests.handlers.IOSStepHandler;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class GroovyScriptImpl implements ScriptRunner {
    @Override
    public void runAndroid(AndroidStepHandler androidStepHandler, String script) {
        Binding binding = new Binding();
        binding.setVariable("androidStepHandler", androidStepHandler);
        if (evalIsFailed(androidStepHandler.log, script, binding)) {
            throw new RuntimeException("Run script failed");
        }
    }

    @Override
    public void runIOS(IOSStepHandler iosStepHandler, String script) {
        Binding binding = new Binding();
        binding.setVariable("iosStepHandler", iosStepHandler);
        if (evalIsFailed(iosStepHandler.log, script, binding)) {
            throw new RuntimeException("Run script failed");
        }
    }

    private boolean evalIsFailed(LogUtil log, String script, Binding binding) {
        FutureTask<Object> task = new FutureTask<>(() -> new GroovyShell(binding).evaluate(script));
        final RunStepThread currentThread = (RunStepThread) Thread.currentThread();
        Thread evalThread = new Thread(task);
        evalThread.start();
        while (!currentThread.isStopped()) {
            try {
                Object ret = task.get(1, TimeUnit.SECONDS);
                if (null != ret) {
                    log.sendStepLog(StepType.INFO, "Script result", ret.toString());
                }
                return false;
            } catch (TimeoutException ignore) {
                if (currentThread.isStopped()) {
                    evalThread.stop();
                    log.sendStepLog(StepType.WARN, "Script force killed", "");
                    return !currentThread.isStopped();
                }
            } catch (ExecutionException | InterruptedException e) {
                log.sendStepLog(StepType.ERROR, "Script error", e.toString());
                return !currentThread.isStopped();
            }
        }
        return true;
    }
}
