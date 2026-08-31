import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ROLE_LABELS, Role } from '../../../core/models/user.model';
import { ThemeService } from '../../../core/services/theme.service';
import { ThemeToggleComponent } from '../../../shared/theme-toggle/theme-toggle.component';

interface NavItem {
  label: string;
  route: string;
  icon: 'home' | 'leads' | 'usuarios';
}

const NAV_BY_ROLE: Record<Role, NavItem[]> = {
  ASESOR: [
    { label: 'Inicio', route: '/panel/asesor', icon: 'home' },
    { label: 'Leads', route: '/panel/leads', icon: 'leads' },
  ],
  LIDER_AREA: [
    { label: 'Inicio', route: '/panel/equipo', icon: 'home' },
    { label: 'Leads', route: '/panel/leads', icon: 'leads' },
  ],
  EQUIPO_INTERNO: [{ label: 'Inicio', route: '/panel/equipo', icon: 'home' }],
  ADMIN: [
    { label: 'Inicio', route: '/panel/admin', icon: 'home' },
    { label: 'Leads', route: '/panel/leads', icon: 'leads' },
    { label: 'Usuarios', route: '/panel/usuarios', icon: 'usuarios' },
  ],
};

@Component({
  selector: 'app-panel-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ThemeToggleComponent],
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
