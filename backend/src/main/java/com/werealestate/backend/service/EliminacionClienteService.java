package com.werealestate.backend.service;

import com.werealestate.backend.dto.ClienteEliminacionDto;
import com.werealestate.backend.dto.NumeroClienteDto;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.model.Cliente;
import com.werealestate.backend.model.ClienteNumero;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaCopropietario;
import com.werealestate.backend.model.WeFormRespuesta;
import com.werealestate.backend.model.WeFormRespuestaItem;
import com.werealestate.backend.repository.ClienteNumeroRepository;
import com.werealestate.backend.repository.ClienteRepository;
import com.werealestate.backend.repository.ReciboVentaRepository;
import com.werealestate.backend.repository.VentaComisionRepository;
import com.werealestate.backend.repository.VentaCopropietarioRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.repository.WeFormRespuestaItemRepository;
import com.werealestate.backend.repository.WeFormRespuestaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Eliminar un cliente (decisiones del usuario, 2026-11-12). Solo admin y administración, con su contraseña (la UI
 * además espera 10 s, como al eliminar una venta). En una sola transacción:
 * <ul>
 *   <li>Sus ventas como titular se eliminan completas con la misma regla que eliminar una venta
 *       ({@link VentaService#eliminarYaAutorizada}): recibos borrados, lotes de vuelta a Disponible, comisiones
 *       conservadas como «Venta eliminada» y la numeración de ventas sin hueco.</li>
 *   <li>De las ventas de otro titular donde es copropietario solo se le quita; la venta sigue.</li>
 *   <li>Sus registros de venta de We Forms se borran, con sus archivos del servidor (ya confirmada la transacción); lo
 *       que ya se subió a Google Drive no se toca.</li>
 *   <li>Sus números de cliente se quitan; si nadie más comparte ese número en el desarrollo, los mayores bajan 1.</li>
 * </ul>
 * Vive aparte de ClienteService porque VentaService ya depende de ClienteService.
 */
@Service
@Transactional
public class EliminacionClienteService {

    private static final Logger log = LoggerFactory.getLogger(EliminacionClienteService.class);

    private final ClienteRepository clienteRepository;
    private final VentaRepository ventaRepository;
    private final VentaLoteRepository ventaLoteRepository;
    private final VentaCopropietarioRepository copropietarioRepository;
    private final ReciboVentaRepository reciboVentaRepository;
    private final VentaComisionRepository comisionRepository;
    private final ClienteNumeroRepository numeroRepository;
    private final ClienteNumeracionService numeracion;
    private final WeFormRespuestaRepository respuestaRepository;
    private final WeFormRespuestaItemRepository itemRepository;
    private final VentaService ventaService;
    private final CurrentUserProvider currentUserProvider;
    private final PasswordEncoder passwordEncoder;
    private final String uploadsPrivadosDir;

    public EliminacionClienteService(
            ClienteRepository clienteRepository,
            VentaRepository ventaRepository,
            VentaLoteRepository ventaLoteRepository,
            VentaCopropietarioRepository copropietarioRepository,
            ReciboVentaRepository reciboVentaRepository,
            VentaComisionRepository comisionRepository,
            ClienteNumeroRepository numeroRepository,
            ClienteNumeracionService numeracion,
            WeFormRespuestaRepository respuestaRepository,
            WeFormRespuestaItemRepository itemRepository,
            VentaService ventaService,
            CurrentUserProvider currentUserProvider,
            PasswordEncoder passwordEncoder,
            @Value("${app.uploads-privados.dir:uploads-privados}") String uploadsPrivadosDir) {
        this.clienteRepository = clienteRepository;
        this.ventaRepository = ventaRepository;
        this.ventaLoteRepository = ventaLoteRepository;
        this.copropietarioRepository = copropietarioRepository;
        this.reciboVentaRepository = reciboVentaRepository;
        this.comisionRepository = comisionRepository;
        this.numeroRepository = numeroRepository;
        this.numeracion = numeracion;
        this.respuestaRepository = respuestaRepository;
        this.itemRepository = itemRepository;
        this.ventaService = ventaService;
        this.currentUserProvider = currentUserProvider;
        this.passwordEncoder = passwordEncoder;
        this.uploadsPrivadosDir = uploadsPrivadosDir;
    }

    /** Lo que se borraría, para la ventana de confirmación. */
    @Transactional(readOnly = true)
    public ClienteEliminacionDto vistaPrevia(Long clienteId) {
        exigirAdminOLider();
        Cliente cliente = cliente(clienteId);
        List<ClienteEliminacionDto.Venta> ventas = ventasComoTitular(clienteId).stream()
                .map(v -> new ClienteEliminacionDto.Venta(
                        v.getEtiqueta(),
                        ventaLoteRepository.findByVentaId(v.getId()).stream()
                                .map(vl -> "Mz " + vl.getLote().getManzana() + " / Lote " + vl.getLote().getNumeroLote())
                                .toList(),
                        reciboVentaRepository.findByVentaId(v.getId()).size(),
                        comisionRepository.findAllByVentaId(v.getId()).size()))
                .toList();
        List<String> copropiedades = copropiedadesAjenas(clienteId).stream().map(cp -> cp.getVenta().getEtiqueta()).toList();
        List<WeFormRespuesta> respuestas = respuestaRepository.findByClienteId(clienteId);
        int archivos = (int) archivosEnServidor(respuestas).stream().filter(Files::exists).count();
        return new ClienteEliminacionDto(
                cliente.getId(),
                cliente.nombreCompleto(),
                numeracion.numerosDe(clienteId).stream().map(NumeroClienteDto::etiqueta).toList(),
                ventas,
                copropiedades,
                respuestas.size(),
                archivos);
    }

    /** Elimina al cliente con todo lo descrito en la clase. Todo o nada. */
    public void eliminar(Long clienteId, String password) {
        Usuario actual = exigirAdminOLider();
        if (password == null || !passwordEncoder.matches(password, actual.getPassword())) {
            throw new ForbiddenOperationException("Contraseña incorrecta");
        }
        cliente(clienteId);

        // 1) Sus ventas como titular, de la de número más alto a la más baja (cada una se vuelve a leer: la numeración
        //    vacía el contexto de persistencia).
        List<Long> ventaIds = ventasComoTitular(clienteId).stream()
                .sorted(Comparator.comparing(Venta::getNumeroGlobal).reversed())
                .map(Venta::getId)
                .toList();
        ventaIds.forEach(ventaService::eliminarYaAutorizada);

        // 2) Se le quita de las ventas de otro titular donde es copropietario.
        copropietarioRepository.deleteAll(copropiedadesAjenas(clienteId));
        copropietarioRepository.flush();

        // 3) Sus registros de We Forms y, ya confirmada la transacción, sus archivos del servidor.
        List<WeFormRespuesta> respuestas = respuestaRepository.findByClienteId(clienteId);
        List<Path> archivos = archivosEnServidor(respuestas);
        List<Long> respuestaIds = respuestas.stream().map(WeFormRespuesta::getId).toList();
        if (!respuestaIds.isEmpty()) {
            itemRepository.deleteAll(itemRepository.findByRespuestaIdInOrderByOrdenAsc(respuestaIds));
            itemRepository.flush();
            respuestaRepository.deleteAll(respuestas);
            respuestaRepository.flush();
        }
        borrarAlConfirmar(archivos);

        // 4) Sus números de cliente: si ya nadie comparte ese número en el desarrollo, los mayores bajan 1.
        for (ClienteNumero n : numeroRepository.deCliente(clienteId)) {
            Long desarrolloId = n.getDesarrollo().getId();
            long numero = n.getNumero();
            numeroRepository.bloquear(desarrolloId);
            numeroRepository.delete(n);
            numeroRepository.flush();
            if (numeroRepository.conNumero(desarrolloId, numero).isEmpty()) {
                numeroRepository.bajarDesde(desarrolloId, numero);
            }
        }

        // 5) El cliente (se vuelve a leer: los pasos anteriores vaciaron el contexto de persistencia).
        clienteRepository.delete(cliente(clienteId));
        clienteRepository.flush();
    }

    private List<Venta> ventasComoTitular(Long clienteId) {
        return ventaRepository.findByClienteRefId(clienteId);
    }

    /** Sus copropiedades en ventas de otro titular (las de sus propias ventas se van con ellas). */
    private List<VentaCopropietario> copropiedadesAjenas(Long clienteId) {
        return copropietarioRepository.findByClienteId(clienteId).stream()
                .filter(cp -> cp.getVenta().getClienteRef() == null || !clienteId.equals(cp.getVenta().getClienteRef().getId()))
                .toList();
    }

    private List<Path> archivosEnServidor(List<WeFormRespuesta> respuestas) {
        if (respuestas.isEmpty()) return List.of();
        List<Path> rutas = new ArrayList<>();
        for (WeFormRespuestaItem item : itemRepository.findByRespuestaIdInOrderByOrdenAsc(
                respuestas.stream().map(WeFormRespuesta::getId).toList())) {
            if (item.getArchivoExtension() == null) continue;
            rutas.add(WeFormService.rutaArchivo(uploadsPrivadosDir, item.getRespuesta().getId(), item.getId(), item.getArchivoExtension()));
        }
        return rutas;
    }

    /** Los archivos se borran solo si la eliminación se confirma (si algo falla y se revierte, siguen ahí). */
    private void borrarAlConfirmar(List<Path> archivos) {
        if (archivos.isEmpty()) return;
        Runnable borrar = () -> archivos.forEach(ruta -> {
            try {
                Files.deleteIfExists(ruta);
            } catch (IOException e) {
                log.warn("No se pudo borrar el archivo {} del cliente eliminado", ruta, e);
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    borrar.run();
                }
            });
        } else {
            borrar.run();
        }
    }

    private Cliente cliente(Long id) {
        return clienteRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
    }

    private Usuario exigirAdminOLider() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN && actual.getRol() != Role.LIDER_AREA) {
            throw new ForbiddenOperationException("Solo un administrador o personal de administración puede eliminar un cliente");
        }
        return actual;
    }
}
