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

    /** Número de cliente que se muestra en la app: lo asigna ClienteService (mayor + 1) al crearlo y solo
     * cambia por las consultas nativas de ClienteRepository, nunca por un guardado de la ficha. */
    @Column(nullable = false, updatable = false)
    private Long numero;

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

    /** País de residencia, uno de los de Paises (null = sin dato). */
    @Column(name = "pais_residencia", length = 80)
    private String paisResidencia;

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

    /** Carpeta del cliente que creó la app en Google Drive para los archivos de sus registros de venta. */
    @Column(name = "drive_carpeta_id", length = 100)
    private String driveCarpetaId;

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

    public Long getNumero() {
        return numero;
    }

    public void setNumero(Long numero) {
        this.numero = numero;
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
    public String getPaisResidencia() { return paisResidencia; }
    public void setPaisResidencia(String paisResidencia) { this.paisResidencia = paisResidencia; }
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
    public String getDriveCarpetaId() { return driveCarpetaId; }

    /** Guarda la carpeta que creó la app en Drive. Si el expediente no tiene enlace, queda con el de esta carpeta y
     * en Drive; un enlace puesto a mano se respeta (puede ser una carpeta que la app no puede usar). */
    public void asignarCarpetaDrive(String carpetaId, String url) {
        this.driveCarpetaId = carpetaId;
        if (this.expedienteDriveUrl == null || this.expedienteDriveUrl.isBlank()) {
            this.expedienteDriveUrl = url;
            this.expedienteUbicacion = UbicacionDocumento.DRIVE;
        }
    }

    /** La carpeta ya no existe en Drive (la borraron): la próxima subida busca o crea otra. Si el enlace del expediente
     * era el que puso el sistema para esa carpeta ({@code urlAnterior}), también se quita para que se llene con el de la
     * nueva y no quede apuntando a una carpeta borrada; uno puesto a mano no se toca. */
    public void olvidarCarpetaDrive(String urlAnterior) {
        if (urlAnterior != null && urlAnterior.equals(this.expedienteDriveUrl)) {
            this.expedienteDriveUrl = null;
        }
        this.driveCarpetaId = null;
    }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
}
