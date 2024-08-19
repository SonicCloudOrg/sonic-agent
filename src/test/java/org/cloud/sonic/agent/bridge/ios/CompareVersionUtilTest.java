package org.cloud.sonic.agent.bridge.ios;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by fengbincao on 2024/08/19
 */
public class CompareVersionUtilTest {
    @Test
    public void compareVersion1() {
        String version1 = "17.1.0";
        String version2 = "17.0.0";
        Assert.assertTrue(CompareVersionUtil.compareVersion(version1, version2) > 0);
    }

    @Test
    public void compareVersion2() {
        String version1 = "17.1.0";
        String version2 = "17";
        Assert.assertTrue(CompareVersionUtil.compareVersion(version1, version2) > 0);
    }

    @Test
    public void compareVersion3() {
        String version1 = "17.1";
        String version2 = "17.0";
        Assert.assertTrue(CompareVersionUtil.compareVersion(version1, version2) > 0);
    }

    @Test
    public void compareVersion4() {
        String version1 = "17.0.0";
        String version2 = "17";
        assertEquals(0, CompareVersionUtil.compareVersion(version1, version2));
    }

    @Test
    public void compareVersion5() {
        String version1 = "16.8";
        String version2 = "17.0";
        Assert.assertTrue(CompareVersionUtil.compareVersion(version1, version2) < 0);
    }

    @Test
    public void compareVersion6() {
        String version1 = "16";
        String version2 = "17";
        Assert.assertTrue(CompareVersionUtil.compareVersion(version1, version2) < 0);
    }

    @Test
    public void compareVersion7() {
        String version1 = "18.0.1";
        String version2 = "17.0";
        Assert.assertTrue(CompareVersionUtil.compareVersion(version1, version2) > 0);
    }
}