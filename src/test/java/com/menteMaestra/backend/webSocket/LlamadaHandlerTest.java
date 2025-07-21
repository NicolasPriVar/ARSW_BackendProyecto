package com.menteMaestra.backend.webSocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menteMaestra.backend.exception.WebSocketBroadcastException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class LlamadaHandlerTest {

    private LlamadaHandler handler;
    private WebSocketSession sessionMock;
    private LlamadaHandler llamadaHandler;
    private WebSocketSession session1;
    private WebSocketSession session2;

    @BeforeEach
    void setUp() {
        handler = new LlamadaHandler();
        sessionMock = mock(WebSocketSession.class);
        llamadaHandler = new LlamadaHandler();
        session1 = mock(WebSocketSession.class);
        session2 = mock(WebSocketSession.class);

        // Insertamos sesiones en la sala ficticia
        Map<String, WebSocketSession> sala = new HashMap<>();
        sala.put("Juan", session1);
        sala.put("Pedro", session2);

        Map<String, Map<String, WebSocketSession>> salas = new HashMap<>();
        salas.put("AB1234", sala);

        // Inyectamos salas en el handler (usando reflexión si es necesario)
        try {
            var field = LlamadaHandler.class.getDeclaredField("salas");
            field.setAccessible(true);
            field.set(llamadaHandler, salas);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testHandleTextMessageJoin() throws Exception {
        String codigo = "123ABC";
        String nombre = "Nico";

        when(sessionMock.getUri()).thenReturn(new URI("/ws/llamada/" + codigo));
        when(sessionMock.isOpen()).thenReturn(true);

        Map<String, Object> mensaje = Map.of(
                "type", "join",
                "from", nombre
        );

        String payload = new ObjectMapper().writeValueAsString(mensaje);
        TextMessage textMessage = new TextMessage(payload);

        handler.handleTextMessage(sessionMock, textMessage);

        verify(sessionMock, never()).sendMessage(any());
    }

    @Test
    void testHandleTextMessageToAnotherUser() throws Exception {
        String codigo = "456DEF";
        String emisor = "Ana";
        String receptor = "Luis";

        WebSocketSession receptorSession = mock(WebSocketSession.class);
        when(sessionMock.getUri()).thenReturn(new URI("/ws/llamada/" + codigo));
        when(receptorSession.getUri()).thenReturn(new URI("/ws/llamada/" + codigo));
        when(sessionMock.isOpen()).thenReturn(true);
        when(receptorSession.isOpen()).thenReturn(true);

        handler.handleTextMessage(receptorSession, new TextMessage(new ObjectMapper().writeValueAsString(
                Map.of("type", "join", "from", receptor)
        )));

        Map<String, Object> mensaje = Map.of(
                "type", "offer",
                "from", emisor,
                "to", receptor,
                "sdp", "fake_sdp_data"
        );

        when(sessionMock.getUri()).thenReturn(new URI("/ws/llamada/" + codigo));
        TextMessage textMessage = new TextMessage(new ObjectMapper().writeValueAsString(mensaje));
        handler.handleTextMessage(sessionMock, textMessage);

        verify(receptorSession, times(1)).sendMessage(any(TextMessage.class));
    }

    @Test
    void testAfterConnectionClosedRemovesSession() throws Exception {
        String codigo = "CERRAR123";
        String usuario = "Juan";

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session1");
        when(session.getUri()).thenReturn(new URI("/ws/llamada/" + codigo));

        java.lang.reflect.Field field = LlamadaHandler.class.getDeclaredField("salas");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Map<String, WebSocketSession>> salas = (Map<String, Map<String, WebSocketSession>>) field.get(handler);

        Map<String, WebSocketSession> mapaUsuarios = new ConcurrentHashMap<>();
        mapaUsuarios.put(usuario, session);
        salas.put(codigo, mapaUsuarios);

        java.lang.reflect.Field nombresField = LlamadaHandler.class.getDeclaredField("nombresPorSesion");
        java.lang.reflect.Field codigosField = LlamadaHandler.class.getDeclaredField("codigosPorSesion");
        nombresField.setAccessible(true);
        codigosField.setAccessible(true);
        ((Map<WebSocketSession, String>) nombresField.get(handler)).put(session, usuario);
        ((Map<WebSocketSession, String>) codigosField.get(handler)).put(session, codigo);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        Map<String, WebSocketSession> usuariosRestantes = salas.get(codigo);
        if (usuariosRestantes != null) {
            assertFalse(usuariosRestantes.containsKey(usuario));
        } else {
            assertTrue(true);
        }
    }
    @Test
    void testExtraerCodigoDesdeUriInvalida() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getUri()).thenReturn(new URI("/sin/llamada"));

        TextMessage mensaje = new TextMessage(new ObjectMapper().writeValueAsString(Map.of(
                "type", "join",
                "from", "Maria"
        )));

        handler.handleTextMessage(session, mensaje);
        verify(session, never()).sendMessage(any());
    }

    @Test
    void testHandleTextMessageDestinoNoExiste() throws Exception {
        String codigo = "NOUSER";
        String emisor = "Carlos";

        when(sessionMock.getUri()).thenReturn(new URI("/ws/llamada/" + codigo));
        when(sessionMock.isOpen()).thenReturn(true);

        TextMessage mensaje = new TextMessage(new ObjectMapper().writeValueAsString(Map.of(
                "type", "offer",
                "from", emisor,
                "to", "Fantasma",
                "sdp", "datos_sdp"
        )));

        handler.handleTextMessage(sessionMock, mensaje);
        verify(sessionMock, never()).sendMessage(any());
    }
    @Test
    void testEnviarATodosMenosConSesionCerrada() throws Exception {
        String codigo = "ROOM999";
        String usuario1 = "A";
        String usuario2 = "B";

        WebSocketSession sesion1 = mock(WebSocketSession.class);
        WebSocketSession sesion2 = mock(WebSocketSession.class);

        when(sesion1.isOpen()).thenReturn(true);
        when(sesion2.isOpen()).thenReturn(false);

        java.lang.reflect.Field field = LlamadaHandler.class.getDeclaredField("salas");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Map<String, WebSocketSession>> salas = (Map<String, Map<String, WebSocketSession>>) field.get(handler);

        Map<String, WebSocketSession> usuarios = new ConcurrentHashMap<>();
        usuarios.put(usuario1, sesion1);
        usuarios.put(usuario2, sesion2);
        salas.put(codigo, usuarios);

        java.lang.reflect.Method metodo = LlamadaHandler.class.getDeclaredMethod("enviarATodosMenos", String.class, String.class, Map.class);
        metodo.setAccessible(true);
        metodo.invoke(handler, codigo, usuario1, Map.of("type", "test"));

        verify(sesion2, never()).sendMessage(any());
    }

    @Test
    void testAfterConnectionClosedConNombreYCodigoNulos() {
        WebSocketSession session = mock(WebSocketSession.class);
        assertDoesNotThrow(() -> handler.afterConnectionClosed(session, CloseStatus.NORMAL)); // ✅ ASSERT
    }

    @Test
    void testAfterConnectionClosedEliminaSalaVacia() throws Exception {
        String codigo = "EMPTYROOM";
        String usuario = "Solo";

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getUri()).thenReturn(new URI("/ws/llamada/" + codigo));

        java.lang.reflect.Field field = LlamadaHandler.class.getDeclaredField("salas");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Map<String, WebSocketSession>> salas = (Map<String, Map<String, WebSocketSession>>) field.get(handler);

        Map<String, WebSocketSession> usuarios = new ConcurrentHashMap<>();
        usuarios.put(usuario, session);
        salas.put(codigo, usuarios);

        java.lang.reflect.Field nombresField = LlamadaHandler.class.getDeclaredField("nombresPorSesion");
        java.lang.reflect.Field codigosField = LlamadaHandler.class.getDeclaredField("codigosPorSesion");
        nombresField.setAccessible(true);
        codigosField.setAccessible(true);
        Map<WebSocketSession, String> nombres = (Map<WebSocketSession, String>) nombresField.get(handler);
        Map<WebSocketSession, String> codigos = (Map<WebSocketSession, String>) codigosField.get(handler);

        nombres.put(session, usuario);
        codigos.put(session, codigo);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertFalse(salas.containsKey(codigo));
    }

    @Test
    void testEnviarATodosMenosEnvioExitoso() throws Exception {
        String codigo = "ROOM123";
        String emisor = "Alice";
        String receptor = "Bob";

        WebSocketSession sesionEmisor = mock(WebSocketSession.class);
        WebSocketSession sesionReceptor = mock(WebSocketSession.class);

        when(sesionReceptor.isOpen()).thenReturn(true);

        java.lang.reflect.Field field = LlamadaHandler.class.getDeclaredField("salas");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Map<String, WebSocketSession>> salas = (Map<String, Map<String, WebSocketSession>>) field.get(handler);

        Map<String, WebSocketSession> usuarios = new ConcurrentHashMap<>();
        usuarios.put(emisor, sesionEmisor);
        usuarios.put(receptor, sesionReceptor);
        salas.put(codigo, usuarios);

        Map<String, Object> mensaje = Map.of("type", "mensaje", "contenido", "Hola!");

        java.lang.reflect.Method metodo = LlamadaHandler.class.getDeclaredMethod("enviarATodosMenos", String.class, String.class, Map.class);
        metodo.setAccessible(true);
        metodo.invoke(handler, codigo, emisor, mensaje);

        verify(sesionReceptor, times(1)).sendMessage(any(TextMessage.class));
        verify(sesionEmisor, never()).sendMessage(any());
    }

    @Test
    void testExtraerCodigoDesdeUriValida() throws Exception {
        String path = "/ws/llamada/ABC123";

        java.lang.reflect.Method metodo = LlamadaHandler.class.getDeclaredMethod("extraerCodigoDesdeUri", String.class);
        metodo.setAccessible(true);

        String resultado = (String) metodo.invoke(handler, path);
        assertEquals("ABC123", resultado);
    }

    @Test
    void testExtraerCodigoDesdeUriInvalidaPorPathNulo() throws Exception {
        java.lang.reflect.Method metodo = LlamadaHandler.class.getDeclaredMethod("extraerCodigoDesdeUri", String.class);
        metodo.setAccessible(true);

        String resultado = (String) metodo.invoke(handler, (String) null);
        assertNull(resultado);
    }

    @Test
    void testExtraerCodigoDesdeUriInvalidaPorFormato() throws Exception {
        String path = "/otra/ruta/sin/codigo";

        java.lang.reflect.Method metodo = LlamadaHandler.class.getDeclaredMethod("extraerCodigoDesdeUri", String.class);
        metodo.setAccessible(true);

        String resultado = (String) metodo.invoke(handler, path);
        assertNull(resultado);
    }

    @Test
    void testHandleTextMessageNombreNulo() throws Exception {
        when(sessionMock.getUri()).thenReturn(new URI("/ws/llamada/ROOM1"));
        when(sessionMock.isOpen()).thenReturn(true);

        // Importante: omitimos el campo "from" para que sea null al hacer get("from")
        TextMessage mensaje = new TextMessage(new ObjectMapper().writeValueAsString(Map.of(
                "type", "join"
                // no se incluye "from"
        )));

        handler.handleTextMessage(sessionMock, mensaje);

        // No debería lanzar excepción ni enviar mensajes
        verify(sessionMock, never()).sendMessage(any());
    }

    @Test
    void testHandleTextMessageCodigoNulo() throws Exception {
        when(sessionMock.getUri()).thenReturn(new URI("/ruta/invalida")); // no contiene /ws/llamada/
        when(sessionMock.isOpen()).thenReturn(true);

        TextMessage mensaje = new TextMessage(new ObjectMapper().writeValueAsString(Map.of(
                "type", "join",
                "from", "Carlos"
        )));

        handler.handleTextMessage(sessionMock, mensaje);

        verify(sessionMock, never()).sendMessage(any());
    }

    @Test
    void testHandleTextMessageSesionDestinoCerrada() throws Exception {
        String codigo = "ROOM2";
        String emisor = "Pedro";
        String receptor = "Luis";

        WebSocketSession sesionReceptor = mock(WebSocketSession.class);

        // Receptor se une primero
        when(sesionReceptor.getUri()).thenReturn(new URI("/ws/llamada/" + codigo));
        when(sesionReceptor.isOpen()).thenReturn(false); // simulamos sesión cerrada
        handler.handleTextMessage(sesionReceptor, new TextMessage(new ObjectMapper().writeValueAsString(
                Map.of("type", "join", "from", receptor)
        )));

        // Luego el emisor intenta enviarle un mensaje
        when(sessionMock.getUri()).thenReturn(new URI("/ws/llamada/" + codigo));
        when(sessionMock.isOpen()).thenReturn(true);

        TextMessage mensaje = new TextMessage(new ObjectMapper().writeValueAsString(Map.of(
                "type", "offer",
                "from", emisor,
                "to", receptor,
                "sdp", "datos_sdp"
        )));

        handler.handleTextMessage(sessionMock, mensaje);

        verify(sesionReceptor, never()).sendMessage(any());
    }

    @Test
    void testAfterConnectionClosedUsuariosNull() throws Exception {
        String codigo = "NO_USERS";
        String usuario = "Fantasma";

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getUri()).thenReturn(new URI("/ws/llamada/" + codigo));

        // Solo llenamos nombres y códigos, pero no salas
        java.lang.reflect.Field nombresField = LlamadaHandler.class.getDeclaredField("nombresPorSesion");
        java.lang.reflect.Field codigosField = LlamadaHandler.class.getDeclaredField("codigosPorSesion");
        nombresField.setAccessible(true);
        codigosField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<WebSocketSession, String> nombres = (Map<WebSocketSession, String>) nombresField.get(handler);
        Map<WebSocketSession, String> codigos = (Map<WebSocketSession, String>) codigosField.get(handler);

        nombres.put(session, usuario);
        codigos.put(session, codigo);

        assertDoesNotThrow(() -> handler.afterConnectionClosed(session, CloseStatus.NORMAL));
    }
    @Test
    void testAfterConnectionClosedSalaNoVacia() throws Exception {
        String codigo = "SALA_LLENA";
        String usuario1 = "Ana";
        String usuario2 = "Beto";

        WebSocketSession sesion1 = mock(WebSocketSession.class);
        WebSocketSession sesion2 = mock(WebSocketSession.class);

        when(sesion1.getUri()).thenReturn(new URI("/ws/llamada/" + codigo));

        java.lang.reflect.Field fieldSalas = LlamadaHandler.class.getDeclaredField("salas");
        java.lang.reflect.Field nombresField = LlamadaHandler.class.getDeclaredField("nombresPorSesion");
        java.lang.reflect.Field codigosField = LlamadaHandler.class.getDeclaredField("codigosPorSesion");
        fieldSalas.setAccessible(true);
        nombresField.setAccessible(true);
        codigosField.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Map<String, WebSocketSession>> salas = (Map<String, Map<String, WebSocketSession>>) fieldSalas.get(handler);
        Map<WebSocketSession, String> nombres = (Map<WebSocketSession, String>) nombresField.get(handler);
        Map<WebSocketSession, String> codigos = (Map<WebSocketSession, String>) codigosField.get(handler);

        Map<String, WebSocketSession> mapa = new ConcurrentHashMap<>();
        mapa.put(usuario1, sesion1);
        mapa.put(usuario2, sesion2);
        salas.put(codigo, mapa);
        nombres.put(sesion1, usuario1);
        codigos.put(sesion1, codigo);

        handler.afterConnectionClosed(sesion1, CloseStatus.NORMAL);

        assertTrue(salas.containsKey(codigo));
        assertFalse(salas.get(codigo).containsKey(usuario1));
        assertTrue(salas.get(codigo).containsKey(usuario2));
    }
    @Test
    void enviarATodosMenos_enviaMensajeCorrectamente() throws Exception {
        when(session1.isOpen()).thenReturn(true);
        when(session2.isOpen()).thenReturn(true);

        Map<String, Object> mensaje = Map.of("type", "LLAMADA", "from", "Pedro");

        // Usamos reflexión para invocar el método privado
        var metodo = LlamadaHandler.class.getDeclaredMethod(
                "enviarATodosMenos", String.class, String.class, Map.class
        );
        metodo.setAccessible(true);
        metodo.invoke(llamadaHandler, "AB1234", "Pedro", mensaje);

        verify(session1, times(1)).sendMessage(any(TextMessage.class));
        verify(session2, never()).sendMessage(any());
    }

    @Test
    void enviarATodosMenos_lanzaExcepcionSiFallaElEnvio() throws Exception {
        when(session1.isOpen()).thenReturn(true);
        doThrow(new RuntimeException("Falla")).when(session1).sendMessage(any());

        Map<String, Object> mensaje = Map.of("type", "LLAMADA", "from", "Pedro");

        var metodo = LlamadaHandler.class.getDeclaredMethod(
                "enviarATodosMenos", String.class, String.class, Map.class
        );
        metodo.setAccessible(true);

        Exception exception = assertThrows(Exception.class, () ->
                metodo.invoke(llamadaHandler, "AB1234", "Pedro", mensaje)
        );

        // Verificamos que la causa sea nuestra excepción personalizada
        assertTrue(exception.getCause() instanceof WebSocketBroadcastException);
        assertTrue(exception.getCause().getMessage().contains("Error al enviar mensaje"));
    }


}
