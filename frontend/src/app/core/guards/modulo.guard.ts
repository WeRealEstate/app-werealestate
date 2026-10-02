import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { Modulo } from '../models/user.model';

/** Restringe una ruta a quien tenga al menos uno de los módulos indicados (ver backend
 * ModulosAcceso); si no, lo manda a su panel de inicio. Va junto a roleGuard, no en su lugar: el rol
 * dice qué puede llegar a ver, el módulo qué le dejó activo el admin. */
export function moduloGuard(modulos: Modulo[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    const user = auth.currentUser();
    if (!user) return router.createUrlTree(['/login']);
    if (auth.tieneAlgunModulo(modulos)) return true;

    return router.createUrlTree([auth.panelRouteForRole(user.rol)]);
  };
}
