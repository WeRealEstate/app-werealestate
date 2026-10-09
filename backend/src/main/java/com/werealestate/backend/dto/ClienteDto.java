package com.werealestate.backend.dto;

import com.werealestate.backend.model.Cliente;
import com.werealestate.backend.model.EstadoCivil;
import com.werealestate.backend.model.FuenteCliente;
import com.werealestate.backend.model.UbicacionDocumento;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;

/** Ficha completa de un cliente, con el resumen y el historial de sus compras. */
public record ClienteDto(
        Long id,
        String nombre,
        String apellidoPaterno,
        String apellidoMaterno,
        String nombreCompleto,
        LocalDate fechaNacimiento,
        Integer edad,
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
        // Quién lo captó: USUARIO / ASESOR (externo) o null.
        String captadoPorTipo,
        Long captadoPorId,
        String captadoPorNombre,
        String notas,
        UbicacionDocumento expedienteUbicacion,
        String expedienteDriveUrl,
        boolean activo,
        boolean datosIncompletos,
        LocalDateTime fechaCreacion,
        int compras,
        BigDecimal totalComprado,
        BigDecimal totalAbonado,
        BigDecimal saldoPendiente,
        List<ClienteVentaDto> ventas) {

    public record ClienteVentaDto(
            Long ventaId,
            Long numero,
            LocalDate fechaVenta,
            List<String> desarrollos,
            List<String> lotes,
            BigDecimal precio,
            BigDecimal abonado,
            BigDecimal saldo,
            // true si es copropietario de esa venta (no el cliente principal).
            boolean copropietario) {
    }

    public static ClienteDto from(Cliente c, List<ClienteVentaDto> ventas) {
        String tipo = null;
        Long id = null;
        String nombre = null;
        if (c.getCaptadoPorUsuario() != null) {
            tipo = "USUARIO";
            id = c.getCaptadoPorUsuario().getId();
            nombre = c.getCaptadoPorUsuario().getNombre();
        } else if (c.getCaptadoPorAsesor() != null) {
            tipo = "ASESOR";
            id = c.getCaptadoPorAsesor().getId();
            nombre = c.getCaptadoPorAsesor().getNombre();
        }
        BigDecimal total = ventas.stream().map(ClienteVentaDto::precio).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal abonado = ventas.stream().map(ClienteVentaDto::abonado).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ClienteDto(
                c.getId(),
                c.getNombre(),
                c.getApellidoPaterno(),
                c.getApellidoMaterno(),
                c.nombreCompleto(),
                c.getFechaNacimiento(),
                c.getFechaNacimiento() == null ? null : Period.between(c.getFechaNacimiento(), LocalDate.now()).getYears(),
                c.getTelefono(),
                c.getTelefono2(),
                c.getCorreo(),
                c.getCurp(),
                c.getRfc(),
                c.getLugarNacimiento(),
                c.getNacionalidad(),
                c.getEstadoCivil(),
                c.getOcupacion(),
                c.getCalle(),
                c.getColonia(),
                c.getMunicipio(),
                c.getEstado(),
                c.getCodigoPostal(),
                c.getBeneficiarioNombre(),
                c.getBeneficiarioParentesco(),
                c.getFuente(),
                c.getFuenteDetalle(),
                tipo,
                id,
                nombre,
                c.getNotas(),
                c.getExpedienteUbicacion(),
                c.getExpedienteDriveUrl(),
                c.isActivo(),
                c.datosIncompletos(),
                c.getFechaCreacion(),
                ventas.size(),
                total,
                abonado,
                total.subtract(abonado).max(BigDecimal.ZERO),
                ventas);
    }
}
