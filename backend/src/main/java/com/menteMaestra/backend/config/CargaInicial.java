package com.menteMaestra.backend.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menteMaestra.backend.model.Pregunta;
import com.menteMaestra.backend.repository.PreguntaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class CargaInicial implements CommandLineRunner {

    private final PreguntaRepository repo;

    public CargaInicial(PreguntaRepository repo) {
        this.repo = repo;
    }

    @Override
    public void run(String... args) throws Exception {
        if (repo.count() == 0) {
            ObjectMapper mapper = new ObjectMapper();
            InputStream inputStream = new ClassPathResource("preguntas.json").getInputStream();
            List<Pregunta> preguntas = mapper.readValue(inputStream, new TypeReference<List<Pregunta>>() {});

            // Debug: Verificar cómo se están cargando los datos
            System.out.println("=== VERIFICACIÓN DE CARGA ===");
            preguntas.forEach(p -> {
                System.out.println("\nPregunta: " + p.getEnunciado());
                p.getOpciones().forEach(op -> {
                    System.out.println("Opción: " + op.getTexto() +
                            " | Correcta en JSON: " + op.isCorrecta());
                });
            });

            repo.saveAll(preguntas);
            System.out.println("Preguntas cargadas correctamente.");
        }
    }
}
