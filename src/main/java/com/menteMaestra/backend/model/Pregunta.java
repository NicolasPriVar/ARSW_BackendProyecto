package com.menteMaestra.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "preguntas")
public class Pregunta {

    @Id
    private String id;
    private String enunciado;
    private List<Opcion> opciones;

    public Pregunta() {}

    public Pregunta(String enunciado, List<Opcion> opciones) {
        this.enunciado = enunciado;
        this.opciones = opciones;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEnunciado() {
        return enunciado;
    }

    public void setEnunciado(String enunciado) {
        this.enunciado = enunciado;
    }

    public List<Opcion> getOpciones() {
        return opciones;
    }

    public void setOpciones(List<Opcion> opciones) {
        this.opciones = opciones;
    }
}
