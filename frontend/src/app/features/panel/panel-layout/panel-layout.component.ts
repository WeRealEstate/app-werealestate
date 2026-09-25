import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ROLE_LABELS, Role } from '../../../core/models/user.model';
import { NotificationBellComponent } from '../../../shared/notification-bell/notification-bell.component';
import { ThemeToggleComponent } from '../../../shared/theme-toggle/theme-toggle.component';
import { ToastContainerComponent } from '../../../shared/toast-container/toast-container.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';

interface NavItem {
  label: string;
  route: string;
  icon: 'home' | 'leads' | 'usuarios' | 'calendario' | 'pipeline' | 'cotizador' | 'lotes' | 'plano' | 'ventas';
}

const ASESORES_EXTERNOS_NAV_ITEM: NavItem = { label: 'Asesores externos', route: '/panel/asesores-externos', icon: 'usuarios' };

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
        { label: 'Leads', route: '/panel/leads', icon: 'leads' },
        { label: 'Pipeline', route: '/panel/pipeline', icon: 'pipeline' },
        { label: 'Cotizador', route: '/panel/cotizador', icon: 'cotizador' },
      ],
    },
    {
      label: 'Inventario',
      items: [
        { label: 'Lotes', route: '/panel/lotes', icon: 'lotes' },
        { label: 'Plano', route: '/panel/plano', icon: 'plano' },
      ],
    },
    { label: 'Administración', items: [{ label: 'Calendario', route: '/panel/calendario', icon: 'calendario' }] },
  ],
  LIDER_AREA: [
    { label: null, items: [{ label: 'Inicio', route: '/panel/equipo', icon: 'home' }] },
    { label: 'Desarrollos', items: [{ label: 'Leads', route: '/panel/leads', icon: 'leads' }] },
    {
      label: 'Inventario',
      items: [
        { label: 'Lotes', route: '/panel/lotes', icon: 'lotes' },
        { label: 'Ventas', route: '/panel/ventas', icon: 'ventas' },
      ],
    },
    { label: 'Administración', items: [{ label: 'Calendario', route: '/panel/calendario', icon: 'calendario' }] },
  ],
  EQUIPO_INTERNO: [
    { label: null, items: [{ label: 'Inicio', route: '/panel/equipo', icon: 'home' }] },
    { label: 'Administración', items: [{ label: 'Calendario', route: '/panel/calendario', icon: 'calendario' }] },
  ],
  ADMIN: [
    { label: null, items: [{ label: 'Inicio', route: '/panel/admin', icon: 'home' }] },
    {
      label: 'Desarrollos',
      items: [
        { label: 'Leads', route: '/panel/leads', icon: 'leads' },
        { label: 'Pipeline', route: '/panel/pipeline', icon: 'pipeline' },
        { label: 'Cotizador', route: '/panel/cotizador', icon: 'cotizador' },
      ],
    },
    {
      label: 'Inventario',
      items: [
        { label: 'Lotes', route: '/panel/lotes', icon: 'lotes' },
        { label: 'Plano', route: '/panel/plano', icon: 'plano' },
        { label: 'Ventas', route: '/panel/ventas', icon: 'ventas' },
      ],
    },
    {
      label: 'Administración',
      items: [
        { label: 'Usuarios', route: '/panel/usuarios', icon: 'usuarios' },
        ASESORES_EXTERNOS_NAV_ITEM,
        { label: 'Calendario', route: '/panel/calendario', icon: 'calendario' },
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
  readonly navSections = computed<NavSection[]>(() => {
    const rol = this.user()?.rol;
    return rol ? NAV_BY_ROLE[rol] : [];
  });

  readonly sidebarOpen = signal(false);

  toggleSidebar(): void {
    this.sidebarOpen.update((v) => !v);
  }

  closeSidebar(): void {
    this.sidebarOpen.set(false);
  }

  logout(): void {
    this.auth.logout();
  }
}
