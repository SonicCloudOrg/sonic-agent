package com.sonic.agent.maps;

import java.io.IOException;

public class test {
    public static void main(String[] args) {

        Thread a = new Thread(()->{
            Process ps = null;
            try {
                ps = Runtime.getRuntime().exec("cmd /c adbkit usb-device-to-tcp -p 6793 MDX0220610012002");
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                ps.waitFor();
            }catch (Exception e){
                ps.destroy();
            }
        });
        a.start();
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        a.interrupt();
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
