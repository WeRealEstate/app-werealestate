package com.werealestate.backend.service;

import com.werealestate.backend.dto.NumeracionVentaDto;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.repository.VentaRepository;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Numeración de las ventas: el número de cada desarrollo (S-1, N-1…), consecutivo y sin saltos, y el número global de
 * respaldo. Reservar un número puede recorrer otras ventas y vacía el contexto de persistencia, así que quien lo usa
 * lo pide antes de cargar nada de su operación.
 */
@Service
@Transactional
public class NumeracionVentaService {

    private final VentaRepository ventaRepository;
    private final AutorizacionVentas autorizacion;

    public NumeracionVentaService(VentaRepository ventaRepository, AutorizacionVentas autorizacion) {
        this.ventaRepository = ventaRepository;
        this.autorizacion = autorizacion;
    }

    /** El número global de siempre (respaldo interno, ya no se muestra): el siguiente = mayor + 1. Va con candado. */
    long reservarNumeroGlobal() {
        ventaRepository.bloquearNumeracion();
        return ventaRepository.maxNumero() + 1;
    }

    /** Deja libre el número que se quiere para una venta dentro de su desarrollo y devuelve el que le toca: sin número
     * pedido, el siguiente automático (mayor + 1 de ese desarrollo); con número pedido, ese mismo — y si otra venta
     * del desarrollo ya lo tiene, se recorren +1 esa y las que le siguen de forma consecutiva (se detiene en el primer
     * hueco). Va con candado para que dos registros simultáneos no se pisen. exceptoId = la venta que se está moviendo
     * (0 si es una nueva) y numeroActual = el que tenía (-1 si es nueva): al irse, deja libre su lugar. */
    long reservarNumero(Long pedido, long desarrolloId, long exceptoId, long numeroActual) {
        ventaRepository.bloquearNumeracion();
        if (pedido == null) {
            return ventaRepository.maxNumeroDesarrollo(desarrolloId) + 1;
        }
        long hasta = finDeLaRacha(desarrolloId, pedido, numeroActual);
        if (hasta >= pedido) {
            ventaRepository.recorrerDesarrollo(desarrolloId, pedido, hasta, exceptoId);
        }
        return pedido;
    }

    /** Último número de la racha de consecutivos que empieza en "desde" dentro del desarrollo (sin contar el que se
     * está moviendo, que se va de su lugar); desde - 1 si "desde" está libre. */
    private long finDeLaRacha(long desarrolloId, long desde, long numeroIgnorado) {
        Set<Long> enUso = new HashSet<>(ventaRepository.numerosDesarrolloDesde(desarrolloId, desde));
        enUso.remove(numeroIgnorado);
        long fin = desde - 1;
        while (enUso.contains(fin + 1)) fin++;
        return fin;
    }

    /** Al eliminar una venta, las de número mayor bajan 1 (el global y el de su desarrollo): la numeración no queda
     * con hueco. El candado ya lo tomó quien elimina. */
    void cerrarHuecoDeVentaEliminada(long numeroGlobal, Long desarrolloId, Long numeroEnDesarrollo) {
        ventaRepository.bajarDesde(numeroGlobal);
        if (desarrolloId != null && numeroEnDesarrollo != null) {
            ventaRepository.bajarDesarrolloDesde(desarrolloId, numeroEnDesarrollo);
        }
    }

    /** Para el formulario: el siguiente número automático del desarrollo y, si el número consultado ya está ocupado
     * (por una venta distinta de excluirId), cuál es y cuántas ventas se recorrerían. */
    @Transactional(readOnly = true)
    public NumeracionVentaDto consultarNumeracion(Long numero, Long excluirId, Long desarrolloId) {
        autorizacion.exigirAdminOLider();
        if (desarrolloId == null) return new NumeracionVentaDto(1, null, null, null, 0);
        long siguiente = ventaRepository.maxNumeroDesarrollo(desarrolloId) + 1;
        if (numero == null || numero < 1) {
            return new NumeracionVentaDto(siguiente, null, null, null, 0);
        }
        Venta ocupante = ventaRepository.findByDesarrolloIdAndNumeroDesarrollo(desarrolloId, numero).orElse(null);
        if (ocupante == null || ocupante.getId().equals(excluirId)) {
            return new NumeracionVentaDto(siguiente, null, null, null, 0);
        }
        Long ignorado = excluirId == null ? null : ventaRepository.findById(excluirId).map(Venta::getNumeroDesarrollo).orElse(null);
        long fin = finDeLaRacha(desarrolloId, numero, ignorado == null ? -1L : ignorado);
        return new NumeracionVentaDto(
                siguiente, ocupante.getId(), ocupante.getCliente(), ocupante.getFechaVenta(), (int) (fin - numero + 1));
    }
}
