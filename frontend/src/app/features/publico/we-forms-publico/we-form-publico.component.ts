import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import {
  PreguntaPublica,
  TIPOS_PAGO_FORM,
  TipoPagoForm,
  WeFormEnvioResultado,
  WeFormPublico,
} from '../../../core/models/we-form.model';
import { WeFormsPublicoService } from '../../../core/services/we-forms-publico.service';
import { Aportacion, MESES_ABREVIADO, MESES_NOMBRE, mensualidadConAportaciones, totalAportaciones } from '../../../core/utils/aportaciones';
import {
  SIN_FORMA_PAGO,
  SeleccionPago,
  alternarFormaPago,
  anualidadesComoAportaciones,
  estaBloqueado,
  estaMarcado,
  hayFormaPago,
  llevaEntrada,
  maximoAnualidades,
  unidadPlazo,
} from '../../../core/utils/formas-pago';
import { curpFormatoValido, datosDeCurp } from '../../../core/utils/identidad';
import { AportacionesSelectorComponent } from '../../../shared/aportaciones-selector/aportaciones-selector.component';
import { CorreoInputDirective } from '../../../shared/correo-input/correo-input.directive';
import { MonedaInputDirective } from '../../../shared/moneda-input/moneda-input.directive';
import { DesarrolloLoaderComponent, DesarrolloLoader } from '../../../shared/desarrollo-loader/desarrollo-loader.component';
import { ListaSelectorComponent } from '../../../shared/lista-selector/lista-selector.component';
import { ESTADOS_MX, estadoCanonico } from '../../../core/data/estados-mx';
import { PAISES, PAIS_PRINCIPAL } from '../../../core/data/paises';
import { PinPublicoComponent } from '../../../shared/pin-publico/pin-publico.component';

const ETIQUETA_ENGANCHE: Record<string, string> = {
  downpayment: 'Monto del enganche',
  initial: 'Monto del pago inicial',
};
const MAX_ARCHIVO_BYTES = 2 * 1024 * 1024;

type Etapa = 'pin' | 'cargando' | 'listo' | 'cerrado' | 'error' | 'enviado';

