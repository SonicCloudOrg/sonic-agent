package org.cloud.sonic.agent.service.impl;

import org.apache.dubbo.config.annotation.DubboService;
import org.cloud.sonic.common.services.TestService;
import org.springframework.stereotype.Service;

/**
 * fixme 测试RPC用
 *
 * @author JayWenStar
 * @date 2022/4/7 11:24 下午
 */
@Service
@DubboService
public class TestServiceImpl implements TestService {

    @Override
    public String test() {
        return "success";
    }
}
