package com.menteMaestra.backend.service;

import com.menteMaestra.backend.model.Pregunta;
import com.menteMaestra.backend.model.Opcion;
import com.menteMaestra.backend.repository.PreguntaRepository;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Servicio que gestiona la lógica relacionada con las preguntas del juego.
 * Provee funcionalidades como obtener preguntas ordenadas, mezcladas,
 * acceder por índice y mezclar opciones de respuesta.
 */
@Service
public class PreguntaService {

    private final PreguntaRepository preguntaRepository;
    private List<Pregunta> cachePreguntas;

    public PreguntaService(PreguntaRepository preguntaRepository) {
        this.preguntaRepository = preguntaRepository;
    }

    /**
     * Obtiene todas las preguntas ordenadas (por enunciado o id).
     * Se utiliza una caché para evitar múltiples accesos a la base de datos.
     *
     * @return lista de preguntas ordenadas
     */
    public List<Pregunta> obtenerTodasOrdenadas() {
        if (cachePreguntas == null) {
            cachePreguntas = preguntaRepository.findAll();
            cachePreguntas.sort(Comparator.comparing(Pregunta::getEnunciado));
        }
        return cachePreguntas;
    }

    /**
     * Devuelve una lista de preguntas mezcladas aleatoriamente.
     *
     * @return lista de preguntas en orden aleatorio
     */
    public List<Pregunta> obtenerPreguntasMezcladas() {
        List<Pregunta> copia = new ArrayList<>(obtenerTodasOrdenadas());
        Collections.shuffle(copia);
        return copia;
    }


    /**
     * Devuelve una pregunta específica por su índice,
     * clonando la pregunta y mezclando aleatoriamente sus opciones.
     *
     * @param index índice de la pregunta
     * @return pregunta clonada y mezclada, o null si el índice es inválido
     */
    public Pregunta obtenerPreguntaPorIndice(int index) {
        List<Pregunta> todas = obtenerTodasOrdenadas();
        if (index >= todas.size()) {
            return null;
        }

        Pregunta original = todas.get(index);
        Pregunta copia = new Pregunta();
        copia.setEnunciado(original.getEnunciado());
        copia.setId(original.getId());

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
