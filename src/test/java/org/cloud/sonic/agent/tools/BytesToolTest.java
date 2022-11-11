package org.cloud.sonic.agent.tools;

import org.junit.Assert;
import org.junit.Test;

public class BytesToolTest {
    @Test
    public void testVersionCheck() {
        String tar = "2.3.5";
        Assert.assertFalse(BytesTool.versionCheck(tar, "2.1.0"));
        Assert.assertFalse(BytesTool.versionCheck(tar, "2.3.4"));
        Assert.assertFalse(BytesTool.versionCheck(tar, "1.4.4"));
        Assert.assertTrue(BytesTool.versionCheck(tar, "2.3.5"));
        Assert.assertTrue(BytesTool.versionCheck(tar, "2.4.2"));
        Assert.assertTrue(BytesTool.versionCheck(tar, "3.1.1"));
    }
}
