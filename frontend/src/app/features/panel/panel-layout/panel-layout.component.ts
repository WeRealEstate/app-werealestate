import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import {
  LucideCalendar,
  LucideChartLine,
  LucideHouse,
  LucideKanban,
  LucideLandPlot,
  LucideMap,
  LucideMenu,
  LucideReceipt,
  LucideUser,
  LucideUsers,
  LucideWallet,
} from '@lucide/angular';
import { AuthService } from '../../../core/services/auth.service';
import { Modulo, ROLE_LABELS, Role } from '../../../core/models/user.model';
import { NotificationBellComponent } from '../../../shared/notification-bell/notification-bell.component';
import { ThemeToggleComponent } from '../../../shared/theme-toggle/theme-toggle.component';
import { ToastContainerComponent } from '../../../shared/toast-container/toast-container.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';

interface NavItem {
  label: string;
  route: string;
  /** Si lo tiene, el item solo se muestra a quien tenga ese módulo activo (ver AuthService.tieneModulo). */
  modulo?: Modulo;
  icon: 'home' | 'leads' | 'usuarios' | 'calendario' | 'pipeline' | 'cotizador' | 'lotes' | 'plano' | 'ventas' | 'gastos';
}

const ASESORES_EXTERNOS_NAV_ITEM: NavItem = { label: 'Asesores externos', route: '/panel/asesores-externos', icon: 'usuarios' };
const TEAMS_NAV_ITEM: NavItem = { label: 'Comunidades We', route: '/panel/teams', icon: 'usuarios' };

/** {@code label: null} agrupa items sin encabezado visible (Inicio, siempre arriba y suelto). */
interface NavSection {
  label: string | null;
  items: NavItem[];
}

const NAV_BY_ROLE: Record<Role, NavSection[]> = {
  ASESOR: [
    { label: null, items: [{ label: 'Inicio', route: '/panel/asesor', icon: 'home' }] },
    {
      label: 'Desarrollos',
      items: [
        { label: 'Leads', route: '/panel/leads', modulo: 'LEADS', icon: 'leads' },
        { label: 'Pipeline', route: '/panel/pipeline', modulo: 'PIPELINE', icon: 'pipeline' },
        { label: 'Cotizador', route: '/panel/cotizador', modulo: 'COTIZADOR', icon: 'cotizador' },
      ],
    },
    {
      label: 'Inventario',
      items: [
        { label: 'Lotes', route: '/panel/lotes', modulo: 'LOTES', icon: 'lotes' },
        { label: 'Plano', route: '/panel/plano', modulo: 'PLANO', icon: 'plano' },
      ],
    },
    { label: 'Administración', items: [{ label: 'Calendario', route: '/panel/calendario', modulo: 'CALENDARIO', icon: 'calendario' }] },
  ],
  LIDER_AREA: [
    { label: null, items: [{ label: 'Inicio', route: '/panel/equipo', icon: 'home' }] },
    { label: 'Desarrollos', items: [{ label: 'Leads', route: '/panel/leads', modulo: 'LEADS', icon: 'leads' }] },
    {
      label: 'Inventario',
      items: [
        { label: 'Lotes', route: '/panel/lotes', modulo: 'LOTES', icon: 'lotes' },
        { label: 'Ventas', route: '/panel/ventas', modulo: 'VENTAS', icon: 'ventas' },
        { label: 'Gastos', route: '/panel/gastos', modulo: 'GASTOS', icon: 'gastos' },
      ],
    },
    { label: 'Administración', items: [{ label: 'Calendario', route: '/panel/calendario', modulo: 'CALENDARIO', icon: 'calendario' }] },
  ],
  EQUIPO_INTERNO: [
    { label: null, items: [{ label: 'Inicio', route: '/panel/equipo', icon: 'home' }] },
    { label: 'Administración', items: [{ label: 'Calendario', route: '/panel/calendario', modulo: 'CALENDARIO', icon: 'calendario' }] },
  ],
  ADMIN: [
    { label: null, items: [{ label: 'Inicio', route: '/panel/admin', icon: 'home' }] },
    {
      label: 'Desarrollos',
      items: [
        { label: 'Leads', route: '/panel/leads', modulo: 'LEADS', icon: 'leads' },
        { label: 'Pipeline', route: '/panel/pipeline', modulo: 'PIPELINE', icon: 'pipeline' },
        { label: 'Cotizador', route: '/panel/cotizador', modulo: 'COTIZADOR', icon: 'cotizador' },
      ],
    },
    {
      label: 'Inventario',
      items: [
        { label: 'Lotes', route: '/panel/lotes', modulo: 'LOTES', icon: 'lotes' },
        { label: 'Plano', route: '/panel/plano', modulo: 'PLANO', icon: 'plano' },
        { label: 'Ventas', route: '/panel/ventas', modulo: 'VENTAS', icon: 'ventas' },
        { label: 'Gastos', route: '/panel/gastos', modulo: 'GASTOS', icon: 'gastos' },
      ],
    },
    {
      label: 'Administración',
      items: [
        { label: 'Usuarios', route: '/panel/usuarios', icon: 'usuarios' },
        ASESORES_EXTERNOS_NAV_ITEM,
        TEAMS_NAV_ITEM,
        { label: 'Calendario', route: '/panel/calendario', modulo: 'CALENDARIO', icon: 'calendario' },
      ],
    },
  ],
};

@Component({
  selector: 'app-panel-layout',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    ThemeToggleComponent,
    ToastContainerComponent,
    ConfirmDialogComponent,
    NotificationBellComponent,
    LucideHouse,
    LucideUsers,
    LucideCalendar,
    LucideKanban,
    LucideReceipt,
    LucideLandPlot,
    LucideMap,
    LucideChartLine,
    LucideUser,
    LucideMenu,
    LucideWallet,
  ],
  templateUrl: './panel-layout.component.html',
})
export class PanelLayoutComponent {
  private readonly auth = inject(AuthService);

  readonly user = this.auth.currentUser;
  readonly roleLabel = computed(() => {
    const rol = this.user()?.rol;
    return rol ? ROLE_LABELS[rol] : '';
  });
  /** El menú de su rol, sin los módulos que un admin le quitó (ver AuthService.tieneModulo); una
   * sección que se queda sin items desaparece con su encabezado. */
  readonly navSections = computed<NavSection[]>(() => {
    const user = this.user();
    if (!user) return [];
    return NAV_BY_ROLE[user.rol]
      .map((seccion) => ({
        ...seccion,
        items: seccion.items.filter((item) => !item.modulo || user.rol === 'ADMIN' || !user.modulos || user.modulos.includes(item.modulo)),
      }))
      .filter((seccion) => seccion.items.length > 0);
  });

  readonly sidebarOpen = signal(false);

  constructor() {
    // Los módulos pueden haber cambiado desde el último login (un admin se los quitó o dio): se
    // vuelven a pedir al abrir el panel para que el menú y las rutas reflejen lo de hoy.
    void this.auth.refrescarPerfil();
  }

  toggleSidebar(): void {
    this.sidebarOpen.update((v) => !v);
  }

  closeSidebar(): void {
    this.sidebarOpen.set(false);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.sidebarOpen()) this.closeSidebar();
  }

  logout(): void {
    this.auth.logout();
  }
}
