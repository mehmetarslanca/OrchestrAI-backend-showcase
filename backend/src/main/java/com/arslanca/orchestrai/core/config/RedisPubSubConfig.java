package com.arslanca.orchestrai.core.config;

import com.arslanca.orchestrai.business.concretes.ChatCancelListener;
import com.arslanca.orchestrai.business.concretes.cancellation.ProjectGenerationCancelListener;
import com.arslanca.orchestrai.business.concretes.cancellation.ProjectGenerationSessionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisPubSubConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            ChatCancelListener chatCancelListener,
            ProjectGenerationCancelListener projectGenerationCancelListener) {
        
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(chatCancelListener, new ChannelTopic(com.arslanca.orchestrai.business.concretes.DistributedChatStreamRegistry.CANCEL_CHANNEL));
        container.addMessageListener(projectGenerationCancelListener, new ChannelTopic(ProjectGenerationSessionRegistry.CANCEL_CHANNEL));
        return container;
    }
}
