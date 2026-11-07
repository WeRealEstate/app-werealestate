import { CanActivateFn } from '@angular/router';
import { Modulo, Role } from '../models/user.model';

/** Lo que pide un roleGuard o moduloGuard. La precarga de pantallas (PrecargaPorRolStrategy) lo lee para saber qué
 * pantallas puede abrir el usuario sin ejecutar los guards, que redirigen. */
export interface RequisitoRuta {
  roles?: readonly Role[];
  modulos?: readonly Modulo[];
}

/** Guard creado por roleGuard/moduloGuard → lo que exige. WeakMap: no retiene nada que el router ya no use. */
export const REQUISITOS_DE_GUARD = new WeakMap<CanActivateFn, RequisitoRuta>();

/** Registra lo que exige un guard recién creado y lo devuelve tal cual. */
export function conRequisito(guard: CanActivateFn, requisito: RequisitoRuta): CanActivateFn {
  REQUISITOS_DE_GUARD.set(guard, requisito);
  return guard;
}
