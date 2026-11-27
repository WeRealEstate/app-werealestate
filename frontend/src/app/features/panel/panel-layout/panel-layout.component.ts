import { Component, DestroyRef, HostListener, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import {
  LucideBuilding2,
  LucideCalendar,
  LucideHandCoins,
  LucideHandshake,
  LucideBadgeDollarSign,
  LucideClipboardList,
  LucideContactRound,
  LucideFolderOpen,
  LucideHouse,
  LucideTrophy,
  LucideKanban,
  LucideLandPlot,
  LucideMap,
  LucideMenu,
  LucideCalculator,
  LucideUser,
  LucideUserCog,
  LucideUsers,
  LucideUsersRound,
  LucideWallet,
  LucideTrendingUp,
  LucideLandmark,
} from '@lucide/angular';
import { VERSION_ACTUAL } from '../../../core/changelog';
import { FECHA_COMPILACION } from '../../../build-info';
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
  queryParams?: { pestana: 'comisiones' | 'ingresos' | 'valor' | 'gastos' };
  /** Si lo tiene, el item solo se muestra a quien tenga ese módulo activo (ver AuthService.tieneModulo). */
  modulo?: Modulo;
  /** Alternativa: el item también se muestra a quien tenga este otro módulo (Finanzas incluye la pestaña Gastos). */
  tambienConModulo?: Modulo;
  icon: 'home' | 'leads' | 'usuarios' | 'asesores' | 'comunidades' | 'calendario' | 'pipeline' | 'cotizador' | 'lotes' | 'plano' | 'ventas' | 'clientes' | 'finanzas' | 'ingresos' | 'valor' | 'gastos' | 'forms' | 'material' | 'comercializadoras' | 'score';
}

const ASESORES_EXTERNOS_NAV_ITEM: NavItem = {
  label: 'Asesores externos',
  route: '/panel/asesores-externos',
  modulo: 'ASESORES_EXTERNOS',
  icon: 'asesores',
};
const COMERCIALIZADORAS_NAV_ITEM: NavItem = {
  label: 'Equipos',
  route: '/panel/comercializadoras',
  modulo: 'ASESORES_EXTERNOS',
  icon: 'comercializadoras',
};
const TEAMS_NAV_ITEM: NavItem = { label: 'Comunidades We', route: '/panel/teams', modulo: 'COMUNIDADES', icon: 'comunidades' };
const WE_FORMS_NAV_ITEM: NavItem = { label: 'We Forms', route: '/panel/we-forms', modulo: 'WE_FORMS', icon: 'forms' };
const MATERIAL_NAV_ITEM: NavItem = { label: 'Material', route: '/panel/material', modulo: 'MATERIAL', icon: 'material' };
const REGISTRAR_VENTA_ITEM: NavItem = { label: 'Registrar venta', route: '/panel/registrar-venta', modulo: 'REGISTRAR_VENTA', icon: 'forms' };

/** {@code label: null} agrupa items sin encabezado visible (Inicio, siempre arriba y suelto). */
interface NavSection {
  label: string | null;
  items: NavItem[];
}

