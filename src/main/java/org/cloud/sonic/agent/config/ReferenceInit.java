/**
 * Copyright (C) [SonicCloudOrg] Sonic Project
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cloud.sonic.agent.config;

import org.apache.dubbo.config.annotation.DubboReference;
import org.cloud.sonic.agent.registry.zookeeper.AgentZookeeperRegistry;
import org.cloud.sonic.common.services.AgentsService;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

/**
 * 上层接口必须要使用{@link DubboReference}声明才能被Spring容器初始化
 * 但某些情况类无法使用{@link Component}、{@link Import}会导致原本的构造函数无法使用，跟Dubbo的IOC冲突
 * 为了让这部分类能顺利注入，就需要在此类先声明Dubbo引用
 * 例子：{@link AgentZookeeperRegistry}
 *
 * @author JayWenStar
 * @date 2022/4/8 5:54 下午
 */
@Component
public class ReferenceInit {

    @DubboReference
    private AgentsService agentsService;

}
