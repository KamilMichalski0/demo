package com.example.demo.config;

import com.tibco.tibjms.TibjmsConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;

@Configuration
public class EmsConfig {

    @Value("${ems.serverUrl}")
    private String serverUrl;

    @Value("${ems.username}")
    private String username;

    @Value("${ems.password}")
    private String password;

    @Bean
    public TibjmsConnectionFactory connectionFactory() {
        return new TibjmsConnectionFactory(serverUrl, username, password);
    }

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(TibjmsConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrency("1-5");
        return factory;
    }
}
