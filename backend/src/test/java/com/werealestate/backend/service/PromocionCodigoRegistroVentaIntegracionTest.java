package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.werealestate.backend.dto.LoteDto;
import com.werealestate.backend.dto.NivelBono;
import com.werealestate.backend.dto.PromocionCreateRequest;
import com.werealestate.backend.dto.PromocionDto;
import com.werealestate.backend.dto.PromocionUpdateRequest;
import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.dto.WeFormDtos;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.integracion.PruebaDeIntegracion;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.ModalidadPromocion;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoPrecioPromocion;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.PromocionRepository;
import com.werealestate.backend.repository.WeFormRespuestaRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** Código de promoción en el registro de venta (12.18.0), desde el panel del asesor (contra la BD local, se revierte). */
class PromocionCodigoRegistroVentaIntegracionTest extends PruebaDeIntegracion {

    @Autowired PromocionService promocionService;
    @Autowired PromocionRepository promociones;
    @Autowired WeFormService formService;
    @Autowired RegistroVentaPanelService panel;
    @Autowired WeFormRespuestaRepository respuestas;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    @Autowired PromocionVentaService promocionVenta;

    private final Random azar = new Random();
    private Usuario admin;
    private Usuario asesor;
    private String slug;

    @BeforeEach
    void preparar() {
        admin = usuario(Role.ADMIN);
        iniciarSesionComo(admin);
        // Que ninguna promoción de la BD local interfiera con las de la prueba.
        promociones.findAll().forEach(p -> p.setActiva(false));
        slug = formService.crear(new WeFormDtos.CrearRequest("Prueba " + etiqueta(), null, samai().getId())).slug();
        asesor = usuario(Role.ASESOR);
    }

    private PromocionDto crear(ModalidadPromocion modalidad, String proyecto, String codigo) {
        iniciarSesionComo(admin);
        boolean bono = modalidad == ModalidadPromocion.BONO_LOTES;
        PromocionDto p = promocionService.crear(new PromocionCreateRequest("Promo " + etiqueta(), proyecto,
                TipoPrecioPromocion.LOTE, modalidad, bono ? null : new BigDecimal("1900"),
                bono ? List.of(new NivelBono(1, new BigDecimal("10000")), new NivelBono(4, new BigDecimal("40000"))) : null,
                bono ? 60 : null, null, true, null, null, codigo));
        em.flush();
        return p;
    }

