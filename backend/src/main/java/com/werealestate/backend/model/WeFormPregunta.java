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
import java.util.Arrays;
import java.util.List;

/** Una pregunta de un formulario. Con `destino` alimenta un campo del cliente o de la venta (y no se
 * puede borrar); sin él es una pregunta propia del formulario (se archiva en vez de borrarse, así sus
 * respuestas viejas se conservan). */
@Entity
@Table(name = "we_form_pregunta")
public class WeFormPregunta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "form_id", nullable = false)
    private WeForm form;

    @Column(nullable = false)
    private int orden;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoPreguntaForm tipo;

    @Column(nullable = false, length = 300)
    private String titulo;

    @Column(length = 500)
    private String descripcion;

    @Column(nullable = false)
    private boolean obligatoria;

    @Column(columnDefinition = "TEXT")
    private String opciones;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private DestinoPregunta destino;

    @Column(nullable = false)
    private boolean visible = true;

    @Column(nullable = false)
    private boolean archivada;

    protected WeFormPregunta() {
        // JPA
    }

    public WeFormPregunta(
            WeForm form, int orden, TipoPreguntaForm tipo, String titulo, String descripcion,
            boolean obligatoria, String opciones, DestinoPregunta destino, boolean visible) {
        this.form = form;
        this.orden = orden;
        this.tipo = tipo;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.obligatoria = obligatoria;
        this.opciones = opciones;
        this.destino = destino;
        this.visible = visible;
    }

    /** Opciones como lista (una por línea en la base). */
    public List<String> opcionesLista() {
        if (opciones == null || opciones.isBlank()) return List.of();
        return Arrays.stream(opciones.split("\n")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public void editar(String titulo, String descripcion, boolean obligatoria, String opciones, boolean visible) {
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.obligatoria = obligatoria;
        this.opciones = opciones;
        this.visible = visible;
    }

    public void setOrden(int orden) { this.orden = orden; }
    public void archivar() { this.archivada = true; }

    public Long getId() { return id; }
    public WeForm getForm() { return form; }
    public int getOrden() { return orden; }
    public TipoPreguntaForm getTipo() { return tipo; }
    public String getTitulo() { return titulo; }
    public String getDescripcion() { return descripcion; }
    public boolean isObligatoria() { return obligatoria; }
    public String getOpciones() { return opciones; }
    public DestinoPregunta getDestino() { return destino; }
    public boolean isVisible() { return visible; }
    public boolean isArchivada() { return archivada; }
}
