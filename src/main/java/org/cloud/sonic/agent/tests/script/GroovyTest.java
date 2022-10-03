package org.cloud.sonic.agent.tests.script;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.cloud.sonic.driver.android.AndroidDriver;
import org.cloud.sonic.vision.cv.SIFTFinder;

public class GroovyTest {
    public static void main(String[] args) {
        Binding binding = new Binding();
        binding.setVariable("SIFTFinder", new SIFTFinder());
        GroovyShell gs = new GroovyShell(binding);
        Object value = gs.evaluate(
                "import org.cloud.sonic.vision.cv.SIFTFinder;" +
                        "SIFTFinder a = new SIFTFinder();" +
                        "println a.toString();" +
                        "return 1"
        );
        System.out.println(value.equals(1));
//        System.out.println(binding.getVariable("abc").equals(123));
    }
}
