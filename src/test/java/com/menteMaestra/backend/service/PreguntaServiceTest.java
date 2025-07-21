package com.menteMaestra.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menteMaestra.backend.model.Pregunta;
import com.menteMaestra.backend.repository.PreguntaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PreguntaServiceTest {

    private PreguntaRepository preguntaRepository;
    private PreguntaService preguntaService;
    private List<Pregunta> preguntasDesdeJson;

    @BeforeEach
    void setUp() throws Exception {
        preguntaRepository = mock(PreguntaRepository.class);
        preguntaService = new PreguntaService(preguntaRepository);

        ObjectMapper mapper = new ObjectMapper();
        InputStream inputStream = new ClassPathResource("preguntas.json").getInputStream();
        preguntasDesdeJson = mapper.readValue(inputStream, new TypeReference<>() {});

        when(preguntaRepository.findAll()).thenReturn(preguntasDesdeJson);
    }

    @Test
    void obtenerTodasOrdenadasDevuelveListaOrdenada() {
        List<Pregunta> preguntas = preguntaService.obtenerTodasOrdenadas();
        assertEquals(preguntasDesdeJson.size(), preguntas.size());
        assertEquals(preguntasDesdeJson.get(0).getEnunciado(), preguntas.get(0).getEnunciado());
    }

    @Test
    void obtenerPreguntasMezcladasDevuelveListaAleatoria() {
        List<Pregunta> mezcladas = preguntaService.obtenerPreguntasMezcladas();
        assertEquals(preguntasDesdeJson.size(), mezcladas.size());
        assertNotSame(mezcladas, preguntaService.obtenerTodasOrdenadas());
    }

    @Test
    void obtenerPreguntaPorIndiceDevuelvePreguntaConOpcionesMezcladas() {
        Pregunta clonada = preguntaService.obtenerPreguntaPorIndice(0);
        assertNotNull(clonada);
        assertEquals(preguntasDesdeJson.get(0).getEnunciado(), clonada.getEnunciado());
        assertEquals(preguntasDesdeJson.get(0).getOpciones().size(), clonada.getOpciones().size());
        assertNotSame(preguntasDesdeJson.get(0).getOpciones(), clonada.getOpciones());
    }

    @Test
    void obtenerPreguntaPorIndiceInvalidoDevuelveNull() {
        int fueraDeRango = preguntasDesdeJson.size() + 10;
        Pregunta resultado = preguntaService.obtenerPreguntaPorIndice(fueraDeRango);
        assertNull(resultado);
    }

    @Test
    void totalPreguntasDevuelveElNumeroCorrecto() {
        int total = preguntaService.totalPreguntas();
        assertEquals(preguntasDesdeJson.size(), total);
    }
}
