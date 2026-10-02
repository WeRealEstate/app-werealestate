import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginRequest, LoginResponse, Modulo, Role, User } from '../models/user.model';

const TOKEN_KEY = 'we_auth_token';
const USER_KEY = 'we_auth_user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly currentUserSignal = signal<User | null>(this.readStoredUser());
  private readonly tokenSignal = signal<string | null>(localStorage.getItem(TOKEN_KEY));

  readonly currentUser = this.currentUserSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.tokenSignal() !== null && this.currentUserSignal() !== null);

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router,
  ) {}

  get token(): string | null {
    return this.tokenSignal();
  }

  async login(credentials: LoginRequest): Promise<User> {
    const response = await firstValueFrom(
      this.http.post<LoginResponse>(`${environment.apiUrl}/auth/login`, credentials),
    );

    this.tokenSignal.set(response.token);
    this.currentUserSignal.set(response.user);
    localStorage.setItem(TOKEN_KEY, response.token);
    localStorage.setItem(USER_KEY, JSON.stringify(response.user));

    return response.user;
  }

  /** true si el usuario puede usar ese módulo. Un admin siempre; mientras la sesión no traiga la lista
   * (login anterior a esta función) se deja pasar y el backend sigue siendo quien manda — el panel la
   * refresca al abrirse (ver refrescarPerfil). */
  tieneModulo(modulo: Modulo): boolean {
    const user = this.currentUserSignal();
    if (!user) return false;
    if (user.rol === 'ADMIN' || !user.modulos) return true;
    return user.modulos.includes(modulo);
  }

  tieneAlgunModulo(modulos: Modulo[]): boolean {
    return modulos.some((m) => this.tieneModulo(m));
  }

  /** Vuelve a pedir el usuario al servidor para que un cambio de módulos hecho por un admin se vea
   * sin tener que cerrar sesión. Si falla (sin red), se queda con lo que ya había. */
  async refrescarPerfil(): Promise<void> {
    if (!this.tokenSignal()) return;
    try {
      const fresco = await firstValueFrom(this.http.get<User>(`${environment.apiUrl}/usuarios/me`));
      this.currentUserSignal.set(fresco);
      localStorage.setItem(USER_KEY, JSON.stringify(fresco));
    } catch {
      // Una caída de red no debe sacar al usuario ni dejarlo sin menú.
    }
  }

  logout(): void {
    this.tokenSignal.set(null);
    this.currentUserSignal.set(null);
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.router.navigate(['/login']);
  }

  /** Ruta del panel correspondiente al rol del usuario autenticado. */
  panelRouteForRole(rol: Role): string {
    switch (rol) {
      case 'ASESOR':
        return '/panel/asesor';
      case 'LIDER_AREA':
      case 'EQUIPO_INTERNO':
        return '/panel/equipo';
      case 'ADMIN':
        return '/panel/admin';
    }
  }

  private readStoredUser(): User | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as User;
    } catch {
      return null;
    }
  }
}
