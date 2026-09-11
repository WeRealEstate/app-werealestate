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
    // Sin guard y fuera del layout del panel a propósito: cualquiera con el link puede generar
    // cotizaciones, sin sesión ni acceso al resto de la app. Ver `esPublico` en CotizadorComponent.
    path: 'cotizador-publico',
    data: { publico: true },
    loadComponent: () => import('./features/panel/cotizador/cotizador.component').then((m) => m.CotizadorComponent),
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
        path: 'leads/importar',
        loadComponent: () =>
          import('./features/leads/lead-import/lead-import.component').then((m) => m.LeadImportComponent),
      },
      {
        path: 'leads/:id/editar',
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
        path: 'pipeline',
        canActivate: [roleGuard(['ASESOR', 'ADMIN'])],
        loadComponent: () =>
          import('./features/panel/pipeline/pipeline.component').then((m) => m.PipelineComponent),
      },
      {
        path: 'cotizador',
        canActivate: [roleGuard(['ASESOR', 'ADMIN'])],
        loadComponent: () =>
          import('./features/panel/cotizador/cotizador.component').then((m) => m.CotizadorComponent),
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
        path: 'cotizaciones-historial',
        canActivate: [roleGuard(['ADMIN'])],
        loadComponent: () =>
          import('./features/panel/historial-cotizaciones/historial-cotizaciones.component').then(
            (m) => m.HistorialCotizacionesComponent,
          ),
      },
      {
        path: 'promociones',
        canActivate: [roleGuard(['ADMIN'])],
        loadComponent: () =>
          import('./features/panel/promociones/promociones.component').then((m) => m.PromocionesComponent),
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
