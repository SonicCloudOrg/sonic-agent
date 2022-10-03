package org.cloud.sonic.agent.tests.script;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.cloud.sonic.driver.android.AndroidDriver;

public class GroovyTest {
    public static void main(String[] args) {
        Binding binding = new Binding();
        AndroidDriver a;
//        binding.setVariable("var", 5);
        GroovyShell gs = new GroovyShell(binding);
        Object value = gs.evaluate("a = new AndroidDriver(\"http://localhost:8094\");" +
                "println a.getSessionId();" +
                "return 1"
        );
        System.out.println(value.equals(1));
//        System.out.println(binding.getVariable("abc").equals(123));
    }
}
