package com.werealestate.backend.service;

import com.werealestate.backend.dto.NumeracionClienteDto;
import com.werealestate.backend.dto.NumeroClienteDto;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.Cliente;
import com.werealestate.backend.model.ClienteNumero;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaCopropietario;
import com.werealestate.backend.repository.ClienteNumeroRepository;
import com.werealestate.backend.repository.VentaCopropietarioRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Número de cliente por desarrollo: SAMAI (S-1, S-2…) y Aldea Nanuu (N-1, N-2…) llevan cada uno su numeración; el
 * desarrollo "Otro" no numera. Un cliente recibe número en un desarrollo con su primera venta ahí: el titular que aún
 * no tiene, el siguiente (el mayor + 1); los copropietarios que aún no tienen, el mismo del titular. Quien ya tiene
 * número en ese desarrollo lo conserva aunque compre otra vez o después sea titular. Registrar un cliente sin venta
 * no le da número, y eliminar una venta no se lo quita. El número consecutivo de siempre ({@code cliente.numero})
 * queda como respaldo y ya no se muestra (ver V72).
 */
@Service
@Transactional
public class ClienteNumeracionService {

    private final ClienteNumeroRepository numeroRepository;
    private final VentaLoteRepository ventaLoteRepository;
    private final VentaCopropietarioRepository copropietarioRepository;

    public ClienteNumeracionService(
            ClienteNumeroRepository numeroRepository,
            VentaLoteRepository ventaLoteRepository,
            VentaCopropietarioRepository copropietarioRepository) {
        this.numeroRepository = numeroRepository;
        this.ventaLoteRepository = ventaLoteRepository;
        this.copropietarioRepository = copropietarioRepository;
    }

    /** "S" para SAMAI, "N" para Aldea Nanuu; null para los desarrollos que no numeran clientes ("Otro"). */
    public static String prefijo(String desarrollo) {
        if (desarrollo == null) return null;
        return switch (desarrollo) {
            case "SAMAI Campestre" -> "S";
            case "Aldea Nanuu" -> "N";
            default -> null;
        };
    }

    /** SAMAI primero, luego Nanuu (orden en que se muestran y se ordena la lista de clientes). */
    static int ordenDe(String prefijo) {
        return "S".equals(prefijo) ? 0 : "N".equals(prefijo) ? 1 : 2;
    }

    /**
     * Da número en cada desarrollo de los lotes de la venta (SAMAI y/o Nanuu) a quien todavía no tenga: el titular, el
     * siguiente; los copropietarios, el del titular. Se llama al registrar una venta y al cambiar su titular o sus
     * copropietarios; si ya todos tienen, no cambia nada.
     */
    public void asignarPorVenta(Venta venta) {
        Cliente titular = venta.getClienteRef();
        if (titular == null) return;
        Map<Long, Desarrollo> desarrollos = new LinkedHashMap<>();
        ventaLoteRepository.findByVentaId(venta.getId()).forEach(vl -> {
            Desarrollo d = vl.getLote().getDesarrollo();
            if (prefijo(d.getNombre()) != null) desarrollos.putIfAbsent(d.getId(), d);
        });
        if (desarrollos.isEmpty()) return;
        List<Cliente> copropietarios = copropietarioRepository.findByVentaIdOrderByOrdenAsc(venta.getId()).stream()
                .map(VentaCopropietario::getCliente)
                .toList();

        for (Desarrollo d : desarrollos.values()) {
            numeroRepository.bloquear(d.getId());
            long numeroTitular = numeroRepository.findByClienteIdAndDesarrolloId(titular.getId(), d.getId())
                    .map(ClienteNumero::getNumero)
                    .orElseGet(() -> numeroRepository
                            .save(new ClienteNumero(titular, d, numeroRepository.maxNumero(d.getId()) + 1))
                            .getNumero());
            for (Cliente cp : copropietarios) {
                if (numeroRepository.findByClienteIdAndDesarrolloId(cp.getId(), d.getId()).isEmpty()) {
                    numeroRepository.save(new ClienteNumero(cp, d, numeroTitular));
                }
            }
        }
    }

