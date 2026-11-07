/** 5 minutos (decisión del usuario): lo que otra persona cambie en estos catálogos se ve, como mucho, en ese tiempo. */
const VIGENCIA_CATALOGOS_MS = 5 * 60_000;

const cachesDeSesion = new Set<CacheLista<unknown>>();
const otrasLimpiezas = new Set<() => void>();

/** Vacía todo lo guardado en memoria que depende de quién inició sesión (catálogos, notificaciones). La llaman el
 * inicio y el cierre de sesión de AuthService: así nadie ve datos del usuario anterior en la misma pestaña. */
export function limpiarCachesDeSesion(): void {
  for (const cache of cachesDeSesion) cache.invalidar();
  for (const limpiar of otrasLimpiezas) limpiar();
}

/** Para estado compartido que no es una CacheLista (ver NotificacionesService) y también debe vaciarse al cambiar
 * de sesión. */
export function alLimpiarSesion(limpiar: () => void): void {
  otrasLimpiezas.add(limpiar);
}

/**
 * Lista que casi no cambia (desarrollos, usuarios, asesores) guardada en memoria durante unos minutos, para no
 * pedirla otra vez en cada pantalla. Si dos pantallas la piden a la vez comparten la misma petición. Cada llamada
 * recibe una copia del arreglo, así ninguna pantalla altera la guardada. El servicio dueño llama a invalidar() en
 * cuanto alguien la edita desde esta pestaña, y se vacía sola al iniciar o cerrar sesión.
 */
export class CacheLista<T> {
  private valor: T[] | null = null;
  private vence = 0;
  private enCurso: Promise<T[]> | null = null;
  /** Sube con cada invalidar(): una respuesta que llegue después de invalidar ya no se guarda. */
  private version = 0;

  constructor(private readonly vigenciaMs = VIGENCIA_CATALOGOS_MS) {
    cachesDeSesion.add(this as CacheLista<unknown>);
  }

  obtener(cargar: () => Promise<T[]>): Promise<T[]> {
    if (this.valor && Date.now() < this.vence) return Promise.resolve([...this.valor]);
    if (!this.enCurso) {
      const version = this.version;
      const peticion: Promise<T[]> = cargar()
        .then((lista) => {
          if (version === this.version) {
            this.valor = lista;
            this.vence = Date.now() + this.vigenciaMs;
          }
          return lista;
        })
        .finally(() => {
          if (this.enCurso === peticion) this.enCurso = null;
        });
      this.enCurso = peticion;
    }
    return this.enCurso.then((lista) => [...lista]);
  }

  invalidar(): void {
    this.valor = null;
    this.vence = 0;
    this.enCurso = null;
    this.version++;
  }
}
