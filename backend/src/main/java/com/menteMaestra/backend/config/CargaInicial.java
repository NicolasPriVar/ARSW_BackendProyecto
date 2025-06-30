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
            repo.saveAll(preguntas);
            System.out.println("Preguntas cargadas correctamente.");
        }
    }
}
