import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/guards/auth.guard';
import { moduloGuard } from './core/guards/modulo.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    // Plano público en pantalla completa, sin sesión ni layout — pensado para compartir el link
    // directo de un desarrollo. Mismas restricciones que /cotizador-publico/lotes: se puede ver el
    // estado de cada lote y apartar uno disponible, pero no editar el plano ni liberar un lote ya
    // apartado. Ver PlanoPublicoComponent.
    path: 'samai',
    data: { proyecto: 'samai' },
    loadComponent: () =>
      import('./features/publico/plano-publico/plano-publico.component').then((m) => m.PlanoPublicoComponent),
  },
  {
    path: 'aldea-nanuu',
    data: { proyecto: 'nanuu' },
    loadComponent: () =>
      import('./features/publico/plano-publico/plano-publico.component').then((m) => m.PlanoPublicoComponent),
  },
  {
    // Sin guard y fuera del layout del panel a propósito: cualquiera con el link puede generar
    // cotizaciones, sin sesión ni acceso al resto de la app. Ver `esPublico` en CotizadorComponent.
    // El layout público solo pone el header/footer de marca y el ancho centrado (lo que
    // PanelLayoutComponent le da al Cotizador interno); no exige login ni agrega navegación.
    path: 'cotizador-publico',
    loadComponent: () =>
      import('./features/publico/cotizador-publico-layout/cotizador-publico-layout.component').then(
        (m) => m.CotizadorPublicoLayoutComponent,
      ),
    children: [
      {
        path: '',
        data: { publico: true },
        loadComponent: () =>
          import('./features/panel/cotizador/cotizador.component').then((m) => m.CotizadorComponent),
      },
      {
        // Ver disponibilidad de lotes de SAMAI/Nanuu y apartar/liberar uno, sin sesión — mismas
        // restricciones que un asesor, ver LoteService.cambiarEstadoPublico.
        path: 'lotes',
        loadComponent: () =>
          import('./features/publico/lotes-publico/lotes-publico.component').then((m) => m.LotesPublicoComponent),
      },
    ],
  },
  {
    path: 'panel',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/panel/panel-layout/panel-layout.component').then((m) => m.PanelLayoutComponent),
    children: [
      {
        path: 'leads',
        canActivate: [moduloGuard(['LEADS'])],
        loadComponent: () =>
          import('./features/leads/leads-list/leads-list.component').then((m) => m.LeadsListComponent),
      },
      {
        path: 'leads/nuevo',
        canActivate: [moduloGuard(['LEADS', 'PIPELINE'])],
        loadComponent: () =>
          import('./features/leads/lead-form/lead-form.component').then((m) => m.LeadFormComponent),
      },
      {
        path: 'leads/importar',
        canActivate: [moduloGuard(['LEADS'])],
        loadComponent: () =>
          import('./features/leads/lead-import/lead-import.component').then((m) => m.LeadImportComponent),
      },
      {
        path: 'leads/:id/editar',
        canActivate: [moduloGuard(['LEADS', 'PIPELINE'])],
        loadComponent: () =>
          import('./features/leads/lead-form/lead-form.component').then((m) => m.LeadFormComponent),
      },
      {
        path: 'leads/:id',
        canActivate: [moduloGuard(['LEADS', 'PIPELINE'])],
        loadComponent: () =>
          import('./features/leads/lead-detail/lead-detail.component').then((m) => m.LeadDetailComponent),
      },
      {
        path: 'lotes',
        canActivate: [roleGuard(['ASESOR', 'LIDER_AREA', 'ADMIN']), moduloGuard(['LOTES'])],
        loadComponent: () =>
          import('./features/lotes/lotes-list/lotes-list.component').then((m) => m.LotesListComponent),
      },
      {
        path: 'lotes/historial',
        canActivate: [roleGuard(['ASESOR', 'LIDER_AREA', 'ADMIN']), moduloGuard(['LOTES'])],
        loadComponent: () =>
          import('./features/lotes/lotes-historial/lotes-historial.component').then(
            (m) => m.LotesHistorialComponent,
          ),
      },
      {
        path: 'lotes/nuevo',
        canActivate: [roleGuard(['ADMIN']), moduloGuard(['LOTES'])],
        loadComponent: () =>
          import('./features/lotes/lote-form/lote-form.component').then((m) => m.LoteFormComponent),
      },
      {
        path: 'lotes/importar',
        canActivate: [roleGuard(['ADMIN']), moduloGuard(['LOTES'])],
        loadComponent: () =>
          import('./features/lotes/lote-import/lote-import.component').then((m) => m.LoteImportComponent),
      },
      {
        path: 'lotes/:id/editar',
        canActivate: [roleGuard(['ADMIN']), moduloGuard(['LOTES'])],
        loadComponent: () =>
          import('./features/lotes/lote-form/lote-form.component').then((m) => m.LoteFormComponent),
      },
      {
        path: 'plano',
        canActivate: [roleGuard(['ASESOR', 'LIDER_AREA', 'ADMIN']), moduloGuard(['PLANO'])],
        loadComponent: () => import('./features/panel/plano/plano.component').then((m) => m.PlanoComponent),
      },
      {
        path: 'calendario',
        canActivate: [moduloGuard(['CALENDARIO'])],
        loadComponent: () =>
          import('./features/panel/calendario/calendario.component').then((m) => m.CalendarioComponent),
      },
      {
        path: 'pipeline',
        canActivate: [roleGuard(['ASESOR', 'ADMIN']), moduloGuard(['PIPELINE'])],
        loadComponent: () =>
          import('./features/panel/pipeline/pipeline.component').then((m) => m.PipelineComponent),
      },
      {
        path: 'cotizador',
        canActivate: [roleGuard(['ASESOR', 'ADMIN']), moduloGuard(['COTIZADOR'])],
        loadComponent: () =>
          import('./features/panel/cotizador/cotizador.component').then((m) => m.CotizadorComponent),
      },
      {
        path: 'finanzas',
        canActivate: [roleGuard(['ADMIN', 'LIDER_AREA']), moduloGuard(['FINANZAS'])],
        loadComponent: () =>
          import('./features/panel/finanzas/finanzas.component').then((m) => m.FinanzasComponent),
      },
      {
        // Ingresos ya se ven como abonos dentro de cada venta (ver VentaDetalleComponent); esta
        // sección es solo para Gastos (comisiones, renta, etc. — ver GastoService/TipoGasto).
        path: 'gastos',
        canActivate: [roleGuard(['ADMIN', 'LIDER_AREA']), moduloGuard(['GASTOS'])],
        loadComponent: () =>
          import('./features/panel/gastos/gastos-list/gastos-list.component').then((m) => m.GastosListComponent),
      },
      {
        // Catálogo de tipos de gasto: exclusivo de admin (ver TipoGastoService), por eso no aparece
        // en la nav de líder de área aunque sí pueda ver/registrar gastos.
        path: 'gastos/tipos',
        canActivate: [roleGuard(['ADMIN']), moduloGuard(['GASTOS'])],
        loadComponent: () =>
          import('./features/panel/gastos/tipos-gasto-list/tipos-gasto-list.component').then(
            (m) => m.TiposGastoListComponent,
          ),
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
        path: 'ventas',
        canActivate: [roleGuard(['ADMIN', 'LIDER_AREA']), moduloGuard(['VENTAS'])],
        loadComponent: () =>
          import('./features/panel/ventas/ventas-list/ventas-list.component').then((m) => m.VentasListComponent),
      },
      {
        path: 'ventas/nueva',
        canActivate: [roleGuard(['ADMIN', 'LIDER_AREA']), moduloGuard(['VENTAS'])],
        loadComponent: () =>
          import('./features/panel/ventas/venta-form/venta-form.component').then((m) => m.VentaFormComponent),
      },
      {
        path: 'ventas/:numero',
        canActivate: [roleGuard(['ADMIN', 'LIDER_AREA']), moduloGuard(['VENTAS'])],
        loadComponent: () =>
          import('./features/panel/ventas/venta-detalle/venta-detalle.component').then(
            (m) => m.VentaDetalleComponent,
          ),
      },
      {
        path: 'usuarios/nuevo',
        canActivate: [roleGuard(['ADMIN'])],
        loadComponent: () =>
          import('./features/usuarios/usuario-form/usuario-form.component').then((m) => m.UsuarioFormComponent),
      },
      {
        path: 'asesores-externos',
        canActivate: [roleGuard(['ADMIN', 'LIDER_AREA']), moduloGuard(['ASESORES_EXTERNOS'])],
        loadComponent: () =>
          import('./features/asesores-externos/asesores-externos-list/asesores-externos-list.component').then(
            (m) => m.AsesoresExternosListComponent,
          ),
      },
      {
        path: 'teams',
        canActivate: [roleGuard(['ADMIN', 'LIDER_AREA']), moduloGuard(['COMUNIDADES'])],
        loadComponent: () => import('./features/teams/teams.component').then((m) => m.TeamsComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
