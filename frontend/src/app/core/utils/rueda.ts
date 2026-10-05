/** Distingue una rueda de mouse de dos dedos sobre un trackpad, porque ambos llegan al navegador como
 * eventos `wheel`. La rueda de mouse manda saltos grandes y enteros (±100, ±120…) o va por líneas
 * (deltaMode distinto de 0, p. ej. Firefox); el trackpad manda muchos deltas chicos, a menudo con
 * componente horizontal. El tipo se decide con el PRIMER evento de cada gesto y se conserva mientras
 * sigan llegando (el impulso del trackpad también trae números grandes), hasta una pausa corta. */
const PAUSA_ENTRE_GESTOS_MS = 250;
const SALTO_MINIMO_RUEDA_MOUSE = 100;

export class DetectorDeRueda {
  private esMouse = false;
  private ultimoEvento = 0;

  /** true si este evento pertenece a una rueda de mouse; false si es un gesto de trackpad. */
  esRuedaDeMouse(event: WheelEvent): boolean {
    const ahora = performance.now();
    if (ahora - this.ultimoEvento > PAUSA_ENTRE_GESTOS_MS) {
      this.esMouse =
        event.deltaMode !== 0 ||
        (event.deltaX === 0 &&
          Number.isInteger(event.deltaY) &&
          Math.abs(event.deltaY) >= SALTO_MINIMO_RUEDA_MOUSE);
    }
    this.ultimoEvento = ahora;
    return this.esMouse;
  }
}
