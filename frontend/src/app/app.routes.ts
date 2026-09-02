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
        path: 'leads',
        loadComponent: () =>
          import('./features/leads/leads-list/leads-list.component').then((m) => m.LeadsListComponent),
      },
      {
        path: 'leads/nuevo',
        loadComponent: () =>
          import('./features/leads/lead-form/lead-form.component').then((m) => m.LeadFormComponent),
      },
      {
        path: 'leads/:id',
        loadComponent: () =>
          import('./features/leads/lead-detail/lead-detail.component').then((m) => m.LeadDetailComponent),
      },
      {
        path: 'calendario',
        loadComponent: () =>
          import('./features/panel/calendario/calendario.component').then((m) => m.CalendarioComponent),
      },
      {
        path: 'tarjetas',
        canActivate: [roleGuard(['ASESOR', 'ADMIN'])],
        loadComponent: () =>
          import('./features/panel/tarjetas/tarjetas.component').then((m) => m.TarjetasComponent),
      },
      {
        path: 'comisiones',
        canActivate: [roleGuard(['ASESOR', 'ADMIN'])],
        loadComponent: () =>
          import('./features/panel/comisiones/comisiones.component').then((m) => m.ComisionesComponent),
      },
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
      {
        path: 'usuarios',
        canActivate: [roleGuard(['ADMIN'])],
        loadComponent: () =>
          import('./features/usuarios/usuarios-list/usuarios-list.component').then((m) => m.UsuariosListComponent),
      },
      {
        path: 'usuarios/nuevo',
        canActivate: [roleGuard(['ADMIN'])],
        loadComponent: () =>
          import('./features/usuarios/usuario-form/usuario-form.component').then((m) => m.UsuarioFormComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