const INICIO = (route: string): NavSection => ({ label: null, items: [{ label: 'Inicio', route, icon: 'home' }] });
const LEADS_ITEM: NavItem = { label: 'Leads', route: '/panel/leads', modulo: 'LEADS', icon: 'leads' };
const PIPELINE_ITEM: NavItem = { label: 'Pipeline', route: '/panel/pipeline', modulo: 'PIPELINE', icon: 'pipeline' };
const COTIZADOR_ITEM: NavItem = { label: 'Cotizador', route: '/panel/cotizador', modulo: 'COTIZADOR', icon: 'cotizador' };
const SCORE_ITEM: NavItem = { label: 'Score de ventas', route: '/panel/score', modulo: 'SCORE', icon: 'score' };
const VENTAS_ITEM: NavItem = { label: 'Ventas', route: '/panel/ventas', modulo: 'VENTAS', icon: 'ventas' };
const CLIENTES_ITEM: NavItem = { label: 'Clientes', route: '/panel/clientes', modulo: 'CLIENTES', icon: 'clientes' };
const LOTES_ITEM: NavItem = { label: 'Lotes', route: '/panel/lotes', modulo: 'LOTES', icon: 'lotes' };
const PLANO_ITEM: NavItem = { label: 'Plano', route: '/panel/plano', modulo: 'PLANO', icon: 'plano' };
const GRUPO_FINANZAS: NavSection = { label: 'Finanzas', items: [
  { label: 'Comisiones', route: '/panel/finanzas', queryParams: { pestana: 'comisiones' }, modulo: 'FINANZAS', icon: 'finanzas' },
  { label: 'Ingresos', route: '/panel/finanzas', queryParams: { pestana: 'ingresos' }, modulo: 'FINANZAS', icon: 'ingresos' },
  { label: 'Valor vendido', route: '/panel/finanzas', queryParams: { pestana: 'valor' }, modulo: 'FINANZAS', icon: 'valor' },
  { label: 'Gastos', route: '/panel/finanzas', queryParams: { pestana: 'gastos' }, modulo: 'GASTOS', icon: 'gastos' },
] };
const CALENDARIO_ITEM: NavItem = { label: 'Calendario', route: '/panel/calendario', modulo: 'CALENDARIO', icon: 'calendario' };

/** Grupos desplegables: Ventas, Inventario, Usuarios y Administración; solo Inicio va suelto. */
const GRUPO_VENTAS_ASESOR: NavSection = { label: 'Ventas', items: [LEADS_ITEM, PIPELINE_ITEM, COTIZADOR_ITEM, REGISTRAR_VENTA_ITEM] };
const GRUPO_VENTAS_ADMIN: NavSection = {
  label: 'Ventas',
  items: [LEADS_ITEM, PIPELINE_ITEM, COTIZADOR_ITEM, SCORE_ITEM, VENTAS_ITEM, CLIENTES_ITEM],
};
const GRUPO_INVENTARIO: NavSection = { label: 'Inventario', items: [LOTES_ITEM, PLANO_ITEM] };
const USUARIOS_ITEM: NavItem = { label: 'Usuarios', route: '/panel/usuarios', icon: 'usuarios' };
const ITEMS_DE_PERSONAS: NavItem[] = [ASESORES_EXTERNOS_NAV_ITEM, COMERCIALIZADORAS_NAV_ITEM, TEAMS_NAV_ITEM, WE_FORMS_NAV_ITEM];
const GRUPO_ADMINISTRACION: NavSection = { label: 'Administración', items: [MATERIAL_NAV_ITEM, CALENDARIO_ITEM] };
/** Asesores y equipo interno: del grupo solo les toca el Calendario. */
const GRUPO_ADMINISTRACION_SOLO_CALENDARIO: NavSection = { label: 'Administración', items: [CALENDARIO_ITEM] };

const NAV_BY_ROLE: Record<Role, NavSection[]> = {
  ASESOR: [INICIO('/panel/asesor'), GRUPO_VENTAS_ASESOR, GRUPO_INVENTARIO, GRUPO_ADMINISTRACION_SOLO_CALENDARIO],
  LIDER_AREA: [
    INICIO('/panel/equipo'),
    // Administración no ve Pipeline ni Cotizador en el menú (como antes).
    { label: 'Ventas', items: [LEADS_ITEM, SCORE_ITEM, VENTAS_ITEM, CLIENTES_ITEM] },
    GRUPO_INVENTARIO,
    // La pantalla de Usuarios es solo del administrador.
    { label: 'Usuarios', items: ITEMS_DE_PERSONAS },
    GRUPO_FINANZAS,
    GRUPO_ADMINISTRACION,
  ],
  EQUIPO_INTERNO: [INICIO('/panel/equipo'), GRUPO_ADMINISTRACION_SOLO_CALENDARIO],
  ADMIN: [
    INICIO('/panel/admin'),
    GRUPO_VENTAS_ADMIN,
    GRUPO_INVENTARIO,
    { label: 'Usuarios', items: [USUARIOS_ITEM, ...ITEMS_DE_PERSONAS] },
    GRUPO_FINANZAS,
    GRUPO_ADMINISTRACION,
  ],
};