    private String letras(int n) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < n; i++) s.append((char) ('A' + azar.nextInt(26)));
        return s.toString();
    }

    /** Un registro de venta del asesor con ese lote, tipo de pago, plazo, aportaciones y código. */
    private VentaDto registrar(Lote lote, Object tipoPago, int plazo, List<Map<String, Object>> aportaciones, String codigo) {
        iniciarSesionComo(asesor);
        WeFormDtos.Publico form = panel.obtener(slug);
        List<WeFormDtos.RespuestaEnvio> lista = new ArrayList<>();
        for (WeFormDtos.PreguntaPublica p : form.preguntas()) {
            if (p.destino() == null) continue;
            Object valor = switch (p.destino()) {
                case NOMBRE -> "Ana";
                case APELLIDO_PATERNO -> "Prueba";
                case FECHA_NACIMIENTO -> "1980-01-01";
                case CURP -> letras(4) + "800101M" + letras(5) + "A" + azar.nextInt(10);
                case TELEFONO -> String.valueOf(5_000_000_000L + (long) (azar.nextDouble() * 999_999_999L));
                case LOTES -> List.of(lote.getId());
                case TIPO_PAGO -> tipoPago;
                case PLAZO -> plazo;
                case DIA_PAGO -> 5;
                case APORTACIONES -> aportaciones;
                default -> null;
            };
            if (valor != null) lista.add(new WeFormDtos.RespuestaEnvio(p.id(), json.valueToTree(valor)));
        }
        WeFormDtos.EnvioResultado r = panel.enviar(slug, new WeFormDtos.EnvioRequest(lista, codigo), Map.of());
        em.flush();
        iniciarSesionComo(admin);
        return ventaService.obtener(respuestas.findById(r.respuestaId()).orElseThrow().getVenta().getId());
    }

    private Lote loteSamai() {
        return lote(samai(), etiqueta(), "1", "200", EstadoLote.DISPONIBLE);
    }

    @Test
    void elBonoMandaSobreElTipoDePagoYElPlazo() {
        PromocionDto p = crear(ModalidadPromocion.BONO_LOTES, "samai", null);
        Lote lote = loteSamai();
        BigDecimal lista = LoteDto.from(lote).precio();
        // El asesor marcó contado y 12 meses: con el código queda sin enganche, 60 meses y el bono restado.
        VentaDto v = registrar(lote, Map.of("base", "cash"), 12, null, p.codigo());
        assertEquals(p.nombre(), v.promocionNombre());
        assertEquals(0, lista.subtract(new BigDecimal("10000")).compareTo(v.precioVenta()));
        assertEquals(60, v.plazoMeses());
        assertNull(v.enganche());
        assertEquals(0, v.precioVenta().divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP).compareTo(v.mensualidad()));
    }

    @Test
    void laMensualidadFijaRepartePorIgualLoQueFaltaEnLasAportaciones() {
        PromocionDto p = crear(ModalidadPromocion.MENSUALIDAD_FIJA, "samai", null);
        Lote lote = loteSamai();
        BigDecimal precio = LoteDto.from(lote).precio();
        BigDecimal mensualidad = PromocionVentaService.mensualidadEscalada(new BigDecimal("1900"), precio,
                PromocionVentaService.precioEstandar(promociones.findById(p.id()).orElseThrow(), samai()));
        YearMonth a = YearMonth.now().plusMonths(6);
        YearMonth b = YearMonth.now().plusMonths(18);
        // Los montos que manda el formulario no cuentan: el servidor reparte por igual.
        List<Map<String, Object>> aportaciones = List.of(
                Map.of("anio", a.getYear(), "mes", a.getMonthValue(), "monto", 1),
                Map.of("anio", b.getYear(), "mes", b.getMonthValue(), "monto", 1));
        VentaDto v = registrar(lote, Map.of("base", "msi", "extra", "aportaciones"), 60, aportaciones, p.codigo());
        assertEquals(p.nombre(), v.promocionNombre());
        assertEquals(0, precio.compareTo(v.precioVenta()));
        assertEquals(0, mensualidad.compareTo(v.mensualidad()));
        assertEquals(0, new BigDecimal("1900").compareTo(v.promocionMensualidadFija()));
        BigDecimal resto = precio.subtract(mensualidad.multiply(BigDecimal.valueOf(58)));
        BigDecimal total = v.aportaciones().stream().map(x -> x.monto()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, resto.compareTo(total));
        assertEquals(2, v.aportaciones().size());
    }

    @Test
    void laMensualidadFijaSinAportacionesQueCubranNoSeRegistra() {
        PromocionDto p = crear(ModalidadPromocion.MENSUALIDAD_FIJA, "samai", null);
        Lote lote = loteSamai();
        var e = assertThrows(ValidationException.class,
                () -> registrar(lote, Map.of("base", "msi"), 12, null, p.codigo()));
        assertTrue(e.getMessage().contains("aportación"));
    }

    @Test
    void codigoEquivocadoOtroDesarrolloYTopeDeIntentos() {
        PromocionDto nanuu = crear(ModalidadPromocion.BONO_LOTES, "nanuu", null);
        iniciarSesionComo(asesor);
        assertThrows(ValidationException.class, () -> panel.codigoPromocion(slug, nanuu.codigo()));
        assertThrows(ValidationException.class, () -> panel.codigoPromocion(slug, "12ab"));
        for (int i = 0; i < LimitadorCodigoPromocion.MAX_FALLOS - 2; i++) {
            assertThrows(ValidationException.class, () -> panel.codigoPromocion(slug, "000000x"));
        }
        assertThrows(ResponseStatusException.class, () -> panel.codigoPromocion(slug, "000001"));
    }

    @Test
    void validarElCodigoDevuelveLoQueNecesitaElFormulario() {
        PromocionDto p = crear(ModalidadPromocion.MENSUALIDAD_FIJA, "samai", null);
        iniciarSesionComo(asesor);
        var dto = panel.codigoPromocion(slug, p.codigo());
        assertEquals(p.nombre(), dto.nombre());
        assertEquals(0, new BigDecimal("200").multiply(samai().getPrecioM2()).compareTo(dto.precioEstandar()));
    }

    @Test
    void elCodigoSoloLoVeElAdminYSePuedeEscribirOGenerarOtro() {
        String propio = String.format("%06d", azar.nextInt(1_000_000));
        promociones.findAll().stream().filter(x -> propio.equals(x.getCodigo())).forEach(x -> x.setCodigo(null));
        PromocionDto p = crear(ModalidadPromocion.BONO_LOTES, "samai", propio);
        assertEquals(propio, p.codigo());
        assertTrue(promocionService.listar().stream().anyMatch(x -> propio.equals(x.codigo())));

        assertThrows(ConflictException.class, () -> crear(ModalidadPromocion.BONO_LOTES, "nanuu", propio));
        assertThrows(ValidationException.class, () -> crear(ModalidadPromocion.BONO_LOTES, "nanuu", "1234"));
        assertThrows(ValidationException.class, () -> crear(ModalidadPromocion.BONO_LOTES, "nanuu", "12345678"));

        PromocionDto otro = promocionService.generarCodigo(p.id());
        assertNotEquals(propio, otro.codigo());
        assertTrue(otro.codigo().matches("[A-Z0-9]{6}"));
        PromocionDto editada = promocionService.actualizar(p.id(), new PromocionUpdateRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, propio));
        assertEquals(propio, editada.codigo());

        iniciarSesionComo(usuario(Role.LIDER_AREA));
        assertTrue(promocionService.listar().stream().allMatch(x -> x.codigo() == null));
        assertTrue(promocionService.listarActivas().stream().allMatch(x -> x.codigo() == null));
    }

    @Test
    void aceptaYAplicaCodigosDeCincoSeisYSieteCaracteres() {
        for (int longitud : List.of(5, 6, 7)) {
            String codigo = etiqueta().substring(0, longitud);
            promociones.findAll().stream().filter(x -> codigo.equals(x.getCodigo())).forEach(x -> x.setCodigo(null));
            PromocionDto promo = crear(ModalidadPromocion.BONO_LOTES, "samai", codigo.toLowerCase());
            assertEquals(codigo, promo.codigo());
            iniciarSesionComo(asesor);
            assertEquals(promo.nombre(), panel.codigoPromocion(slug, codigo.toLowerCase()).nombre());
        }
        iniciarSesionComo(admin);
        assertThrows(ValidationException.class, () -> promocionVenta.porCodigo("1234", samai()));
        assertThrows(ValidationException.class, () -> promocionVenta.porCodigo("12345678", samai()));
    }

    @Test
    void elCodigoAceptaLetrasSinDistinguirMayusculas() {
        String propio = "Q" + etiqueta().substring(0, 5);
        promociones.findAll().stream().filter(x -> propio.equalsIgnoreCase(x.getCodigo())).forEach(x -> x.setCodigo(null));
        PromocionDto p = crear(ModalidadPromocion.BONO_LOTES, "samai", propio.toLowerCase());
        assertEquals(propio, p.codigo());
        iniciarSesionComo(asesor);
        assertEquals(p.nombre(), panel.codigoPromocion(slug, " " + propio.toLowerCase() + " ").nombre());
        assertThrows(ValidationException.class, () -> crear(ModalidadPromocion.BONO_LOTES, "nanuu", "AB-12C"));
    }

    @Test
    void elBonoSoloAplicaAlNumeroExactoDeLotes() {
        PromocionDto p = crear(ModalidadPromocion.BONO_LOTES, "samai", null);
        var entidad = promociones.findById(p.id()).orElseThrow();
        assertEquals(0, new BigDecimal("40000").compareTo(entidad.bonoPara(4)));
        assertNull(entidad.bonoPara(2));
        assertNull(entidad.bonoPara(5));
        // Dos lotes: la promoción tiene niveles 1 y 4, así que no aplica.
        Lote a = loteSamai();
        Lote b = loteSamai();
        assertThrows(ValidationException.class,
                () -> promocionVenta.aplicar(p.id(), java.time.LocalDate.now(), List.of(a, b)));
    }

    @Test
    void losNivelesSeValidan() {
        iniciarSesionComo(admin);
        assertThrows(ValidationException.class, () -> promocionService.crear(new PromocionCreateRequest("Promo " + etiqueta(),
                "samai", TipoPrecioPromocion.LOTE, ModalidadPromocion.BONO_LOTES, null,
                List.of(new NivelBono(21, new BigDecimal("1000"))), 60, null, true, null, null, null)));
        assertThrows(ValidationException.class, () -> promocionService.crear(new PromocionCreateRequest("Promo " + etiqueta(),
                "samai", TipoPrecioPromocion.LOTE, ModalidadPromocion.BONO_LOTES, null,
                List.of(new NivelBono(3, new BigDecimal("1000")), new NivelBono(3, new BigDecimal("2000"))), 60, null, true,
                null, null, null)));
        assertThrows(ValidationException.class, () -> promocionService.crear(new PromocionCreateRequest("Promo " + etiqueta(),
                "samai", TipoPrecioPromocion.LOTE, ModalidadPromocion.BONO_LOTES, null, List.of(), 60, null, true, null, null, null)));
    }
}
