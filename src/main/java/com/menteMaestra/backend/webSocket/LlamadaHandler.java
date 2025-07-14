package com.menteMaestra.backend.webSocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LlamadaHandler extends TextWebSocketHandler {

    // Map<codigo, Map<nombreJugador, WebSocketSession>>
    private final Map<String, Map<String, WebSocketSession>> salas = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, String> nombresPorSesion = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, String> codigosPorSesion = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // No hacemos nada aún: esperamos a que envíe "join" con su nombre y código
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        Map<String, Object> data = new ObjectMapper().readValue(payload, Map.class);

        String tipo = (String) data.get("type");
        String nombre = (String) data.get("from");
        String codigo = extraerCodigoDesdeUri(session.getUri().getPath());

        if (codigo == null || nombre == null) return;

        nombresPorSesion.put(session, nombre);
        codigosPorSesion.put(session, codigo);
        salas.computeIfAbsent(codigo, k -> new ConcurrentHashMap<>()).put(nombre, session);

        if ("join".equals(tipo)) {
            // Notificar a los demás que alguien se unió
            Map<String, Object> joinMsg = Map.of(
                    "type", "join",
                    "from", nombre
            );

            enviarATodosMenos(codigo, nombre, joinMsg);
        } else {
            // Reenviar mensaje directamente al destinatario
            String destino = (String) data.get("to");
            WebSocketSession sesionDestino = salas.getOrDefault(codigo, Map.of()).get(destino);

            if (sesionDestino != null && sesionDestino.isOpen()) {
                sesionDestino.sendMessage(new TextMessage(payload));
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String nombre = nombresPorSesion.remove(session);
        String codigo = codigosPorSesion.remove(session);

        if (codigo != null && nombre != null) {
            Map<String, WebSocketSession> usuarios = salas.get(codigo);
            if (usuarios != null) {
                usuarios.remove(nombre);
                if (usuarios.isEmpty()) {
                    salas.remove(codigo);
                }
            }
        }
    }

    private void enviarATodosMenos(String codigo, String nombreRemitente, Map<String, Object> mensaje) throws Exception {
        for (Map.Entry<String, WebSocketSession> entry : salas.getOrDefault(codigo, Map.of()).entrySet()) {
            if (!entry.getKey().equals(nombreRemitente)) {
                WebSocketSession sesion = entry.getValue();
                if (sesion.isOpen()) {
                    sesion.sendMessage(new TextMessage(new ObjectMapper().writeValueAsString(mensaje)));
                }
            }
        }
    }

    private String extraerCodigoDesdeUri(String path) {
        if (path == null || !path.contains("/ws/llamada/")) return null;
        return path.substring(path.lastIndexOf("/") + 1);
    }
}