    // ---------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public List<NumeroClienteDto> numerosDe(Long clienteId) {
        return ordenados(numeroRepository.deCliente(clienteId).stream().map(ClienteNumeracionService::dto).toList());
    }

    /** Los números de todos los clientes (por id), para la lista completa sin una consulta por fila. */
    @Transactional(readOnly = true)
    public Map<Long, List<NumeroClienteDto>> todos() {
        Map<Long, List<NumeroClienteDto>> porCliente = new HashMap<>();
        for (ClienteNumero n : numeroRepository.todosConDesarrollo()) {
            porCliente.computeIfAbsent(n.getCliente().getId(), k -> new ArrayList<>()).add(dto(n));
        }
        porCliente.replaceAll((k, v) -> ordenados(v));
        return porCliente;
    }

    /** Para el aviso previo al cambiar un número a mano: el siguiente automático de ese desarrollo y, si el número
     * consultado ya lo tiene otro cliente (distinto de excluirId y de su grupo), quién y cuántos números se recorren. */
    @Transactional(readOnly = true)
    public NumeracionClienteDto consultar(Long desarrolloId, Long numero, Long excluirId) {
        long siguiente = numeroRepository.maxNumero(desarrolloId) + 1;
        if (numero == null || numero < 1) return new NumeracionClienteDto(siguiente, null, null, 0);
        Long actualDelExcluido = excluirId == null
                ? null
                : numeroRepository.findByClienteIdAndDesarrolloId(excluirId, desarrolloId).map(ClienteNumero::getNumero).orElse(null);
        if (actualDelExcluido != null && actualDelExcluido.longValue() == numero) {
            return new NumeracionClienteDto(siguiente, null, null, 0);
        }
        List<ClienteNumero> ocupantes = numeroRepository.conNumero(desarrolloId, numero);
        if (ocupantes.isEmpty()) return new NumeracionClienteDto(siguiente, null, null, 0);
        String nombres = ocupantes.stream().map(o -> o.getCliente().nombreCompleto()).collect(Collectors.joining(", "));
        long fin = finDeLaRacha(desarrolloId, numero, actualDelExcluido == null ? -1L : actualDelExcluido);
        return new NumeracionClienteDto(siguiente, ocupantes.get(0).getCliente().getId(), nombres, (int) (fin - numero + 1));
    }

    // ---------------------------------------------------------------- cambio a mano

    /**
     * Cambia el número de un cliente en un desarrollo. Se mueve junto con quienes comparten su número (el titular y
     * los copropietarios de su compra). Si el número nuevo ya lo tiene otro, ese y los consecutivos que le siguen se
     * recorren +1 hasta el primer hueco, como el número de venta; el lugar que deja queda libre.
     */
    public void cambiarNumero(Long clienteId, Long desarrolloId, long nuevo) {
        if (nuevo < 1) throw new ValidationException("El número de cliente debe ser 1 o mayor");
        numeroRepository.bloquear(desarrolloId);
        ClienteNumero propio = numeroRepository.findByClienteIdAndDesarrolloId(clienteId, desarrolloId)
                .orElseThrow(() -> new ValidationException("Ese cliente todavía no tiene número en ese desarrollo"));
        long actual = propio.getNumero();
        if (actual == nuevo) return;
        List<Long> grupo = numeroRepository.conNumero(desarrolloId, actual).stream()
                .map(n -> n.getCliente().getId())
                .toList();
        long hasta = finDeLaRacha(desarrolloId, nuevo, actual);
        if (hasta >= nuevo) numeroRepository.recorrer(desarrolloId, nuevo, hasta, grupo);
        numeroRepository.fijar(desarrolloId, grupo, nuevo);
    }

    /** Último número de la racha de consecutivos en uso que empieza en "desde", sin contar el número que se está
     * moviendo (deja su lugar); desde - 1 si "desde" está libre. */
    private long finDeLaRacha(Long desarrolloId, long desde, long ignorado) {
        Set<Long> enUso = new HashSet<>(numeroRepository.numerosDesde(desarrolloId, desde));
        enUso.remove(ignorado);
        long fin = desde - 1;
        while (enUso.contains(fin + 1)) fin++;
        return fin;
    }

    // ---------------------------------------------------------------- utilidades

    private static NumeroClienteDto dto(ClienteNumero n) {
        String prefijo = prefijo(n.getDesarrollo().getNombre());
        return new NumeroClienteDto(
                n.getDesarrollo().getId(), n.getDesarrollo().getNombre(), prefijo, n.getNumero(), etiqueta(prefijo, n.getNumero()));
    }

    static String etiqueta(String prefijo, long numero) {
        return (prefijo == null ? "" : prefijo + "-") + numero;
    }

    private static List<NumeroClienteDto> ordenados(List<NumeroClienteDto> numeros) {
        return numeros.stream().sorted(Comparator.comparingInt((NumeroClienteDto n) -> ordenDe(n.prefijo()))).toList();
    }

    /** Lo que se busca por número: "S-40" (prefijo S, 40) o solo "40" (prefijo null: cualquier desarrollo). */
    record BusquedaNumero(String prefijo, long numero) {}

    /** "s-40", "S40", "#n 12" → (S|N, número); solo dígitos ("40") → (null, número); si no es un número, null. */
    static BusquedaNumero leerBusqueda(String texto) {
        if (texto == null) return null;
        String t = texto.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("[\\s#-]", "");
        if (t.matches("[SN]\\d{1,9}")) return new BusquedaNumero(t.substring(0, 1), Long.parseLong(t.substring(1)));
        if (t.matches("\\d{1,9}")) return new BusquedaNumero(null, Long.parseLong(t));
        return null;
    }

    /** true si alguno de los números coincide con lo buscado ("S-40" exacto, o "40" en cualquier desarrollo). */
    static boolean coincide(List<NumeroClienteDto> numeros, String texto) {
        BusquedaNumero b = leerBusqueda(texto);
        if (b == null || numeros == null) return false;
        return numeros.stream().anyMatch(n -> n.numero() == b.numero() && (b.prefijo() == null || b.prefijo().equals(n.prefijo())));
    }
}
