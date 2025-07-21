package com.menteMaestra.backend.webSocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.stereotype.Component;
import com.menteMaestra.backend.exception.WebSocketBroadcastException;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manejador WebSocket para las sesiones de llamada en Mente Maestra.
 * Gestiona la unión de usuarios a una sala identificada por un código,
 * el enrutamiento de mensajes entre usuarios y la desconexión.
 */
@Component
public class LlamadaHandler extends TextWebSocketHandler {

    private final Map<String, Map<String, WebSocketSession>> salas = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, String> nombresPorSesion = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, String> codigosPorSesion = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Método implementado por requerimiento de la interfaz.
        // Actualmente no se necesita lógica en la apertura de conexión,
        // pero puede usarse para registrar o autenticar sesiones más adelante.
    }

    /**
     * Maneja los mensajes entrantes del tipo "join" o mensajes dirigidos a otro usuario.
     *
     * @param session sesión WebSocket que envió el mensaje
     * @param message contenido del mensaje
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        Map<String, Object> data = new ObjectMapper().readValue(payload, Map.class);

        String tipo = (String) data.get("type");
        String nombre = (String) data.get("from");
        URI uri = session.getUri();
        String codigo = (uri != null) ? extraerCodigoDesdeUri(uri.getPath()) : null;

        if (codigo == null || nombre == null) return;

        nombresPorSesion.put(session, nombre);
        codigosPorSesion.put(session, codigo);
        salas.computeIfAbsent(codigo, k -> new ConcurrentHashMap<>()).put(nombre, session);

        if ("join".equals(tipo)) {

            Map<String, Object> joinMsg = Map.of(
                    "type", "join",
                    "from", nombre
            );

            enviarATodosMenos(codigo, nombre, joinMsg);
        } else {

            String destino = (String) data.get("to");
            WebSocketSession sesionDestino = salas.getOrDefault(codigo, Map.of()).get(destino);

            if (sesionDestino != null && sesionDestino.isOpen()) {
                sesionDestino.sendMessage(new TextMessage(payload));
            }
        }
    }

    /**
     * Maneja la desconexión del usuario y limpieza de la sesión en las estructuras internas.
     *
     * @param session sesión desconectada
     * @param status  estado de la desconexión
     */
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

    /**
     * Envía un mensaje a todos los usuarios de una sala, excepto al remitente.
     *
     * @param codigo           código de la sala
     * @param nombreRemitente  usuario que originó el mensaje
     * @param mensaje          mensaje a enviar
     */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private void enviarATodosMenos(String codigo, String nombreRemitente, Map<String, Object> mensaje) {
        Map<String, WebSocketSession> sesiones = salas.getOrDefault(codigo, Map.of());

        for (Map.Entry<String, WebSocketSession> entry : sesiones.entrySet()) {
            if (!entry.getKey().equals(nombreRemitente)) {
                WebSocketSession sesion = entry.getValue();
                if (sesion.isOpen()) {
                    try {
                        String json = objectMapper.writeValueAsString(mensaje);
                        sesion.sendMessage(new TextMessage(json));
                    } catch (Exception e) {
                        throw new WebSocketBroadcastException("Error al enviar mensaje a " + entry.getKey() + " en la sala " + codigo, e);
                    }
                }
            }
        }
    }
    /**
     * Extrae el código de sala desde la URI del WebSocket.
     *
     * @param path ruta de la URI
     * @return el código de la sala o null si no es válido
     */
    private String extraerCodigoDesdeUri(String path) {
        if (path == null || !path.contains("/ws/llamada/")) return null;
        return path.substring(path.lastIndexOf("/") + 1);
    }
}
