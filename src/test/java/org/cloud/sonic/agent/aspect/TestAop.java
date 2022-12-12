package org.cloud.sonic.agent.aspect;


import com.alibaba.fastjson.JSONObject;
import org.cloud.sonic.agent.common.models.HandleContext;
import org.springframework.stereotype.Component;

@Component
public class TestAop {
    @PocoIteratorCheck
    public void runStep(JSONObject stepJSON, HandleContext handleContext){
        System.out.println(stepJSON.toJSONString());
        assert handleContext.currentIteratorPocoElement!=null;
    }
}
