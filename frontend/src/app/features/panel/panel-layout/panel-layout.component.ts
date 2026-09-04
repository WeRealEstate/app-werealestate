import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ROLE_LABELS, Role } from '../../../core/models/user.model';
import { ThemeService } from '../../../core/services/theme.service';
import { NotificationBellComponent } from '../../../shared/notification-bell/notification-bell.component';
import { ThemeToggleComponent } from '../../../shared/theme-toggle/theme-toggle.component';
import { ToastContainerComponent } from '../../../shared/toast-container/toast-container.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';

interface NavItem {
  label: string;
  route: string;
  icon: 'home' | 'leads' | 'usuarios' | 'calendario' | 'comisiones' | 'tarjetas' | 'cotizador';
}

const NAV_BY_ROLE: Record<Role, NavItem[]> = {
  ASESOR: [
    { label: 'Inicio', route: '/panel/asesor', icon: 'home' },
    { label: 'Leads', route: '/panel/leads', icon: 'leads' },
    { label: 'Tarjetas', route: '/panel/tarjetas', icon: 'tarjetas' },
    { label: 'Calendario', route: '/panel/calendario', icon: 'calendario' },
    { label: 'Comisiones', route: '/panel/comisiones', icon: 'comisiones' },
    { label: 'Cotizador', route: '/panel/cotizador', icon: 'cotizador' },
  ],
  LIDER_AREA: [
    { label: 'Inicio', route: '/panel/equipo', icon: 'home' },
    { label: 'Leads', route: '/panel/leads', icon: 'leads' },
    { label: 'Calendario', route: '/panel/calendario', icon: 'calendario' },
  ],
  EQUIPO_INTERNO: [
    { label: 'Inicio', route: '/panel/equipo', icon: 'home' },
    { label: 'Calendario', route: '/panel/calendario', icon: 'calendario' },
  ],
  ADMIN: [
    { label: 'Inicio', route: '/panel/admin', icon: 'home' },
    { label: 'Leads', route: '/panel/leads', icon: 'leads' },
    { label: 'Tarjetas', route: '/panel/tarjetas', icon: 'tarjetas' },
    { label: 'Usuarios', route: '/panel/usuarios', icon: 'usuarios' },
    { label: 'Calendario', route: '/panel/calendario', icon: 'calendario' },
    { label: 'Comisiones', route: '/panel/comisiones', icon: 'comisiones' },
    { label: 'Cotizador', route: '/panel/cotizador', icon: 'cotizador' },
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
  private readonly themeService = inject(ThemeService);

  readonly user = this.auth.currentUser;
  readonly roleLabel = computed(() => {
    const rol = this.user()?.rol;
    return rol ? ROLE_LABELS[rol] : '';
  });
  readonly themeLabel = computed(() => (this.themeService.theme() === 'dark' ? 'Modo oscuro' : 'Modo claro'));
  readonly navItems = computed<NavItem[]>(() => {
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
