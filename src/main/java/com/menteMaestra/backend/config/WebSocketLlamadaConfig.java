package com.menteMaestra.backend.config;

import com.menteMaestra.backend.webSocket.LlamadaHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketLlamadaConfig implements WebSocketConfigurer {

    private final LlamadaHandler llamadaHandler;

    public WebSocketLlamadaConfig(LlamadaHandler llamadaHandler) {
        this.llamadaHandler = llamadaHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(llamadaHandler, "/ws/llamada/{codigo}")
                .setAllowedOrigins("https://gentle-ground-02c72f30f.1.azurestaticapps.net");
    }
}
