package com.menteMaestra.backend.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menteMaestra.backend.model.Pregunta;
import com.menteMaestra.backend.repository.PreguntaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;

/**
 * Clase encargada de cargar datos iniciales en la base de datos desde un archivo JSON.
 * Esta clase se ejecuta automáticamente al iniciar la aplicación.
 * Si el repositorio de preguntas está vacío, se cargan las preguntas definidas en el archivo preguntas.json
 *
 * @author Nicolás
 */
@Component
public class CargaInicial implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(CargaInicial.class);
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

            logger.info("=== VERIFICACIÓN DE CARGA ===");
            preguntas.forEach(p -> {
                logger.info("\nPregunta: {}", p.getEnunciado());
                p.getOpciones().forEach(op ->
                        logger.info("Opción: {} | Correcta en JSON: {}", op.getTexto(), op.isCorrecta())
                );
            });

            repo.saveAll(preguntas);
            logger.info("Preguntas cargadas correctamente.");
        }
    }
}
