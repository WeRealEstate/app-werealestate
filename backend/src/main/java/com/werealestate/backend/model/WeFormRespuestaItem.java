package com.werealestate.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Una respuesta a una pregunta, guardada con el título y tipo que la pregunta tenía al contestarse. */
@Entity
@Table(name = "we_form_respuesta_item")
public class WeFormRespuestaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "respuesta_id", nullable = false)
    private WeFormRespuesta respuesta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pregunta_id")
    private WeFormPregunta pregunta;

    @Column(name = "pregunta_titulo", nullable = false, length = 300)
    private String preguntaTitulo;

    @Enumerated(EnumType.STRING)
    @Column(name = "pregunta_tipo", nullable = false, length = 30)
    private TipoPreguntaForm preguntaTipo;

    @Column(columnDefinition = "TEXT")
    private String valor;

    @Column(name = "archivo_extension", length = 10)
    private String archivoExtension;

    @Column(nullable = false)
    private int orden;

    protected WeFormRespuestaItem() {
        // JPA
    }

    public WeFormRespuestaItem(
            WeFormRespuesta respuesta, WeFormPregunta pregunta, String valor, String archivoExtension, int orden) {
        this.respuesta = respuesta;
        this.pregunta = pregunta;
        this.preguntaTitulo = pregunta.getTitulo();
        this.preguntaTipo = pregunta.getTipo();
        this.valor = valor;
        this.archivoExtension = archivoExtension;
        this.orden = orden;
    }

    public Long getId() { return id; }
    public WeFormRespuesta getRespuesta() { return respuesta; }
    public WeFormPregunta getPregunta() { return pregunta; }
    public String getPreguntaTitulo() { return preguntaTitulo; }
    public TipoPreguntaForm getPreguntaTipo() { return preguntaTipo; }
    public String getValor() { return valor; }
    public String getArchivoExtension() { return archivoExtension; }
    public int getOrden() { return orden; }
}
