package com.menteMaestra.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menteMaestra.backend.MenteMaestraApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = MenteMaestraApplication.class)
@AutoConfigureMockMvc
class CodigoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;
    private void iniciarPartida(String... nombresJugadores) throws Exception {
        for (String nombre : nombresJugadores) {
            Map<String, String> jugador = Map.of(
                    "codigo", codigo,
                    "nombre", nombre
            );
            mockMvc.perform(post("/api/codigo/ingresar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(jugador)))
                    .andExpect(status().isOk());
        }

        Map<String, String> admin = Map.of(
                "codigo", codigo,
                "nombre", "Admin",
                "rol", "admin",
                "cantidadPreguntas", "1"
        );

        mockMvc.perform(post("/api/codigo/ingresar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/codigo/iniciar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(admin)))
                .andExpect(status().isOk());
    }

    private String codigo;

    @BeforeEach
    void setUp() throws Exception {
        codigo = generarCodigoPartida();
    }

    @Test
    void generarCodigo_deberiaRetornarOkYCodigo() throws Exception {
        mockMvc.perform(post("/api/codigo/generar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").exists());
    }

    @Test
    void ingresarPartida_deberiaAceptarJugadorNuevo() throws Exception {
        Map<String, String> body = Map.of("codigo", codigo, "nombre", "Ana");
        mockMvc.perform(post("/api/codigo/ingresar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Bienvenido"));
    }

    @Test
    void ingresarPartida_nombreDuplicado_deberiaFallar() throws Exception {
        Map<String, String> body = Map.of("codigo", codigo, "nombre", "Ana");
        mockMvc.perform(post("/api/codigo/ingresar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/codigo/ingresar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Ese nombre ya está en uso en esta partida"));
    }

    private String generarCodigoPartida() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/codigo/generar"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        Map<?, ?> response = objectMapper.readValue(body, Map.class);
        return response.get("codigo").toString();
    }
    @Test
    void obtenerJugadores_deberiaRetornarLista() throws Exception {
        Map<String, String> body = Map.of("codigo", codigo, "nombre", "Ana");
        mockMvc.perform(post("/api/codigo/ingresar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/codigo/jugadores/" + codigo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Ana").value("jugador"));
    }

    @Test
    void estadoPartida_deberiaRetornarNoIniciada() throws Exception {
        mockMvc.perform(get("/api/codigo/estado/" + codigo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iniciada").value(false));
    }

    @Test
    void iniciarPartida_deberiaIniciarYRetornarOk() throws Exception {

        Map<String, String> admin = Map.of("codigo", codigo, "nombre", "Admin", "rol", "admin", "cantidadPreguntas", "1");
        mockMvc.perform(post("/api/codigo/ingresar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/codigo/iniciar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Partida iniciada"));
    }

    @Test
    void obtenerPreguntaActual_deberiaRetornarPregunta() throws Exception {
        iniciarPartida();

        mockMvc.perform(get("/api/codigo/pregunta/" + codigo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enunciado").exists())
                .andExpect(jsonPath("$.opciones").isArray());
    }

    @Test
    void salirDePartida_deberiaEliminarJugador() throws Exception {
        Map<String, String> body = Map.of("codigo", codigo, "nombre", "Ana");
        mockMvc.perform(post("/api/codigo/ingresar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/codigo/salir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }
    @Test
    void registrarRespuesta_deberiaAceptarRespuestaCorrectaYSumarPuntos() throws Exception {
        iniciarPartida("Ana");

        // Obtener pregunta actual para conocer opciones
        MvcResult result = mockMvc.perform(get("/api/codigo/pregunta/" + codigo))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        Map<String, Object> pregunta = objectMapper.readValue(body, Map.class);
        List<Map<String, Object>> opciones = (List<Map<String, Object>>) pregunta.get("opciones");

        String respuestaCorrecta = opciones.stream()
                .filter(op -> Boolean.TRUE.equals(op.get("correcta")))
                .findFirst()
                .map(op -> op.get("texto").toString())
                .orElseThrow();

        Map<String, String> respuesta = Map.of(
                "codigo", codigo,
                "nombre", "Ana",
                "respuesta", respuestaCorrecta
        );

        mockMvc.perform(post("/api/codigo/respuesta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(respuesta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correcta").value(true))
                .andExpect(jsonPath("$.puntaje").value(5));
    }

    @Test
    void registrarRespuesta_incorrecta_deberiaRetornarFalsoSinPuntos() throws Exception {
        iniciarPartida("Ana");

        Map<String, String> respuesta = Map.of(
                "codigo", codigo,
                "nombre", "Ana",
                "respuesta", "Respuesta que no existe"
        );

        mockMvc.perform(post("/api/codigo/respuesta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(respuesta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correcta").value(false))
                .andExpect(jsonPath("$.puntaje").value(0));
    }

    @Test
    void registrarRespuesta_duplicada_deberiaFallar() throws Exception {
        iniciarPartida("Ana");

        Map<String, String> respuesta = Map.of(
                "codigo", codigo,
                "nombre", "Ana",
                "respuesta", "cualquiera"
        );

        // Primer intento (válido)
        mockMvc.perform(post("/api/codigo/respuesta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(respuesta)))
                .andExpect(status().isOk());

        // Segundo intento (debe fallar)
        mockMvc.perform(post("/api/codigo/respuesta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(respuesta)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Ya respondiste esta pregunta"));
    }

    @Test
    void obtenerPreguntaActual_partidaNoIniciada_deberiaRetornarError() throws Exception {
        mockMvc.perform(get("/api/codigo/pregunta/" + codigo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("La partida no ha comenzado"));
    }

    @Test
    void ingresarPartida_codigoInvalido_deberiaRetornarNotFound() throws Exception {
        Map<String, String> body = Map.of("codigo", "999999", "nombre", "Ana");

        mockMvc.perform(post("/api/codigo/ingresar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Código no encontrado"));
    }

    @Test
    void ingresarPartida_yaIniciada_deberiaRetornarForbidden() throws Exception {
        iniciarPartida("Ana");

        Map<String, String> nuevo = Map.of("codigo", codigo, "nombre", "Laura");

        mockMvc.perform(post("/api/codigo/ingresar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nuevo)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("La partida ya ha sido iniciada"));
    }

    @Test
    void ingresarPartida_datosInvalidos_deberiaRetornarBadRequest() throws Exception {
        Map<String, String> body = Map.of("codigo", "123", "nombre", "");

        mockMvc.perform(post("/api/codigo/ingresar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Datos inválidos"));
    }

    @Test
    void ingresarPartida_masDeCincoJugadores_deberiaFallar() throws Exception {
        for (int i = 0; i < 5; i++) {
            Map<String, String> jugador = Map.of("codigo", codigo, "nombre", "J" + i);
            mockMvc.perform(post("/api/codigo/ingresar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(jugador)))
                    .andExpect(status().isOk());
        }

        Map<String, String> jugadorExtra = Map.of("codigo", codigo, "nombre", "Jugador6");

        mockMvc.perform(post("/api/codigo/ingresar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(jugadorExtra)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Ooops, ya hay muchos jugadores en esta partida"));
    }

    @Test
    void ingresarPartida_adminCantidadInvalida_deberiaFallar() throws Exception {
        Map<String, String> admin = Map.of(
                "codigo", codigo,
                "nombre", "Admin",
                "rol", "admin",
                "cantidadPreguntas", "abc"
        );

        mockMvc.perform(post("/api/codigo/ingresar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cantidad de preguntas inválida"));
    }

    @Test
    void obtenerPuntajes_deberiaRetornarPuntajeJugador() throws Exception {
        iniciarPartida("Ana");

        Map<String, String> respuesta = Map.of(
                "codigo", codigo,
                "nombre", "Ana",
                "respuesta", "Respuesta equivocada"
        );

        mockMvc.perform(post("/api/codigo/respuesta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(respuesta)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/codigo/puntajes/" + codigo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Ana").value(0));
    }
    @Test
    void iniciarPartida_sinSerAdmin_deberiaRetornarForbidden() throws Exception {
        Map<String, String> jugador = Map.of("codigo", codigo, "nombre", "Ana");

        mockMvc.perform(post("/api/codigo/ingresar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(jugador)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/codigo/iniciar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(jugador)))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Solo el administrador puede iniciar la partida"));
    }

    @Test
    void iniciarPartida_codigoInvalido_deberiaRetornarNotFound() throws Exception {
        Map<String, String> admin = Map.of("codigo", "000000", "nombre", "Admin");

        mockMvc.perform(post("/api/codigo/iniciar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(admin)))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Código no encontrado"));
    }
}
