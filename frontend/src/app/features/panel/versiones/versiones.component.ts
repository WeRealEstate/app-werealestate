import { Component, computed } from '@angular/core';
import { version } from '../../../../../package.json';
import { CHANGELOG, EntradaVersion } from '../../../core/changelog';

interface GrupoMenor {
  clave: string;
  entradas: EntradaVersion[];
}

interface GrupoMayor {
  mayor: number;
  menores: GrupoMenor[];
}

const partes = (v: string): number[] => v.split('.').map((n) => Number.parseInt(n, 10) || 0);

/** Orden descendente por mayor, menor y parche. */
const masNuevoPrimero = (a: EntradaVersion, b: EntradaVersion): number => {
  const [a1, a2, a3] = partes(a.version);
  const [b1, b2, b3] = partes(b.version);
  return b1 - a1 || b2 - a2 || b3 - a3;
};

@Component({
  selector: 'app-versiones',
  standalone: true,
  templateUrl: './versiones.component.html',
})
export class VersionesComponent {
  readonly versionActual = version;

  /** Versión mayor → versión menor → versiones (la más nueva primero en todos los niveles). */
  readonly grupos = computed<GrupoMayor[]>(() => {
    const mayores = new Map<number, Map<string, EntradaVersion[]>>();
    for (const entrada of [...CHANGELOG].sort(masNuevoPrimero)) {
      const [mayor, menor] = partes(entrada.version);
      const menores = mayores.get(mayor) ?? new Map<string, EntradaVersion[]>();
      const clave = `${mayor}.${menor}`;
      menores.set(clave, [...(menores.get(clave) ?? []), entrada]);
      mayores.set(mayor, menores);
    }
    return [...mayores].map(([mayor, menores]) => ({
      mayor,
      menores: [...menores].map(([clave, entradas]) => ({ clave, entradas })),
    }));
  });

  fecha(iso: string): string {
    return iso.split('-').reverse().join('/');
  }
}
