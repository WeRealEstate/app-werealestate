import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import {
  LucideCalendar,
  LucideHandCoins,
  LucideChartLine,
  LucideContactRound,
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
import { PushService } from '../../../core/services/push.service';
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
  /** Alternativa: el item también se muestra a quien tenga este otro módulo (Finanzas incluye la pestaña Gastos). */
  tambienConModulo?: Modulo;
  icon: 'home' | 'leads' | 'usuarios' | 'calendario' | 'pipeline' | 'cotizador' | 'lotes' | 'plano' | 'ventas' | 'clientes' | 'finanzas' | 'gastos';
}

const ASESORES_EXTERNOS_NAV_ITEM: NavItem = {
  label: 'Asesores externos',
  route: '/panel/asesores-externos',
  modulo: 'ASESORES_EXTERNOS',
  icon: 'usuarios',
};
const TEAMS_NAV_ITEM: NavItem = { label: 'Comunidades We', route: '/panel/teams', modulo: 'COMUNIDADES', icon: 'usuarios' };

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
        { label: 'Plano', route: '/panel/plano', modulo: 'PLANO', icon: 'plano' },
        { label: 'Ventas', route: '/panel/ventas', modulo: 'VENTAS', icon: 'ventas' },
        { label: 'Clientes', route: '/panel/clientes', modulo: 'CLIENTES', icon: 'clientes' },
        { label: 'Finanzas', route: '/panel/finanzas', modulo: 'FINANZAS', tambienConModulo: 'GASTOS', icon: 'finanzas' },
      ],
    },
    {
      label: 'Administración',
      items: [
        ASESORES_EXTERNOS_NAV_ITEM,
        TEAMS_NAV_ITEM,
        { label: 'Calendario', route: '/panel/calendario', modulo: 'CALENDARIO', icon: 'calendario' },
      ],
    },
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
        { label: 'Clientes', route: '/panel/clientes', modulo: 'CLIENTES', icon: 'clientes' },
        { label: 'Finanzas', route: '/panel/finanzas', modulo: 'FINANZAS', tambienConModulo: 'GASTOS', icon: 'finanzas' },
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
    LucideHandCoins,
    LucideKanban,
    LucideReceipt,
    LucideLandPlot,
    LucideMap,
    LucideChartLine,
    LucideContactRound,
    LucideUser,
    LucideMenu,
    LucideWallet,
  ],
  templateUrl: './panel-layout.component.html',
})
export class PanelLayoutComponent {
  private readonly auth = inject(AuthService);
  private readonly push = inject(PushService);

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
        items: seccion.items.filter(
          (item) =>
            !item.modulo ||
            user.rol === 'ADMIN' ||
            !user.modulos ||
            user.modulos.includes(item.modulo) ||
            (item.tambienConModulo !== undefined && user.modulos.includes(item.tambienConModulo)),
        ),
      }))
      .filter((seccion) => seccion.items.length > 0);
  });

  readonly sidebarOpen = signal(false);
  /** Pantalla ancha (sidebar fijo): ahí la campana va en el sidebar; en el teléfono, en la barra superior. */
  readonly esEscritorio = signal(typeof window !== 'undefined' && window.matchMedia('(min-width: 1024px)').matches);

  constructor() {
    // Sin fondo de página propio a propósito: en Android la barra de navegación del sistema toma el
    // color de fondo de la página, y debe verse igual que el final del contenido (claro/oscuro del
    // tema), no azul de marca.
    // Los módulos pueden haber cambiado desde el último login (un admin se los quitó o dio): se
    // vuelven a pedir al abrir el panel para que el menú y las rutas reflejen lo de hoy.
    void this.auth.refrescarPerfil();
    if (typeof window !== 'undefined') {
      const consulta = window.matchMedia('(min-width: 1024px)');
      consulta.addEventListener('change', (e) => this.esEscritorio.set(e.matches));
    }
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

  async logout(): Promise<void> {
    // Este dispositivo deja de recibir los avisos push de este usuario.
    await this.push.alCerrarSesion();
    this.auth.logout();
  }
}
