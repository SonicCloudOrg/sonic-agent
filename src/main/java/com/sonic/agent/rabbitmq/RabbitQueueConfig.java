package com.sonic.agent.rabbitmq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class RabbitQueueConfig {
    private final Logger logger = LoggerFactory.getLogger(RabbitQueueConfig.class);
    private String queueId;
    @Bean
    public String queueId() {
        queueId = UUID.randomUUID().toString();
        return queueId;
    }

    @Bean("AgentExchange")
    public FanoutExchange createAgentExchange() {
        return new FanoutExchange("AgentExchange", true, false);
    }

    @Bean("AgentQueue")
    public Queue createAgentQueue() {
        return new Queue("AgentQueue-" +queueId, true, false, true);
    }

    @Bean
    public Binding bindingDirect(@Qualifier("AgentQueue") Queue queue,
                                       @Qualifier("AgentExchange") FanoutExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate();
        rabbitTemplate.setConnectionFactory(connectionFactory);
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                logger.error("ConfirmCallback: 相关数据：" + correlationData + "\n" +
                        "确认情况：" + false + "\n" +
                        "原因：" + cause);
            }
        });

        rabbitTemplate.setReturnCallback((message, replyCode, replyText, exchange, routingKey) ->
                logger.info("ConfirmCallback: 消息：" + message + "\n" +
                        "回应码：" + replyCode + "\n" +
                        "回应信息：" + replyText + "\n" +
                        "交换机：" + exchange + "\n" +
                        "路由键：" + routingKey));

        return rabbitTemplate;
    }

}
