package com.menteMaestra.backend.repository;

import com.menteMaestra.backend.model.Pregunta;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PreguntaRepository extends MongoRepository<Pregunta, String> {
}