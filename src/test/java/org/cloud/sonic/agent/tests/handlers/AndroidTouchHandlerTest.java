package org.cloud.sonic.agent.tests.handlers;

import org.junit.Test;

import java.util.function.BiFunction;

public class AndroidTouchHandlerTest {

    private int width = 720;
    private int height = 1600;

    private int[] transferWithRotation(int x, int y) {
        int directionStatus = 3;
        int _x;
        int _y;
        if (directionStatus == 1 || directionStatus == 3) {
            _x = directionStatus == 1 ? width - y : y - width * 3;
            _y = directionStatus == 1 ? x : -x;
        } else {
            _x = directionStatus == 2 ? width - x : x;
            _y = directionStatus == 2 ? height - y : y;
        }
        return new int[]{_x, _y};
    }

    @Test
    public void test() {
        int[] re = transferWithRotation(1095, 168);
        System.out.println(re[0]);
        System.out.println(re[1]);
    }

    @Test
    public void test_interpolator() {
        int[] re1 = {200, 1200};
        int[] re2 = {200, 300};
        // 过渡总时间
        int duration = 500;
        // 开始时间
        long startTime = System.currentTimeMillis();
        while (true) {
            // 当前时间
            long currentTime = System.currentTimeMillis();
            float timeProgress = (currentTime - startTime) / (float) duration;
            if (timeProgress >= 1.0f) {
                // 已经过渡到结束值，停止过渡
                System.out.println("[" + re2[0] + "," + re2[1] + "]");
                break;
            }
            BiFunction<Integer, Integer, Integer> transitionX = (start, end) ->
                    (int) (start + (end - start) * timeProgress);
            BiFunction<Integer, Integer, Integer> transitionY = (start, end) ->
                    (int) (start + (end - start) * timeProgress); // Y 坐标过渡函数

            int currentX = transitionX.apply(re1[0], re2[0]); // 当前 X 坐标
            int currentY = transitionY.apply(re1[1], re2[1]); // 当前 Y 坐标
            // 使用当前坐标进行操作
            // ...
            System.out.println("[" + currentX + "," + currentY + "]");
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {

            }
        }
    }
}
