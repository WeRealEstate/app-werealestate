import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { Role } from '../models/user.model';

/** Restringe una ruta a los roles indicados; si el usuario tiene otro rol, lo manda a su propio panel. */
export function roleGuard(allowedRoles: Role[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    const user = auth.currentUser();
    if (!user) return router.createUrlTree(['/login']);

    if (allowedRoles.includes(user.rol)) return true;

    return router.createUrlTree([auth.panelRouteForRole(user.rol)]);
  };
}
