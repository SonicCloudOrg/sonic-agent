package org.cloud.sonic.agent.tests.script;

public class ExcuterResult {
    private int exitCode;
    private String result;

    public ExcuterResult(int exitCode, String result) {
        this.exitCode = exitCode;
        this.result = result;
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    @Override
    public String toString() {
        return "ExcuterResult{" +
                "exitCode=" + exitCode +
                ", result='" + result + '\'' +
                '}';
    }
}