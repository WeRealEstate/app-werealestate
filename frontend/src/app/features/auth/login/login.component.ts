import { AfterViewInit, Component, ElementRef, OnDestroy, ViewChild, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { environment } from '../../../../environments/environment';

interface LoginForm {
  email: string;
  password: string;
}

/** API mínima del script de Cloudflare Turnstile (window.turnstile), cargado en index.html. */
interface TurnstileApi {
  render(
    container: HTMLElement,
    options: {
      sitekey: string;
      theme?: 'light' | 'dark' | 'auto';
      callback?: (token: string) => void;
      'expired-callback'?: () => void;
      'error-callback'?: () => void;
    },
  ): string;
  reset(widgetId?: string): void;
  remove(widgetId?: string): void;
}

declare global {
  interface Window {
    turnstile?: TurnstileApi;
  }
}

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './login.component.html',
})
export class LoginComponent implements AfterViewInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  @ViewChild('turnstileContainer') private readonly turnstileContainer?: ElementRef<HTMLDivElement>;
  private turnstileWidgetId: string | null = null;

  readonly form = this.fb.group({
    email: this.fb.control('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    password: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
  });

  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly showPassword = signal(false);
  readonly captchaToken = signal<string | null>(null);
  readonly currentYear = new Date().getFullYear();

  ngAfterViewInit(): void {
    this.renderTurnstile();
  }

  ngOnDestroy(): void {
    if (this.turnstileWidgetId) {
      window.turnstile?.remove(this.turnstileWidgetId);
    }
  }

  /** El script de Cloudflare carga async, así que puede no estar listo todavía cuando la vista se
   * inicializa: reintenta cada 250ms (hasta 5s) en vez de fallar si window.turnstile no existe aún. */
  private renderTurnstile(intentosRestantes = 20): void {
    const turnstile = window.turnstile;
    const contenedor = this.turnstileContainer?.nativeElement;

    if (turnstile && contenedor) {
      this.turnstileWidgetId = turnstile.render(contenedor, {
        sitekey: environment.turnstileSiteKey,
        theme: 'dark',
        callback: (token) => this.captchaToken.set(token),
        'expired-callback': () => this.captchaToken.set(null),
        'error-callback': () => this.captchaToken.set(null),
      });
      return;
    }
    if (intentosRestantes <= 0) return;
    setTimeout(() => this.renderTurnstile(intentosRestantes - 1), 250);
  }

  get email() {
    return this.form.controls.email;
  }

  get password() {
    return this.form.controls.password;
  }

  togglePasswordVisibility(): void {
    this.showPassword.update((v) => !v);
  }

  async onSubmit(): Promise<void> {
    if (this.form.invalid || this.isLoading()) {
      this.form.markAllAsTouched();
      return;
    }

    const captchaToken = this.captchaToken();
    if (!captchaToken) {
      this.errorMessage.set('Completa el captcha para continuar.');
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const { email, password } = this.form.getRawValue() as LoginForm;

    try {
      const user = await this.auth.login({ email, password, captchaToken });
      await this.router.navigateByUrl(this.auth.panelRouteForRole(user.rol));
    } catch (error) {
      this.errorMessage.set(this.messageFor(error));
      // El token de Turnstile es de un solo uso: si el login falló (credenciales o captcha), hay
      // que resolverlo de nuevo antes de reintentar.
      this.captchaToken.set(null);
      if (this.turnstileWidgetId) {
        window.turnstile?.reset(this.turnstileWidgetId);
      }
    } finally {
      this.isLoading.set(false);
    }
  }

  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) return 'Correo o contraseña incorrectos.';
      if (error.status === 400) return 'Completa el captcha para continuar.';
      if (error.status === 0) return 'No se pudo conectar con el servidor. Verifica tu conexión.';
    }
    return 'Ocurrió un error al iniciar sesión. Intenta de nuevo.';
  }
}
