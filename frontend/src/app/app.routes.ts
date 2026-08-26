import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'panel',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/panel/panel-layout/panel-layout.component').then((m) => m.PanelLayoutComponent),
    children: [
      {
        path: 'asesor',
        canActivate: [roleGuard(['ASESOR'])],
        loadComponent: () =>
          import('./features/panel/asesor-panel/asesor-panel.component').then((m) => m.AsesorPanelComponent),
      },
      {
        path: 'equipo',
        canActivate: [roleGuard(['EQUIPO_INTERNO', 'LIDER_AREA'])],
        loadComponent: () =>
          import('./features/panel/equipo-panel/equipo-panel.component').then((m) => m.EquipoPanelComponent),
      },
      {
        path: 'admin',
        canActivate: [roleGuard(['ADMIN'])],
        loadComponent: () =>
          import('./features/panel/admin-panel/admin-panel.component').then((m) => m.AdminPanelComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
