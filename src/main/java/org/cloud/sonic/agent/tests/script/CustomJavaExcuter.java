//package org.cloud.sonic.agent.tests.script;
//
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
//public class CustomJavaExcuter {
//    static ScriptExcuterManager excuterManager = new ScriptExcuterManager();
//
//    public ExcuterResult compilerClass(String java_file_path,String external_jar_Path,String class_save_dir) {
//        ArrayList<String> args = new ArrayList<String>();
//        args.add("-cp");
//        args.add(".:"+external_jar_Path+"*");
//        args.add(java_file_path);
//        args.add("-d");
//        args.add(class_save_dir);
//        try {
//            return excuterManager.scriptExcuter("javac",args);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return new ExcuterResult(-1,"compiler fail");
//    }
//
//    public ExcuterResult excuteClass(String external_jar_Path,String class_save_dir,String class_name,String[] excute_args) {
//        ArrayList<String> args = new ArrayList<String>();
//        args.add("-Djava.ext.dirs=" + external_jar_Path);
//        args.add("-classpath");
//        args.add(class_save_dir);
//        args.add(class_name);
//        for (String arg : excute_args) {
//            args.add(arg);
//        }
//        try {
//            return excuterManager.scriptExcuter("java",args);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return new ExcuterResult(-1,"excute class fail");
//    }
//
//    /**
//     * 获取类的全名称
//     */
//    public String getFullClassName(String sourceCode) {
//        String className = "";
//        Pattern pattern = Pattern.compile("package\\s+\\S+\\s*;");
//        Matcher matcher = pattern.matcher(sourceCode);
//        if (matcher.find()) {
//            className = matcher.group().replaceFirst("package", "").replace(";", "").trim() + ".";
//        }
//
//        pattern = Pattern.compile("class\\s+\\S+\\s+\\{");
//        matcher = pattern.matcher(sourceCode);
//        if (matcher.find()) {
//            className += matcher.group().replaceFirst("class", "").replace("{", "").trim();
//        }
//        return className;
//    }
//
//    public static void main( String[] args ){
//        String java_file_path = "App.java";
//        String custom_class_base_dir = "/";
//        String external_jar_Path = "/lib/";
//
//        CustomJavaExcuter javaExcuter = new CustomJavaExcuter();
//        System.out.println(javaExcuter.compilerClass(java_file_path,external_jar_Path,custom_class_base_dir));
//        System.out.println(javaExcuter.excuteClass(external_jar_Path,custom_class_base_dir,"App", new String[]{"this", "is", "demo"}));
//
//        System.out.println(javaExcuter.getFullClassName("package com.tinafu;\n" +
//                "import org.apache.commons.exec.CommandLine;\n" +
//                "\n" +
//                "/**\n" +
//                " * Hello world!\n" +
//                " *\n" +
//                " */\n" +
//                "public class App \n" +
//                "{\n" +
//                "    public static void main( String[] args )\n" +
//                "    {\n" +
//                "        System.out.println( \"Hello World!\" );\n" +
//                "        for(int i=0;i<args.length;i++) {\n" +
//                "            System.out.println(\"args[\" + i + \"]\" + args[i]);\n" +
//                "        }\n" +
//                "        }\n"));
//    }
//
//}
