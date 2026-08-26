import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginRequest, LoginResponse, Role, User } from '../models/user.model';

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
