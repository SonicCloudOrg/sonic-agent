package com.sonic.agent.rabbitmq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class RabbitQueueConfig {
    private final Logger logger = LoggerFactory.getLogger(RabbitQueueConfig.class);
    private String queueId;
    @Value("${sonic.agent.key}")
    private String key;

    @Bean
    public String queueId() {
        queueId = UUID.randomUUID().toString();
        return queueId;
    }

    @Bean("MsgDirectExchange")
    public DirectExchange MsgDirectExchange() {
        return new DirectExchange("MsgDirectExchange", true, false);
    }

    @Bean("MsgQueue")
    public Queue MsgQueue() {
        return new Queue("MsgQueue-" + key, true);
    }

    @Bean
    public Binding bindingDirect(@Qualifier("MsgQueue") Queue queue,
                                 @Qualifier("MsgDirectExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(key);
    }

    @Bean("PackageExchange")
    public FanoutExchange PackageExchange() {
        return new FanoutExchange("PackageExchange", true, false);
    }

    @Bean("AgentExchange")
    public FanoutExchange AgentExchange() {
        return new FanoutExchange("AgentExchange", true, false);
    }

    @Bean("PackageQueue")
    public Queue PackageQueue() {
        return new Queue("PackageQueue-" + queueId, true, false, true);
    }

    @Bean("AgentTaskQueue")
    public Queue AgentTaskQueue() {
        return new Queue("AgentTaskQueue-" + queueId, true, false, true);
    }

    @Bean
    public Binding bindingAgentDirect(@Qualifier("AgentTaskQueue") Queue queue,
                                      @Qualifier("AgentExchange") FanoutExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange);
    }

    @Bean
    public Binding bindingPackageDirect(@Qualifier("PackageQueue") Queue queue,
                                        @Qualifier("PackageExchange") FanoutExchange exchange) {
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
