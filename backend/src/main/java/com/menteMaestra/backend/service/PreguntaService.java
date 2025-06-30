package com.menteMaestra.backend.service;

import com.menteMaestra.backend.model.Pregunta;
import com.menteMaestra.backend.model.Opcion;
import com.menteMaestra.backend.repository.PreguntaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PreguntaService {

    @Autowired
    private PreguntaRepository preguntaRepository;

    private List<Pregunta> cachePreguntas;

    public List<Pregunta> obtenerTodasOrdenadas() {
        if (cachePreguntas == null) {
            cachePreguntas = preguntaRepository.findAll();
            cachePreguntas.sort(Comparator.comparing(Pregunta::getEnunciado)); // o .getId() si es más confiable
        }
        return cachePreguntas;
    }

    public Pregunta obtenerPreguntaPorIndice(int index) {
        List<Pregunta> todas = obtenerTodasOrdenadas();
        if (index >= todas.size()) {
            return null;
        }

        // Clonar la pregunta para evitar modificar el orden original en el cache
        Pregunta original = todas.get(index);
        Pregunta copia = new Pregunta();
        copia.setEnunciado(original.getEnunciado());
        copia.setId(original.getId());

        // Clonar y mezclar las opciones
        List<Opcion> opcionesClonadas = new ArrayList<>();
        for (Opcion o : original.getOpciones()) {
            Opcion nueva = new Opcion();
            nueva.setTexto(o.getTexto());
            nueva.setCorrecta(o.isCorrecta());
            opcionesClonadas.add(nueva);
        }
        Collections.shuffle(opcionesClonadas);
        copia.setOpciones(opcionesClonadas);

        return copia;
    }

    public int totalPreguntas() {
        return obtenerTodasOrdenadas().size();
    }
}
