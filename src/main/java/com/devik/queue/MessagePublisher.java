package com.devik.queue;

import com.devik.util.Constants;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MessagePublisher {

    final RabbitTemplate template;

    public MessagePublisher(RabbitTemplate template) {
        this.template = template;
    }

    public void publish(String message) {
        template.convertAndSend(Constants.exchangeName, Constants.queueName, message);
    }
}
