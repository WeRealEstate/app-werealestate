package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.werealestate.backend.dto.WeFormDtos;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.integracion.PruebaDeIntegracion;
import com.werealestate.backend.model.CampoCliente;
import com.werealestate.backend.model.Cliente;
import com.werealestate.backend.model.EstadoCivil;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoPreguntaForm;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.WeFormRespuestaRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

/** Preguntas propias de We Forms vinculadas a datos opcionales del cliente (12.19.0; BD local, se revierte). */
class WeFormCampoClienteIntegracionTest extends PruebaDeIntegracion {

    @Autowired WeFormService formService;
    @Autowired RegistroVentaPanelService panel;
    @Autowired WeFormRespuestaRepository respuestas;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;

    private final Random azar = new Random();
    private Usuario admin;
    private Usuario asesor;
    private WeFormDtos.Detalle form;

    @BeforeEach
    void preparar() {
        admin = usuario(Role.ADMIN);
        iniciarSesionComo(admin);
        form = formService.crear(new WeFormDtos.CrearRequest("Prueba " + etiqueta(), null, samai().getId()));
        asesor = usuario(Role.ASESOR);
    }

    private WeFormDtos.Pregunta pregunta(TipoPreguntaForm tipo, String titulo, CampoCliente campo) {
        iniciarSesionComo(admin);
        WeFormDtos.Pregunta p = formService.crearPregunta(form.id(), new WeFormDtos.PreguntaRequest(
                tipo, titulo, null, false, List.of("Opción 1", "Opción 2"), true, campo));
        em.flush();
        return p;
    }

    private String letras(int n) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < n; i++) s.append((char) ('A' + azar.nextInt(26)));
        return s.toString();
    }

    /** Registra una venta con esas respuestas extra (por id de pregunta) y devuelve el cliente que quedó. */
    private Cliente registrar(String curp, Map<Long, Object> extra) {
        Lote lote = lote(samai(), etiqueta(), "1", "200", EstadoLote.DISPONIBLE);
        iniciarSesionComo(asesor);
        List<WeFormDtos.RespuestaEnvio> lista = new ArrayList<>();
        for (WeFormDtos.PreguntaPublica p : panel.obtener(form.slug()).preguntas()) {
            Object valor = extra.get(p.id());
            if (valor == null && p.destino() != null) {
                valor = switch (p.destino()) {
                    case NOMBRE -> "Ana";
                    case APELLIDO_PATERNO -> "Prueba";
                    case FECHA_NACIMIENTO -> "1980-01-01";
                    case CURP -> curp;
                    case TELEFONO -> String.valueOf(5_000_000_000L + (long) (azar.nextDouble() * 999_999_999L));
                    case LOTES -> List.of(lote.getId());
                    case TIPO_PAGO -> Map.of("base", "cash");
                    default -> null;
                };
            }
            if (valor != null) lista.add(new WeFormDtos.RespuestaEnvio(p.id(), json.valueToTree(valor)));
        }
        var r = panel.enviar(form.slug(), new WeFormDtos.EnvioRequest(lista), Map.of());
        em.flush();
        em.clear();
        return respuestas.findById(r.respuestaId()).orElseThrow().getCliente();
    }

    private String curp() {
        return letras(4) + "800101M" + letras(5) + "A" + azar.nextInt(10);
    }

    @Test
    void lasRespuestasVinculadasLlenanAlClienteNuevo() {
        var ocupacion = pregunta(TipoPreguntaForm.CORTA, "¿A qué se dedica?", CampoCliente.OCUPACION);
        var civil = pregunta(TipoPreguntaForm.DESPLEGABLE, "Estado civil", CampoCliente.ESTADO_CIVIL);
        var nacionalidad = pregunta(TipoPreguntaForm.PAIS, "Nacionalidad", CampoCliente.NACIONALIDAD);
        var beneficiario = pregunta(TipoPreguntaForm.CORTA, "Beneficiario", CampoCliente.BENEFICIARIO_NOMBRE);
        assertEquals(CampoCliente.OPCIONES_ESTADO_CIVIL, civil.opciones());

        Cliente c = registrar(curp(), Map.of(ocupacion.id(), "Médica", civil.id(), "Casado(a)",
                nacionalidad.id(), "México", beneficiario.id(), "Luis Prueba"));
        assertEquals("Médica", c.getOcupacion());
        assertEquals(EstadoCivil.CASADO, c.getEstadoCivil());
        assertEquals("México", c.getNacionalidad());
        assertEquals("Luis Prueba", c.getBeneficiarioNombre());
        // La pregunta País vinculada a nacionalidad no se toma como país de residencia.
        assertEquals(null, c.getPaisResidencia());
    }

    @Test
    void enUnClienteQueYaExistiaSoloLlenaLoVacio() {
        var ocupacion = pregunta(TipoPreguntaForm.CORTA, "Ocupación", CampoCliente.OCUPACION);
        var civil = pregunta(TipoPreguntaForm.OPCION_MULTIPLE, "Estado civil", CampoCliente.ESTADO_CIVIL);
        String curp = curp();
        registrar(curp, Map.of(ocupacion.id(), "Médica"));
        Cliente c = registrar(curp, Map.of(ocupacion.id(), "Abogada", civil.id(), "Viudo(a)"));
        assertEquals("Médica", c.getOcupacion());
        assertEquals(EstadoCivil.VIUDO, c.getEstadoCivil());
    }

    @Test
    void cadaDatoUnaVezYSoloConTiposQueLeSirven() {
        pregunta(TipoPreguntaForm.CORTA, "Ocupación", CampoCliente.OCUPACION);
        assertThrows(ValidationException.class, () -> pregunta(TipoPreguntaForm.PARRAFO, "Otra", CampoCliente.OCUPACION));
        assertThrows(ValidationException.class, () -> pregunta(TipoPreguntaForm.CORTA, "Civil", CampoCliente.ESTADO_CIVIL));
        assertThrows(ValidationException.class, () -> pregunta(TipoPreguntaForm.FECHA, "Nac.", CampoCliente.NACIONALIDAD));
    }

    @Test
    void elLugarDeNacimientoLlenaLaNacionalidad() {
        var nacionalidad = pregunta(TipoPreguntaForm.CORTA, "Nacionalidad", CampoCliente.NACIONALIDAD);
        var lugar = pregunta(TipoPreguntaForm.CORTA, "Lugar de nacimiento", CampoCliente.LUGAR_NACIMIENTO);
        // La nacionalidad que se mande no cuenta: sale del país de nacimiento.
        Cliente c = registrar(curp(), Map.of(lugar.id(), "oaxaca, mexico", nacionalidad.id(), "Otra"));
        assertEquals("Oaxaca, México", c.getLugarNacimiento());
        assertEquals("Mexicana", c.getNacionalidad());

        Cliente otro = registrar(curp(), Map.of(lugar.id(), "Houston, Texas, Estados Unidos"));
        assertEquals("Houston, Texas, Estados Unidos", otro.getLugarNacimiento());
        assertEquals("Estadounidense", otro.getNacionalidad());

        assertThrows(ValidationException.class, () -> registrar(curp(), Map.of(lugar.id(), "Houston, México")));
        assertThrows(ValidationException.class, () -> registrar(curp(), Map.of(lugar.id(), "Oaxaca, Narnia")));
    }
}
