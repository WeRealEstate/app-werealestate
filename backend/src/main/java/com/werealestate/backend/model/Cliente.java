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

/** Comprador. Se registra una vez y se liga a todas sus ventas (ver Venta.clienteRef). */
@Entity
@Table(name = "cliente")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(name = "apellido_paterno", length = 120)
    private String apellidoPaterno;

    @Column(name = "apellido_materno", length = 120)
    private String apellidoMaterno;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(length = 30)
    private String telefono;

    @Column(length = 30)
    private String telefono2;

    @Column(length = 150)
    private String correo;

    @Column(length = 18)
    private String curp;

    @Column(length = 13)
    private String rfc;

    @Column(name = "lugar_nacimiento", length = 120)
    private String lugarNacimiento;

    @Column(length = 80)
    private String nacionalidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_civil", length = 20)
    private EstadoCivil estadoCivil;

    @Column(length = 120)
    private String ocupacion;

    @Column(length = 200)
    private String calle;

    @Column(length = 120)
    private String colonia;

    @Column(length = 120)
    private String municipio;

    @Column(length = 80)
    private String estado;

    @Column(name = "codigo_postal", length = 10)
    private String codigoPostal;

    @Column(name = "beneficiario_nombre", length = 200)
    private String beneficiarioNombre;

    @Column(name = "beneficiario_parentesco", length = 80)
    private String beneficiarioParentesco;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private FuenteCliente fuente;

    @Column(name = "fuente_detalle", length = 200)
    private String fuenteDetalle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "captado_por_usuario_id")
    private Usuario captadoPorUsuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "captado_por_asesor_id")
    private AsesorExterno captadoPorAsesor;

    @Column(length = 1000)
    private String notas;

    @Enumerated(EnumType.STRING)
    @Column(name = "expediente_ubicacion", length = 20)
    private UbicacionDocumento expedienteUbicacion;

    @Column(name = "expediente_drive_url", length = 500)
    private String expedienteDriveUrl;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    protected Cliente() {
        // JPA
    }

    public Cliente(String nombre, String apellidoPaterno, String apellidoMaterno) {
        this.nombre = nombre;
        this.apellidoPaterno = apellidoPaterno;
        this.apellidoMaterno = apellidoMaterno;
    }

    /** "Nombre Apellido Paterno Apellido Materno", sin espacios de más. */
    public String nombreCompleto() {
        StringBuilder sb = new StringBuilder(nombre);
        if (apellidoPaterno != null && !apellidoPaterno.isBlank()) sb.append(' ').append(apellidoPaterno);
        if (apellidoMaterno != null && !apellidoMaterno.isBlank()) sb.append(' ').append(apellidoMaterno);
        return sb.toString();
    }

    /** Faltan datos básicos (apellido, fecha de nacimiento o teléfono): típico de los clientes
     * creados a partir de ventas anteriores. */
    public boolean datosIncompletos() {
        return apellidoPaterno == null || apellidoPaterno.isBlank() || fechaNacimiento == null
                || telefono == null || telefono.isBlank();
    }

    public void actualizarDatos(
            String nombre,
            String apellidoPaterno,
            String apellidoMaterno,
            LocalDate fechaNacimiento,
            String telefono,
            String telefono2,
            String correo,
            String curp,
            String rfc,
            String lugarNacimiento,
            String nacionalidad,
            EstadoCivil estadoCivil,
            String ocupacion,
            String calle,
            String colonia,
            String municipio,
            String estado,
            String codigoPostal,
            String beneficiarioNombre,
            String beneficiarioParentesco,
            FuenteCliente fuente,
            String fuenteDetalle,
            Usuario captadoPorUsuario,
            AsesorExterno captadoPorAsesor,
            String notas,
            UbicacionDocumento expedienteUbicacion,
            String expedienteDriveUrl) {
        this.nombre = nombre;
        this.apellidoPaterno = apellidoPaterno;
        this.apellidoMaterno = apellidoMaterno;
        this.fechaNacimiento = fechaNacimiento;
        this.telefono = telefono;
        this.telefono2 = telefono2;
        this.correo = correo;
        this.curp = curp;
        this.rfc = rfc;
        this.lugarNacimiento = lugarNacimiento;
        this.nacionalidad = nacionalidad;
        this.estadoCivil = estadoCivil;
        this.ocupacion = ocupacion;
        this.calle = calle;
        this.colonia = colonia;
        this.municipio = municipio;
        this.estado = estado;
        this.codigoPostal = codigoPostal;
        this.beneficiarioNombre = beneficiarioNombre;
        this.beneficiarioParentesco = beneficiarioParentesco;
        this.fuente = fuente;
        this.fuenteDetalle = fuenteDetalle;
        this.captadoPorUsuario = captadoPorUsuario;
        this.captadoPorAsesor = captadoPorAsesor;
        this.notas = notas;
        this.expedienteUbicacion = expedienteUbicacion;
        this.expedienteDriveUrl = expedienteDriveUrl;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getApellidoPaterno() { return apellidoPaterno; }
    public String getApellidoMaterno() { return apellidoMaterno; }
    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public String getTelefono() { return telefono; }
    public String getTelefono2() { return telefono2; }
    public String getCorreo() { return correo; }
    public String getCurp() { return curp; }
    public String getRfc() { return rfc; }
    public String getLugarNacimiento() { return lugarNacimiento; }
    public String getNacionalidad() { return nacionalidad; }
    public EstadoCivil getEstadoCivil() { return estadoCivil; }
    public String getOcupacion() { return ocupacion; }
    public String getCalle() { return calle; }
    public String getColonia() { return colonia; }
    public String getMunicipio() { return municipio; }
    public String getEstado() { return estado; }
    public String getCodigoPostal() { return codigoPostal; }
    public String getBeneficiarioNombre() { return beneficiarioNombre; }
    public String getBeneficiarioParentesco() { return beneficiarioParentesco; }
    public FuenteCliente getFuente() { return fuente; }
    public String getFuenteDetalle() { return fuenteDetalle; }
    public Usuario getCaptadoPorUsuario() { return captadoPorUsuario; }
    public AsesorExterno getCaptadoPorAsesor() { return captadoPorAsesor; }
    public String getNotas() { return notas; }
    public UbicacionDocumento getExpedienteUbicacion() { return expedienteUbicacion; }
    public String getExpedienteDriveUrl() { return expedienteDriveUrl; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
}
