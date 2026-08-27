package com.smartlamp.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
public class MqttPublisherService {

    @Autowired
    @Qualifier("mqttOutboundChannel")
    private MessageChannel mqttOutboundChannel;

    // 发布消息；返回是否成功投递到发送通道（false 表示投递失败；连接类异常向上抛出）
    public boolean publish(String topic, String payload) {
        return mqttOutboundChannel.send(
                MessageBuilder.withPayload(payload)
                        .setHeader(MqttHeaders.TOPIC, topic)
                        .build()
        );
    }
}