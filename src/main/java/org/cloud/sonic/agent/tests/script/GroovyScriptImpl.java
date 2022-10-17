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
package org.cloud.sonic.agent.tests.script;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.cloud.sonic.agent.automation.AndroidStepHandler;
import org.cloud.sonic.agent.automation.IOSStepHandler;

public class GroovyScriptImpl implements GroovyScript {
    @Override
    public void runAndroid(AndroidStepHandler androidStepHandler, String script) {
        Binding binding = new Binding();
        binding.setVariable("androidStepHandler", androidStepHandler);
        GroovyShell gs = new GroovyShell(binding);
        gs.evaluate(script);
    }

    @Override
    public void runIOS(IOSStepHandler iosStepHandler, String script) {
        Binding binding = new Binding();
        binding.setVariable("iosStepHandler", iosStepHandler);
        GroovyShell gs = new GroovyShell(binding);
        gs.evaluate(script);
    }
}