const CLAVE_SECCIONES_PLEGADAS = 'we_nav_secciones_plegadas';

function leerSeccionesPlegadas(): ReadonlySet<string> {
  try {
    const guardado: unknown = JSON.parse(localStorage.getItem(CLAVE_SECCIONES_PLEGADAS) ?? '[]');
    return new Set(Array.isArray(guardado) ? guardado.filter((x): x is string => typeof x === 'string') : []);
  } catch {
    return new Set();
  }
}

@Component({
  selector: 'app-panel-layout',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    ThemeToggleComponent,
    ToastContainerComponent,
    ConfirmDialogComponent,    NotificationBellComponent,
    LucideHouse,
    LucideTrophy,
    LucideUsers,
    LucideCalendar,
    LucideHandCoins,
    LucideKanban,
    LucideCalculator,
    LucideLandPlot,
    LucideMap,
    LucideBadgeDollarSign,
    LucideContactRound,
    LucideBuilding2,
    LucideClipboardList,
    LucideFolderOpen,
    LucideUser,
    LucideUserCog,
    LucideUsersRound,
    LucideHandshake,
    LucideMenu,
    LucideWallet,
    LucideTrendingUp,
    LucideLandmark,
  ],
  templateUrl: './panel-layout.component.html',
})
export class PanelLayoutComponent {
  private readonly auth = inject(AuthService);
  private readonly push = inject(PushService);
  private readonly router = inject(Router);

  /** El enlace histórico sin parámetro abre la primera sección permitida. */
  esComisionesPredeterminadas(item: NavItem): boolean {
    const predeterminada = this.auth.tieneModulo('FINANZAS') ? 'comisiones' : 'gastos';
    if (item.queryParams?.pestana !== predeterminada) return false;
    const url = this.router.parseUrl(this.router.url);
    return url.root.children['primary']?.segments.map((s) => s.path).join('/') === 'panel/finanzas'
      && !url.queryParams['pestana'];
  }

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

  /** Secciones del menú que el usuario plegó (varias pueden estar abiertas a la vez; por defecto todas lo están).
   * Se recuerdan en este navegador para que sigan igual al recargar. */
  private readonly seccionesPlegadas = signal<ReadonlySet<string>>(leerSeccionesPlegadas());

  readonly sidebarOpen = signal(false);
  /** Versión de la app (la más alta del historial, core/changelog.ts) y fecha en que se compiló. */
  readonly versionApp =`v${VERSION_ACTUAL} · ${FECHA_COMPILACION.split('-').reverse().join('/')}`;
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
      const alCambiar = (e: MediaQueryListEvent) => this.esEscritorio.set(e.matches);
      consulta.addEventListener('change', alCambiar);
      inject(DestroyRef).onDestroy(() => consulta.removeEventListener('change', alCambiar));
    }
  }

  seccionAbierta(etiqueta: string): boolean {
    return !this.seccionesPlegadas().has(etiqueta);
  }

  alternarSeccion(etiqueta: string): void {
    const nuevas = new Set(this.seccionesPlegadas());
    if (!nuevas.delete(etiqueta)) nuevas.add(etiqueta);
    this.seccionesPlegadas.set(nuevas);
    try {
      localStorage.setItem(CLAVE_SECCIONES_PLEGADAS, JSON.stringify([...nuevas]));
    } catch {
      // Sin almacenamiento (ventana privada o bloqueado): el menú funciona igual, solo no recuerda el estado.
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
