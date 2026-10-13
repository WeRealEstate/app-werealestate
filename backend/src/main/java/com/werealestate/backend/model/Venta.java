package com.werealestate.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Registro de una venta cerrada. A propósito no referencia Lead (ver VentaService): cliente es
 * texto libre porque no todo comprador pasó por el CRM como lead. El asesor sí es una relación
 * real — a un Usuario interno o a un AsesorExterno registrado (gente que vende pero no tiene
 * cuenta en el sistema) — exactamente uno de los dos, nunca los dos ni ninguno (ver
 * VentaService.resolverAsesor y la migración V34).
 *
 * <p>No referencia Lote directo: una venta puede incluir varios lotes (cliente que compra más de
 * uno en la misma operación, con una sola mensualidad/plazo/saldo combinado — ver VentaLote y
 * VentaService). El precio total de la venta es la suma de sus VentaLote, no se guarda aquí.
 */
@Entity
@Table(name = "venta")
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Número de venta visible: automático (mayor + 1) o escrito a mano al registrarla. Se fija al
     * insertar; después solo lo cambia VentaService (consulta directa, que además recorre las
     * consecutivas), por eso JPA no lo actualiza — ver V61. */
    @Column(name = "numero", updatable = false, nullable = false)
    private Long numero;

    /** Nombre del cliente tal como se vio al registrar la venta (se actualiza si se edita el nombre del
     * cliente ligado); sirve para listas y búsquedas sin cargar la ficha. */
    @Column(nullable = false, length = 200)
    private String cliente;

    /** El cliente ligado (ver Cliente); null solo mientras no se migra una venta anterior. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente clienteRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_asesor_id")
    private Usuario usuarioAsesor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asesor_externo_id")
    private AsesorExterno asesorExterno;

    @Column(name = "forma_pago", nullable = false, length = 50)
    private String formaPago;

    @Column(name = "fecha_venta", nullable = false)
    private LocalDate fechaVenta;

    /** Términos de financiamiento fijados al momento de la venta; null en ventas de contado. */
    @Column(precision = 14, scale = 2)
    private BigDecimal mensualidad;

    @Column(name = "plazo_meses")
    private Integer plazoMeses;

    /** "Enganche" / "Pago inicial" / "Aportación anual" según el tipo de pago elegido (mismo
     * concepto que ya usa Cotizacion); ambos null cuando no aplica (Sin enganche / Contado). */
    @Column(name = "enganche_label", length = 30)
    private String engancheLabel;

    @Column(precision = 14, scale = 2)
    private BigDecimal enganche;

    /** Día del mes (1-31) en que paga la mensualidad; en un mes sin ese día se cobra el último. */
    @Column(name = "dia_pago", nullable = false)
    private int diaPago;

    /** true: el mes de la venta cuenta como la primera mensualidad; false: la primera cae el mes
     * siguiente. */
    @Column(name = "primera_mensualidad_mes_venta", nullable = false)
    private boolean primeraMensualidadMesVenta;

    @Column(length = 1000)
    private String notas;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    /** Quién la registró (null en ventas anteriores a este dato). Ver NotificacionService. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrada_por_id")
    private Usuario registradaPor;

    /** La venta nació de un precontrato (We Forms) y su fecha es la del día de envío, a confirmar. */
    @Column(name = "fecha_provisional", nullable = false)
    private boolean fechaProvisional;

    /** Dinero con el que ya estaba apartado el lote al crear la venta desde un precontrato y que aún no
     * se aplica (null = nada pendiente). */
    @Column(name = "deposito_pendiente", precision = 14, scale = 2)
    private java.math.BigDecimal depositoPendiente;

    protected Venta() {
        // JPA
    }

    public Venta(
            String cliente,
            Usuario usuarioAsesor,
            AsesorExterno asesorExterno,
            String formaPago,
            LocalDate fechaVenta,
            BigDecimal mensualidad,
            Integer plazoMeses,
            String engancheLabel,
            BigDecimal enganche,
            Integer diaPago,
            boolean primeraMensualidadMesVenta,
            String notas) {
        this.cliente = cliente;
        this.usuarioAsesor = usuarioAsesor;
        this.asesorExterno = asesorExterno;
        this.formaPago = formaPago;
        this.fechaVenta = fechaVenta;
        this.mensualidad = mensualidad;
        this.plazoMeses = plazoMeses;
        this.engancheLabel = engancheLabel;
        this.enganche = enganche;
        this.diaPago = diaPago != null ? diaPago : fechaVenta.getDayOfMonth();
        this.primeraMensualidadMesVenta = primeraMensualidadMesVenta;
        this.notas = notas;
    }

    /** Modifica los datos capturados de la venta (no toca sus lotes/precios, que se administran
     * aparte). Temporal: el botón que llama a esto en el frontend se va a quitar más adelante. */
    public void actualizar(
            String cliente,
            Usuario usuarioAsesor,
            AsesorExterno asesorExterno,
            String formaPago,
            LocalDate fechaVenta,
            BigDecimal mensualidad,
            Integer plazoMeses,
            String engancheLabel,
            BigDecimal enganche,
            Integer diaPago,
            boolean primeraMensualidadMesVenta,
            String notas) {
        this.cliente = cliente;
        this.usuarioAsesor = usuarioAsesor;
        this.asesorExterno = asesorExterno;
        this.formaPago = formaPago;
        this.fechaVenta = fechaVenta;
        this.mensualidad = mensualidad;
        this.plazoMeses = plazoMeses;
        this.engancheLabel = engancheLabel;
        this.enganche = enganche;
        this.diaPago = diaPago != null ? diaPago : fechaVenta.getDayOfMonth();
        this.primeraMensualidadMesVenta = primeraMensualidadMesVenta;
        this.notas = notas;
    }

    /** Contrato firmado: la comisión de la venta solo se puede entregar con el contrato firmado
     * (ver ComisionService, regla nueva). La fecha de firma es obligatoria cuando está firmado. */
    @jakarta.persistence.Column(name = "contrato_firmado", nullable = false)
    private boolean contratoFirmado;

    @jakarta.persistence.Column(name = "contrato_fecha_firma")
    private LocalDate contratoFechaFirma;

    public boolean isContratoFirmado() {
        return contratoFirmado;
    }

    public LocalDate getContratoFechaFirma() {
        return contratoFechaFirma;
    }

    /** Cómo se firmó (solo con el contrato firmado) y, opcional, el enlace a su carpeta de Drive. */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @jakarta.persistence.Column(name = "contrato_medio", length = 20)
    private MedioFirma contratoMedio;

    @jakarta.persistence.Column(name = "contrato_drive_url", length = 500)
    private String contratoDriveUrl;

    public MedioFirma getContratoMedio() {
        return contratoMedio;
    }

    public String getContratoDriveUrl() {
        return contratoDriveUrl;
    }

    public void registrarContrato(boolean firmado, LocalDate fechaFirma, MedioFirma medio, String driveUrl) {
        this.contratoFirmado = firmado;
        this.contratoFechaFirma = firmado ? fechaFirma : null;
        this.contratoMedio = firmado ? medio : null;
        this.contratoDriveUrl = driveUrl;
    }

    public int getDiaPago() {
        return diaPago;
    }

    public boolean isPrimeraMensualidadMesVenta() {
        return primeraMensualidadMesVenta;
    }

    public Usuario getRegistradaPor() {
        return registradaPor;
    }

    public void setMensualidad(java.math.BigDecimal mensualidad) {
        this.mensualidad = mensualidad;
    }

    public boolean isFechaProvisional() {
        return fechaProvisional;
    }

    public void setFechaProvisional(boolean fechaProvisional) {
        this.fechaProvisional = fechaProvisional;
    }

    public java.math.BigDecimal getDepositoPendiente() {
        return depositoPendiente;
    }

    public void setDepositoPendiente(java.math.BigDecimal depositoPendiente) {
        this.depositoPendiente = depositoPendiente;
    }

    public void setRegistradaPor(Usuario registradaPor) {
        this.registradaPor = registradaPor;
    }

    public Long getId() {
        return id;
    }

    public void setNumero(Long numero) {
        this.numero = numero;
    }

    public Long getNumero() {
        return numero;
    }

    public Cliente getClienteRef() {
        return clienteRef;
    }

    /** Liga la venta a un cliente y deja su nombre como texto de la venta. */
    public void ligarCliente(Cliente c) {
        this.clienteRef = c;
        this.cliente = c.nombreCompleto();
    }

    public String getCliente() {
        return cliente;
    }

    public Usuario getUsuarioAsesor() {
        return usuarioAsesor;
    }

    public AsesorExterno getAsesorExterno() {
        return asesorExterno;
    }

    /** Nombre a mostrar del asesor, sea interno o externo — para bitácoras/historiales de texto
     * (ver LoteService.marcarVendido) que no necesitan distinguir cuál de los dos es. */
    public String getAsesorNombre() {
        return usuarioAsesor != null ? usuarioAsesor.getNombre() : asesorExterno.getNombre();
    }

    public String getFormaPago() {
        return formaPago;
    }

    public LocalDate getFechaVenta() {
        return fechaVenta;
    }

    public BigDecimal getMensualidad() {
        return mensualidad;
    }

    public Integer getPlazoMeses() {
        return plazoMeses;
    }

    public String getEngancheLabel() {
        return engancheLabel;
    }

    public BigDecimal getEnganche() {
        return enganche;
    }

    public String getNotas() {
        return notas;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
