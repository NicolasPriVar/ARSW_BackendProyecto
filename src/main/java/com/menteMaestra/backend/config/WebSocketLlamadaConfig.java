package com.menteMaestra.backend.config;

import com.menteMaestra.backend.webSocket.LlamadaHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketLlamadaConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new LlamadaHandler(), "/ws/llamada/{codigo}")
                .setAllowedOrigins("*");
    }
}
