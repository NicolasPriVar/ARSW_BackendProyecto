package com.menteMaestra.backend.model;

public class Opcion {

    private String texto;
    private boolean correcta;

    // Getter para 'texto'
    public String getTexto() {
        return texto;
    }

    // Setter para 'texto'
    public void setTexto(String texto) {
        this.texto = texto;
    }

    // Getter para 'correcta'
    public boolean isCorrecta() {
        return correcta;
    }

    // Setter para 'correcta'
    public void setCorrecta(boolean correcta) {
        this.correcta = correcta;
    }
}
