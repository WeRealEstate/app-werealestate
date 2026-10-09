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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Persona que vende pero no tiene cuenta en el sistema (sin login, sin rol): solo un nombre
 * registrado por un admin para poder acreditarle ventas, igual que a un Usuario interno (ver
 * Venta.usuarioAsesor/asesorExterno).
 */
@Entity
@Table(name = "asesor_externo")
public class AsesorExterno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nombre;

    // Nullable: celular se pide al crear uno nuevo; correo puede omitirse. Los registrados antes
    // de estos campos tampoco los tienen todavía.
    @Column(length = 20)
    private String celular;

    @Column(length = 150)
    private String correo;

    // PIN de 4 letras/números (mayúsculas) para el botón "Asesor" de los planos públicos; null = sin PIN.
    @Column(length = 4)
    private String pin;

    @Column(nullable = false)
    private boolean activo = true;

    /** Solo para la ficha de comunidad de un usuario interno con rol ASESOR (ver
     * ComunidadInternosService): lo enlaza a su usuario para que aparezca en el árbol de Comunidades
     * We. null = asesor externo de verdad. */
    @Column(name = "usuario_id", unique = true)
    private Long usuarioId;

    // Teams: INDEPENDIENTE (default, trabaja solo) / LIDER (encabeza un equipo) / LINEA (reporta a
    // liderDirecto). Ver TipoAsesorExterno y AsesorExternoService.resolverJerarquia.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoAsesorExterno tipo = TipoAsesorExterno.INDEPENDIENTE;

    /** Solo se llena cuando tipo = LINEA: el LIDER (línea 1) o LINEA de línea 1 (línea 2) al que
     * reporta. Null en INDEPENDIENTE/LIDER. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lider_directo_id")
    private AsesorExterno liderDirecto;

    /** Solo se llena cuando tipo = LINEA: 1 si liderDirecto es un LIDER, 2 si liderDirecto es un
     * LINEA de línea 1 — nunca hay línea 3 (ver AsesorExternoService.resolverJerarquia). */
    @Column(name = "nivel_linea")
    private Integer nivelLinea;

    // Contrato: las fechas son opcionales (firma y vencimiento no siempre aplican).
    @Enumerated(EnumType.STRING)
    @Column(name = "contrato_estado", nullable = false, length = 30)
    private EstadoContratoAsesor contratoEstado = EstadoContratoAsesor.VIGENTE;

    @Column(name = "contrato_fecha_firma")
    private LocalDate contratoFechaFirma;

    @Column(name = "contrato_fecha_vencimiento")
    private LocalDate contratoFechaVencimiento;

    // A qué planos públicos puede entrar (SAMAI / Aldea Nanuu).
    @Column(name = "acceso_samai", nullable = false)
    private boolean accesoSamai = true;

    @Column(name = "acceso_nanuu", nullable = false)
    private boolean accesoNanuu = true;

    // Ficha: datos secundarios, todos opcionales (null = sin definir).
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ExperienciaAsesor experiencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "contrato_copia", length = 20)
    private UbicacionDocumento contratoCopia;

    @Column(name = "contrato_drive_url", length = 500)
    private String contratoDriveUrl;

    @Column(name = "expediente_aplica")
    private Boolean expedienteAplica;

    @Enumerated(EnumType.STRING)
    @Column(name = "expediente_ubicacion", length = 20)
    private UbicacionDocumento expedienteUbicacion;

    @Column(name = "expediente_drive_url", length = 500)
    private String expedienteDriveUrl;

    /** Quién trajo al asesor: un usuario del sistema, otro asesor externo, o "otro" (un nombre o
     * "captado en un evento"). A lo más uno de los tres. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "traido_por_usuario_id")
    private Usuario traidoPorUsuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "traido_por_asesor_id")
    private AsesorExterno traidoPorAsesor;

    @Column(name = "traido_por_otro", length = 200)
    private String traidoPorOtro;

    @Column(length = 1000)
    private String notas;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    protected AsesorExterno() {
        // JPA
    }

    public AsesorExterno(String nombre, String celular, String correo) {
        this.nombre = nombre;
        this.celular = celular;
        this.correo = correo;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCelular() {
        return celular;
    }

    public void setCelular(String celular) {
        this.celular = celular;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public boolean isActivo() {
        return activo;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public TipoAsesorExterno getTipo() {
        return tipo;
    }

    public void setTipo(TipoAsesorExterno tipo) {
        this.tipo = tipo;
    }

    public AsesorExterno getLiderDirecto() {
        return liderDirecto;
    }

    public void setLiderDirecto(AsesorExterno liderDirecto) {
        this.liderDirecto = liderDirecto;
    }

    public Integer getNivelLinea() {
        return nivelLinea;
    }

    public void setNivelLinea(Integer nivelLinea) {
        this.nivelLinea = nivelLinea;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public EstadoContratoAsesor getContratoEstado() {
        return contratoEstado;
    }

    public void setContratoEstado(EstadoContratoAsesor contratoEstado) {
        this.contratoEstado = contratoEstado;
    }

    public LocalDate getContratoFechaFirma() {
        return contratoFechaFirma;
    }

    public void setContratoFechaFirma(LocalDate contratoFechaFirma) {
        this.contratoFechaFirma = contratoFechaFirma;
    }

    public LocalDate getContratoFechaVencimiento() {
        return contratoFechaVencimiento;
    }

    public void setContratoFechaVencimiento(LocalDate contratoFechaVencimiento) {
        this.contratoFechaVencimiento = contratoFechaVencimiento;
    }

    public boolean isAccesoSamai() {
        return accesoSamai;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }

    public void setAccesoSamai(boolean accesoSamai) {
        this.accesoSamai = accesoSamai;
    }

    public boolean isAccesoNanuu() {
        return accesoNanuu;
    }

    public void setAccesoNanuu(boolean accesoNanuu) {
        this.accesoNanuu = accesoNanuu;
    }

    /** Un contrato VIGENTE cuya fecha de vencimiento ya pasó cuenta como VENCIDO sin que un admin
     * tenga que cambiarlo a mano; cualquier otro estado se respeta tal cual. */
    public EstadoContratoAsesor getContratoEstadoEfectivo() {
        if (contratoEstado == EstadoContratoAsesor.VIGENTE
                && contratoFechaVencimiento != null
                && contratoFechaVencimiento.isBefore(LocalDate.now())) {
            return EstadoContratoAsesor.VENCIDO;
        }
        return contratoEstado;
    }

    public ExperienciaAsesor getExperiencia() {
        return experiencia;
    }

    public UbicacionDocumento getContratoCopia() {
        return contratoCopia;
    }

    public String getContratoDriveUrl() {
        return contratoDriveUrl;
    }

    public Boolean getExpedienteAplica() {
        return expedienteAplica;
    }

    public UbicacionDocumento getExpedienteUbicacion() {
        return expedienteUbicacion;
    }

    public String getExpedienteDriveUrl() {
        return expedienteDriveUrl;
    }

    public Usuario getTraidoPorUsuario() {
        return traidoPorUsuario;
    }

    public AsesorExterno getTraidoPorAsesor() {
        return traidoPorAsesor;
    }

    public String getTraidoPorOtro() {
        return traidoPorOtro;
    }

    public String getNotas() {
        return notas;
    }

    public void actualizarFicha(
            ExperienciaAsesor experiencia,
            UbicacionDocumento contratoCopia,
            String contratoDriveUrl,
            Boolean expedienteAplica,
            UbicacionDocumento expedienteUbicacion,
            String expedienteDriveUrl,
            Usuario traidoPorUsuario,
            AsesorExterno traidoPorAsesor,
            String traidoPorOtro,
            String notas) {
        this.experiencia = experiencia;
        this.contratoCopia = contratoCopia;
        this.contratoDriveUrl = contratoDriveUrl;
        this.expedienteAplica = expedienteAplica;
        this.expedienteUbicacion = expedienteUbicacion;
        this.expedienteDriveUrl = expedienteDriveUrl;
        this.traidoPorUsuario = traidoPorUsuario;
        this.traidoPorAsesor = traidoPorAsesor;
        this.traidoPorOtro = traidoPorOtro;
        this.notas = notas;
    }
}
