//package org.cloud.sonic.agent.tests.script;
//
//import org.apache.commons.exec.CommandLine;
//import org.apache.commons.exec.DefaultExecutor;
//import org.apache.commons.exec.PumpStreamHandler;
//
//import java.io.*;
//import java.util.ArrayList;
//
///**
// * @author HappyTinaFu
// * @des 脚本执行管理
// * @date 2022/09/05 15:57
// */
//public class ScriptExcuterManager {
//
//    public ExcuterResult scriptExcuter(String command, ArrayList<String> args) throws IOException {
//        CommandLine cmdLine = new CommandLine(command);
//        for (String arg : args) {
//            cmdLine.addArgument(arg, false);
//        }
//
//        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
//
//        DefaultExecutor executor = new DefaultExecutor();
//        executor.setStreamHandler(streamHandler);
//
//        int exitCode = executor.execute(cmdLine);
//        ExcuterResult result = new ExcuterResult(exitCode,outputStream.toString());
//        return result;
//    }
//}