/** /we-forms/:slug: el registro de venta que llena el asesor (con su PIN). Al enviarlo se crea el cliente y la venta. */
@Component({
  selector: 'app-we-form-publico',
  standalone: true,
  imports: [FormsModule, DecimalPipe, ListaSelectorComponent, MonedaInputDirective, CorreoInputDirective, AportacionesSelectorComponent, PinPublicoComponent, DesarrolloLoaderComponent],
  templateUrl: './we-form-publico.component.html',
})
export class WeFormPublicoComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(WeFormsPublicoService);

  readonly slug = this.route.snapshot.paramMap.get('slug') ?? '';
  readonly tiposPago = TIPOS_PAGO_FORM;
  readonly hoy = new Date().toISOString().slice(0, 10);
  /** El primer mes del plan es el mes en que se envía (la venta lleva la fecha de hoy). */
  readonly inicioPlan = new Date(new Date().getFullYear(), new Date().getMonth(), 1);

  /** Desarrollo del formulario, para su pantalla de carga: ?proyecto= del enlace (lo trae el listado y el
   * plano) o, ya cargado el formulario, el nombre de su desarrollo; null = la carga genérica. */
  readonly proyecto = computed<DesarrolloLoader | null>(() => {
    const q = this.route.snapshot.queryParamMap.get('proyecto');
    if (q === 'samai' || q === 'nanuu') return q;
    const nombre = this.form()?.desarrolloNombre?.toLowerCase() ?? '';
    return nombre.includes('nanuu') ? 'nanuu' : nombre.includes('samai') ? 'samai' : null;
  });

  // Con un PIN recordado se arranca en 'cargando' para no mostrar un instante la pantalla del PIN.
  readonly etapa = signal<Etapa>(this.service.pin() ? 'cargando' : 'pin');
  readonly form = signal<WeFormPublico | null>(null);
  readonly mensajeError = signal<string | null>(null);
  readonly errorPin = signal<string | null>(null);
  readonly verificando = signal(false);
  readonly enviando = signal(false);
  readonly errorEnvio = signal<string | null>(null);
  readonly resultado = signal<WeFormEnvioResultado | null>(null);

  readonly valores = signal<Record<number, unknown>>({});
  readonly archivos = signal<Map<number, File>>(new Map());
  readonly errores = signal<Record<number, string>>({});
  readonly aportaciones = signal<Aportacion[]>([]);
  readonly mesesNombre = MESES_NOMBRE;

  // Anualidades: van dentro de la pregunta "Tipo de pago" (así los formularios que ya existen no necesitan
  // preguntas nuevas) y el servidor las guarda como aportaciones programadas, una por año.
  readonly montoAnualidad = signal<number | null>(null);
  readonly mesAnualidad = signal(12);
  /** null = el máximo que permite el plazo. */
  readonly numeroAnualidades = signal<number | null>(null);

  readonly paises = PAISES;
  readonly estadosMx = ESTADOS_MX;

  /** País que eligió el asesor: el de la primera pregunta tipo País del formulario ('' = aún ninguno). */
  readonly paisElegido = computed(() => {
    const q = this.form()?.preguntas.find((x) => x.tipo === 'PAIS');
    return q ? String(this.valores()[q.id] ?? '') : '';
  });
  /** Con México como país, el Estado se elige de la lista de estados; con otro país es texto libre. */
  readonly esMexico = computed(() => this.paisElegido() === PAIS_PRINCIPAL);

  constructor() {
    const pin = this.service.pin();
    if (pin) void this.cargar(pin);
    // Al pasar a México, un Estado escrito que no sea de la lista se borra para que elija uno (si coincide con
    // uno de la lista, se pone con su nombre oficial).
    effect(() => {
      if (!this.esMexico()) return;
      const q = this.pregunta('ESTADO');
      if (!q) return;
      const actual = String(this.valores()[q.id] ?? '');
      const oficial = estadoCanonico(actual);
      if (actual && oficial !== actual) untracked(() => this.setValor(q.id, oficial ?? ''));
    });
  }

  /** La pregunta Estado se contesta con la lista de estados (solo si el país es México). */
  usaListaEstados(p: PreguntaPublica): boolean {
    return p.destino === 'ESTADO' && this.esMexico();
  }

  // ------------------------------------------------------------------ carga

  async cargar(pin: string): Promise<void> {
    this.verificando.set(true);
    this.errorPin.set(null);
    try {
      this.form.set(await this.service.obtener(this.slug, pin));
      this.service.guardarPin(pin);
      this.etapa.set('listo');
    } catch (e) {
      const mensaje = e instanceof HttpErrorResponse && typeof e.error?.message === 'string' ? e.error.message : 'No se pudo abrir el formulario.';
      const status = e instanceof HttpErrorResponse ? e.status : 0;
      if (status === 409) {
        this.mensajeError.set(mensaje);
        this.etapa.set('cerrado');
      } else if (status === 404 && !/PIN/i.test(mensaje)) {
        this.mensajeError.set(mensaje);
        this.etapa.set('error');
      } else {
        // Solo se olvida el PIN si el servidor lo rechazó; un fallo de red o el límite de intentos no lo borran.
        if (status === 403 || status === 404) this.service.guardarPin(null);
        this.errorPin.set(mensaje);
        this.etapa.set('pin');
      }
    } finally {
      this.verificando.set(false);
    }
  }

  /** Olvida el PIN de este dispositivo para que otro asesor pueda entrar con el suyo. */
  salir(): void {
    this.service.guardarPin(null);
    this.errorPin.set(null);
    this.etapa.set('pin');
  }

  // ------------------------------------------------------------------ valores y reglas

  private pregunta(destino: string): PreguntaPublica | undefined {
    return this.form()?.preguntas.find((p) => p.destino === destino);
  }

  valor(id: number): unknown {
    return this.valores()[id];
  }

  setValor(id: number, v: unknown): void {
    this.valores.update((m) => ({ ...m, [id]: v }));
    if (this.errores()[id]) this.errores.update((e) => ({ ...e, [id]: '' }));
    if (this.errorEnvio()) this.errorEnvio.set(null);
  }

  /** La forma de pago marcada en la pregunta "Tipo de pago": una base y, opcional, anualidades o aportaciones. */
  readonly formaPago = computed<SeleccionPago>(() => {
    const q = this.pregunta('TIPO_PAGO');
    return (q ? (this.valores()[q.id] as SeleccionPago | undefined) : undefined) ?? SIN_FORMA_PAGO;
  });
  readonly hayFormaPago = computed(() => hayFormaPago(this.formaPago()));
  readonly esContado = computed(() => this.formaPago().base === 'cash');
  readonly llevaEntrada = computed(() => llevaEntrada(this.formaPago()));

  tipoPagoMarcado(tipo: TipoPagoForm): boolean {
    return estaMarcado(this.formaPago(), tipo);
  }

  tipoPagoBloqueado(tipo: TipoPagoForm): boolean {
    return estaBloqueado(this.formaPago(), tipo);
  }

  /** Marca o desmarca una forma de pago; lo que no se combina con lo ya marcado está bloqueado. */
  alternarTipoPago(p: PreguntaPublica, tipo: TipoPagoForm): void {
    if (this.tipoPagoBloqueado(tipo)) return;
    const nueva = alternarFormaPago(this.formaPago(), tipo);
    this.setValor(p.id, hayFormaPago(nueva) ? nueva : null);
    if (nueva.extra !== 'aportaciones') this.aportaciones.set([]);
    if (nueva.extra !== 'annualities') {
      this.montoAnualidad.set(null);
      this.numeroAnualidades.set(null);
    }
  }

  readonly maximoAnualidades = computed(() => maximoAnualidades(this.plazo() ?? 0));
  readonly anualidadesProgramadas = computed<Aportacion[]>(() => {
    const monto = this.montoAnualidad();
    const plazo = this.plazo() ?? 0;
    if (this.formaPago().extra !== 'annualities' || !monto || monto <= 0 || plazo <= 0) return [];
    const cantidad = Math.min(this.numeroAnualidades() ?? this.maximoAnualidades(), this.maximoAnualidades());
    return anualidadesComoAportaciones(this.inicioPlan, plazo, this.mesAnualidad(), monto, cantidad);
  });
  readonly anualidadesTexto = computed(() =>
    this.anualidadesProgramadas().map((a) => `${MESES_ABREVIADO[a.mes - 1]} ${a.anio}`).join(' · '),
  );

  /** Las aportaciones elegidas mes por mes o las anualidades: lo que baja la mensualidad aparte del enganche. */
  private readonly extrasDelPlan = computed<Aportacion[]>(() =>
    this.formaPago().extra === 'aportaciones'
      ? this.aportaciones()
      : this.formaPago().extra === 'annualities'
        ? this.anualidadesProgramadas()
        : [],
  );
  readonly totalExtras = computed(() => totalAportaciones(this.extrasDelPlan()));

  /** Por qué las anualidades todavía no se pueden enviar (null si están bien o no aplican). */
  private errorAnualidades(): string | null {
    if (this.formaPago().extra !== 'annualities') return null;
    if (!this.plazo()) return 'Indica el plazo para calcular las anualidades.';
    if (this.maximoAnualidades() < 1) return 'Con anualidades el plazo debe ser de al menos 24 meses.';
    if (!this.montoAnualidad() || this.montoAnualidad()! <= 0) return 'Ingresa el monto de cada anualidad.';
    if (this.anualidadesProgramadas().length === 0) return 'Indica cuántas anualidades lleva la venta.';
    if (this.mensualidad() === null) return 'Las anualidades y el enganche cubren todo el precio: deja saldo para las mensualidades.';
    return null;
  }

  readonly lotesElegidos = computed(() => {
    const q = this.pregunta('LOTES');
    const ids = (q ? (this.valores()[q.id] as number[] | undefined) : undefined) ?? [];
    return (this.form()?.lotes ?? []).filter((l) => ids.includes(l.id));
  });

  /** Filtros de la lista de lotes: la manzana se elige de las que tienen lotes; el lote filtra por el
   * principio de su número (con "2" salen el 2, 21, 23…). Los lotes ya marcados siguen elegidos aunque
   * el filtro los oculte. */
  readonly filtroManzana = signal('');
  readonly filtroLote = signal('');

  readonly manzanas = computed(() =>
    [...new Set((this.form()?.lotes ?? []).map((l) => String(l.manzana)))].sort((a, b) =>
      a.localeCompare(b, 'es', { numeric: true }),
    ),
  );

  readonly lotesFiltrados = computed(() => {
    const manzana = this.filtroManzana();
    const numero = this.filtroLote().trim();
    return (this.form()?.lotes ?? []).filter(
      (l) => (!manzana || String(l.manzana) === manzana) && (!numero || String(l.numeroLote).startsWith(numero)),
    );
  });

  limpiarFiltrosLotes(): void {
    this.filtroManzana.set('');
    this.filtroLote.set('');
  }

  readonly precioTotal = computed(() => this.lotesElegidos().reduce((s, l) => s + l.precio, 0));

  private numeroDe(destino: string): number | null {
    const q = this.pregunta(destino);
    const v = q ? this.valores()[q.id] : null;
    return typeof v === 'number' ? v : null;
  }

  readonly plazo = computed(() => this.numeroDe('PLAZO'));
  readonly enganche = computed(() => this.numeroDe('ENGANCHE'));

  readonly unidadPlazo = computed(() => unidadPlazo(this.formaPago()));

  /** Igual que el servidor: precio menos la entrada, repartido entre los meses sin anualidad o aportación. */
  readonly mensualidad = computed(() => {
    const plazo = this.plazo();
    const precio = this.precioTotal();
    if (!this.hayFormaPago() || this.esContado() || !plazo || plazo < 1 || precio <= 0) return null;
    const saldo = precio - (this.llevaEntrada() ? (this.enganche() ?? 0) : 0);
    if (this.formaPago().extra) {
      const m = mensualidadConAportaciones(saldo, this.extrasDelPlan(), plazo);
      return m > 0 ? Math.round(m * 100) / 100 : null;
    }
    return saldo > 0 ? Math.round((saldo / plazo) * 100) / 100 : null;
  });

  /** Las preguntas de enganche, plazo, día de pago y aportaciones solo aparecen cuando el tipo de pago las pide. */
  visible(p: PreguntaPublica): boolean {
    switch (p.destino) {
      case 'ENGANCHE':
        return this.llevaEntrada();
      case 'PLAZO':
      case 'DIA_PAGO':
        return this.hayFormaPago() && !this.esContado();
      case 'APORTACIONES':
        return this.formaPago().extra === 'aportaciones' && (this.plazo() ?? 0) >= 1;
      default:
        return true;
    }
  }

  obligatoria(p: PreguntaPublica): boolean {
    switch (p.destino) {
      case 'ENGANCHE':
      case 'PLAZO':
      case 'DIA_PAGO':
      case 'APORTACIONES':
        return this.visible(p);
      default:
        return p.obligatoria;
    }
  }

  etiquetaDe(p: PreguntaPublica): string {
    if (p.destino === 'ENGANCHE') return ETIQUETA_ENGANCHE[this.formaPago().base ?? ''] ?? p.titulo;
    if (p.destino === 'PLAZO') return `Plazo (${this.unidadPlazo()})`;
    return p.titulo;
  }

  // ------------------------------------------------------------------ entradas

  alternarLote(preguntaId: number, loteId: number): void {
    const actuales = (this.valor(preguntaId) as number[] | undefined) ?? [];
    this.setValor(preguntaId, actuales.includes(loteId) ? actuales.filter((x) => x !== loteId) : [...actuales, loteId]);
  }

  alternarCasilla(preguntaId: number, opcion: string): void {
    const actuales = (this.valor(preguntaId) as string[] | undefined) ?? [];
    this.setValor(preguntaId, actuales.includes(opcion) ? actuales.filter((x) => x !== opcion) : [...actuales, opcion]);
  }

  estaEnLista(preguntaId: number, opcion: string | number): boolean {
    const v = this.valor(preguntaId) as (string | number)[] | undefined;
    return !!v && v.includes(opcion);
  }

  /** CURP y RFC en mayúsculas; con una CURP completa se llena la fecha de nacimiento si estaba vacía. */
  escribirTexto(p: PreguntaPublica, evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    let texto = campo.value;
    if (p.destino === 'CURP' || p.destino === 'RFC') {
      const pos = campo.selectionStart;
      texto = texto.toUpperCase();
      if (campo.value !== texto) {
        campo.value = texto;
        if (pos !== null) campo.setSelectionRange(pos, pos);
      }
    }
    this.setValor(p.id, texto);
    if (p.destino === 'CURP') {
      const datos = datosDeCurp(texto);
      const fecha = this.pregunta('FECHA_NACIMIENTO');
      if (datos && fecha && !this.valor(fecha.id)) this.setValor(fecha.id, datos.fechaNacimiento);
    }
  }

  elegirArchivo(p: PreguntaPublica, evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const archivo = campo.files?.[0];
    if (!archivo) return;
    if (archivo.size > MAX_ARCHIVO_BYTES) {
      campo.value = '';
      this.errores.update((e) => ({ ...e, [p.id]: 'El archivo puede pesar hasta 2 MB.' }));
      return;
    }
    if (!/^image\/(jpeg|png|webp)$|^application\/pdf$/.test(archivo.type)) {
      campo.value = '';
      this.errores.update((e) => ({ ...e, [p.id]: 'Sube una imagen (JPG, PNG, WEBP) o un PDF.' }));
      return;
    }
    this.archivos.update((m) => new Map(m).set(p.id, archivo));
    this.errores.update((e) => ({ ...e, [p.id]: '' }));
  }

  quitarArchivo(p: PreguntaPublica): void {
    this.archivos.update((m) => {
      const nuevo = new Map(m);
      nuevo.delete(p.id);
      return nuevo;
    });
  }

  // ------------------------------------------------------------------ envío

  private vacio(p: PreguntaPublica): boolean {
    if (p.tipo === 'ARCHIVO') return !this.archivos().has(p.id);
    if (p.tipo === 'APORTACIONES') return this.aportaciones().length === 0;
    const v = this.valores()[p.id];
    if (v === undefined || v === null) return true;
    if (typeof v === 'string') return v.trim() === '';
    if (Array.isArray(v)) return v.length === 0;
    return false;
  }

  async enviar(): Promise<void> {
    const f = this.form();
    const pin = this.service.pin();
    if (!f || !pin || this.enviando()) return;

    const errores: Record<number, string> = {};
    for (const p of f.preguntas) {
      if (p.tipo === 'TITULO' || p.tipo === 'ASESOR' || !this.visible(p)) continue;
      if (this.obligatoria(p) && this.vacio(p)) errores[p.id] = 'Campo obligatorio.';
    }
    const curp = this.pregunta('CURP');
    if (curp && !errores[curp.id]) {
      const v = String(this.valor(curp.id) ?? '');
      if (v && !curpFormatoValido(v)) errores[curp.id] = 'El CURP no tiene un formato válido (18 caracteres).';
    }
    const estadoQ = this.pregunta('ESTADO');
    if (estadoQ && this.esMexico() && !errores[estadoQ.id]) {
      const v = String(this.valor(estadoQ.id) ?? '');
      if (v && !estadoCanonico(v)) errores[estadoQ.id] = 'Elige un estado de la lista.';
    }
    const tipoPagoQ = this.pregunta('TIPO_PAGO');
    const errorAnualidades = this.errorAnualidades();
    if (tipoPagoQ && errorAnualidades && !errores[tipoPagoQ.id]) errores[tipoPagoQ.id] = errorAnualidades;
    const dia = this.pregunta('DIA_PAGO');
    if (dia && this.visible(dia) && !errores[dia.id]) {
      const d = this.numeroDe('DIA_PAGO');
      if (d !== null && (d < 1 || d > 31)) errores[dia.id] = 'Un día del 1 al 31.';
    }
    this.errores.set(errores);
    const primero = f.preguntas.find((p) => errores[p.id]);
    if (primero) {
      document.getElementById('q-' + primero.id)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      this.errorEnvio.set('Revisa los campos marcados.');
      return;
    }

    const respuestas: { preguntaId: number; valor: unknown }[] = [];
    for (const p of f.preguntas) {
      if (p.tipo === 'TITULO' || p.tipo === 'ASESOR' || p.tipo === 'ARCHIVO' || !this.visible(p)) continue;
      if (p.tipo === 'APORTACIONES') {
        respuestas.push({ preguntaId: p.id, valor: this.aportaciones().map((a) => ({ anio: a.anio, mes: a.mes, monto: a.monto })) });
      } else if (p.destino === 'TIPO_PAGO') {
        // La forma de pago y, con anualidades, su monto, mes y cuántas (el servidor las vuelve a calcular).
        const sel = this.formaPago();
        respuestas.push({
          preguntaId: p.id,
          valor: hayFormaPago(sel)
            ? {
                base: sel.base,
                extra: sel.extra,
                anualidad:
                  sel.extra === 'annualities'
                    ? { monto: this.montoAnualidad(), mes: this.mesAnualidad(), cantidad: this.anualidadesProgramadas().length }
                    : null,
              }
            : null,
        });
      } else if (!this.vacio(p)) {
        respuestas.push({ preguntaId: p.id, valor: this.valores()[p.id] });
      }
    }

    this.enviando.set(true);
    this.errorEnvio.set(null);
    try {
      this.resultado.set(await this.service.enviar(this.slug, pin, respuestas, this.archivos()));
      this.etapa.set('enviado');
      window.scrollTo({ top: 0 });
    } catch (e) {
      const status = e instanceof HttpErrorResponse ? e.status : 0;
      this.errorEnvio.set(
        e instanceof HttpErrorResponse && typeof e.error?.message === 'string' ? e.error.message : 'No se pudo registrar la venta. Intenta de nuevo.',
      );
      if (status === 409) {
        // Un lote ya no está disponible: se refresca la lista conservando todo lo demás.
        await this.refrescarLotes(pin);
      }
    } finally {
      this.enviando.set(false);
    }
  }

  private async refrescarLotes(pin: string): Promise<void> {
    try {
      const actualizado = await this.service.obtener(this.slug, pin);
      this.form.update((f) => (f ? { ...f, lotes: actualizado.lotes } : f));
      const q = this.pregunta('LOTES');
      if (q) {
        const libres = new Set(actualizado.lotes.map((l) => l.id));
        this.setValor(q.id, ((this.valor(q.id) as number[] | undefined) ?? []).filter((id) => libres.has(id)));
      }
    } catch {
      // si falla, el asesor puede recargar la página
    }
  }

  async nuevoRegistroDeVenta(): Promise<void> {
    const pin = this.service.pin();
    this.valores.set({});
    this.archivos.set(new Map());
    this.errores.set({});
    this.aportaciones.set([]);
    this.montoAnualidad.set(null);
    this.mesAnualidad.set(12);
    this.numeroAnualidades.set(null);
    this.limpiarFiltrosLotes();
    this.errorEnvio.set(null);
    this.resultado.set(null);
    this.etapa.set('cargando');
    if (pin) await this.cargar(pin);
    else this.etapa.set('pin');
  }
}
