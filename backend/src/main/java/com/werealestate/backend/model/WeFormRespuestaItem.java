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
import java.time.LocalDateTime;

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

    // Subida a Google Drive (ver DriveService): el id del archivo en Drive una vez subido; mientras sea null y haya
    // archivo, está pendiente. Los intentos fallidos esperan cada vez más antes de reintentar.
    @Column(name = "drive_archivo_id", length = 100)
    private String driveArchivoId;

    @Column(name = "drive_subido_en")
    private LocalDateTime driveSubidoEn;

    @Column(name = "drive_intentos", nullable = false)
    private int driveIntentos;

    @Column(name = "drive_ultimo_intento")
    private LocalDateTime driveUltimoIntento;

    @Column(name = "drive_error", length = 500)
    private String driveError;

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
    public String getDriveArchivoId() { return driveArchivoId; }
    public LocalDateTime getDriveSubidoEn() { return driveSubidoEn; }
    public int getDriveIntentos() { return driveIntentos; }
    public LocalDateTime getDriveUltimoIntento() { return driveUltimoIntento; }
    public String getDriveError() { return driveError; }

    public void marcarSubidoADrive(String archivoId) {
        this.driveArchivoId = archivoId;
        this.driveSubidoEn = LocalDateTime.now();
        this.driveUltimoIntento = this.driveSubidoEn;
        this.driveError = null;
    }

    public void registrarFalloDrive(String error) {
        this.driveIntentos++;
        this.driveUltimoIntento = LocalDateTime.now();
        this.driveError = error == null ? null : error.length() > 500 ? error.substring(0, 500) : error;
    }
}
