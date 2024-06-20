package org.cloud.sonic.agent.tests.handlers;

import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.tools.BytesTool;
import org.junit.Assert;
import org.junit.Test;

public class TextHandlerTest {
    @Test
    public void test() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("hello", "world");
        jsonObject.put("hello2", "world2");
        jsonObject.put("xy", "1,2");
        jsonObject.put("x", "0.3");
        jsonObject.put("y", "0.4");
        String s = TextHandler.replaceTrans("{{hello}}", jsonObject);
        Assert.assertEquals("world", s);
        s = TextHandler.replaceTrans("ss{{hello}}ss", jsonObject);
        Assert.assertEquals("ssworldss", s);
        s = TextHandler.replaceTrans("ss{{hello2}}ss{{hello}}", jsonObject);
        Assert.assertEquals("ssworld2ssworld", s);
        s = TextHandler.replaceTrans("{{random}}", jsonObject);
        Assert.assertTrue(BytesTool.isInt(s));
        s = TextHandler.replaceTrans("{{timestamp}}", jsonObject);
        Assert.assertTrue(BytesTool.isInt(s));
        Assert.assertEquals(13, s.length());
        s = TextHandler.replaceTrans("{{random[1]}}", jsonObject);
        Assert.assertTrue(Integer.parseInt(s) < 10);
        s = TextHandler.replaceTrans("{{random[2]}}", jsonObject);
        Assert.assertTrue(Integer.parseInt(s) < 100);
        s = TextHandler.replaceTrans("{{random[3]}}", jsonObject);
        Assert.assertTrue(Integer.parseInt(s) < 1000);
        s = TextHandler.replaceTrans("{{random[3-6]}}", jsonObject);
        Assert.assertTrue(Integer.parseInt(s) <= 6 && Integer.parseInt(s) >= 3);
        s = TextHandler.replaceTrans("{{random[h|2|3]}}", jsonObject);
        Assert.assertEquals(1, s.length());
        s = TextHandler.replaceTrans("{{x}},{{y}}", jsonObject);
        Assert.assertEquals("0.3,0.4", s);
        s = TextHandler.replaceTrans("{{xy}}", jsonObject);
        Assert.assertEquals("1,2", s);
    }
}
