package org.cloud.sonic.agent.aspect;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.cloud.sonic.agent.common.models.HandleContext;
import org.springframework.context.annotation.Configuration;

@Aspect
@Configuration
public class PocoIteratorAspect {
    @Pointcut("@annotation(org.cloud.sonic.agent.aspect.PocoIteratorCheck)")
    public void serviceAspect() {
    }

    @Around(value = "serviceAspect()")
    public Object around(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        Object[] objects = processInputArg(proceedingJoinPoint.getArgs());
        try {
            return proceedingJoinPoint.proceed(objects);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return null;
    }

    private Object[] processInputArg(Object[] args) {
        HandleContext handleContext = null;
        for (Object arg : args) {
            if (arg instanceof HandleContext) {
                handleContext = (HandleContext) arg;
                break;
            }
        }
        JSONObject paramStep = null;
        for (Object arg : args) {
            if (arg instanceof JSONObject) {
                paramStep = (JSONObject) arg;
                break;
            }
        }

        if (paramStep!=null&&handleContext!=null&&handleContext.currentIteratorPocoElement!=null){

            String cssPath = handleContext.currentIteratorPocoElement.currentNodeSelector;

            JSONObject step = paramStep.getJSONObject("step");
            JSONArray eleList = step.getJSONArray("elements");

            for (int i=0;i<eleList.size();i++){
                JSONObject ele = eleList.getJSONObject(i);
                if ("pocoIterator".equals(ele.get("eleType").toString())){
                    ele.put("eleValue",cssPath);
                }
                eleList.set(i,new JSONObject(ele));
            }
        }
        return args;
    }
}
