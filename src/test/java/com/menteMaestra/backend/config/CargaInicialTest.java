package com.menteMaestra.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menteMaestra.backend.model.Pregunta;
import com.menteMaestra.backend.repository.PreguntaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CargaInicialTest {

    private PreguntaRepository repo;
    private CargaInicial cargaInicial;

    @BeforeEach
    void setUp() {
        repo = mock(PreguntaRepository.class);
        cargaInicial = new CargaInicial(repo);
    }

    @Test
    void noCargaSiYaHayPreguntas() throws Exception {
        when(repo.count()).thenReturn(5L);

        cargaInicial.run();

        verify(repo, never()).saveAll(any());
    }

    @Test
    void cargaPreguntasDesdeJsonCuandoRepositorioEstaVacio() throws Exception {
        when(repo.count()).thenReturn(0L);

        // Se usa el mismo JSON real que usa la app
        ObjectMapper mapper = new ObjectMapper();
        InputStream inputStream = new ClassPathResource("preguntas.json").getInputStream();
        List<Pregunta> preguntasEsperadas = mapper.readValue(inputStream,
                mapper.getTypeFactory().constructCollectionType(List.class, Pregunta.class));

        cargaInicial.run();

        ArgumentCaptor<List<Pregunta>> captor = ArgumentCaptor.forClass(List.class);
        verify(repo, times(1)).saveAll(captor.capture());

        List<Pregunta> preguntasGuardadas = captor.getValue();
        assertEquals(preguntasEsperadas.size(), preguntasGuardadas.size());

        for (int i = 0; i < preguntasEsperadas.size(); i++) {
            Pregunta esperada = preguntasEsperadas.get(i);
            Pregunta guardada = preguntasGuardadas.get(i);
            assertEquals(esperada.getEnunciado(), guardada.getEnunciado());
            assertEquals(esperada.getOpciones().size(), guardada.getOpciones().size());
        }
    }

    @Test
    void debeCargarPreguntasSiRepositorioEstaVacio() throws Exception {
        when(repo.count()).thenReturn(0L);
        cargaInicial.run();
        ArgumentCaptor<List<Pregunta>> captor = ArgumentCaptor.forClass(List.class);
        verify(repo, times(1)).saveAll(captor.capture());

        List<Pregunta> preguntasGuardadas = captor.getValue();

        assertFalse(preguntasGuardadas.isEmpty(), "Debe haber al menos una pregunta en el JSON");

        Pregunta primera = preguntasGuardadas.get(0);
        assertNotNull(primera.getEnunciado(), "La pregunta debe tener un enunciado");
        assertNotNull(primera.getOpciones(), "La pregunta debe tener opciones");
        assertTrue(primera.getOpciones().size() >= 2, "Debe tener al menos dos opciones");
    }

    @Test
    void noDebeCargarPreguntasSiRepositorioNoEstaVacio() throws Exception {
        when(repo.count()).thenReturn(10L);
        cargaInicial.run();
        verify(repo, never()).saveAll(any());
    }
}
