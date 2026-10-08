import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Evento no estándar de Chrome/Android para ofrecer instalar la PWA. */
interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

function aUint8Array(base64Url: string): Uint8Array<ArrayBuffer> {
  const relleno = '='.repeat((4 - (base64Url.length % 4)) % 4);
  const base64 = (base64Url + relleno).replace(/-/g, '+').replace(/_/g, '/');
  const bruto = atob(base64);
  const salida = new Uint8Array(new ArrayBuffer(bruto.length));
  for (let i = 0; i < bruto.length; i++) salida[i] = bruto.charCodeAt(i);
  return salida;
}

function aBase64Url(buffer: ArrayBuffer | null): string {
  if (!buffer) return '';
  let texto = '';
  new Uint8Array(buffer).forEach((b) => (texto += String.fromCharCode(b)));
  return btoa(texto).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/**
 * Instalación de la app (PWA) y notificaciones push del teléfono. En iPhone el push solo existe con la
 * app instalada en la pantalla de inicio (iOS 16.4+); en Android funciona también desde el navegador.
 */
@Injectable({ providedIn: 'root' })
export class PushService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/push`;
  private instalacionPendiente: BeforeInstallPromptEvent | null = null;

  /** El navegador sabe hacer push (en iPhone, solo con la app instalada). */
  readonly soportado =
    typeof window !== 'undefined' && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
  readonly esIos =
    typeof navigator !== 'undefined' &&
    (/iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1));
  readonly instalada =
    typeof window !== 'undefined' &&
    (window.matchMedia('(display-mode: standalone)').matches || (navigator as unknown as { standalone?: boolean }).standalone === true);

  readonly permiso = signal<NotificationPermission>(this.soportado ? Notification.permission : 'denied');
  readonly suscrito = signal(false);
  readonly puedeInstalar = signal(false);
  readonly trabajando = signal(false);
  readonly error = signal<string | null>(null);

  /** iPhone sin la app instalada: no puede recibir push, hay que instalarla primero. */
  readonly iosSinInstalar = computed(() => this.esIos && !this.instalada);

  constructor() {
    if (typeof window === 'undefined') return;
    window.addEventListener('beforeinstallprompt', (e) => {
      e.preventDefault();
      this.instalacionPendiente = e as BeforeInstallPromptEvent;
      this.puedeInstalar.set(true);
    });
    window.addEventListener('appinstalled', () => {
      this.instalacionPendiente = null;
      this.puedeInstalar.set(false);
    });
  }

  async instalar(): Promise<void> {
    const evento = this.instalacionPendiente;
    if (!evento) return;
    await evento.prompt();
    await evento.userChoice;
    this.instalacionPendiente = null;
    this.puedeInstalar.set(false);
  }

  /** Al abrir el panel: si este dispositivo ya tiene permiso y suscripción, la reasocia al usuario actual. */
  async sincronizar(): Promise<void> {
    if (!this.soportado) return;
    this.permiso.set(Notification.permission);
    try {
      const registro = await navigator.serviceWorker.ready;
      const sub = await registro.pushManager.getSubscription();
      if (sub && Notification.permission === 'granted') {
        await this.enviarSuscripcion(sub);
        this.suscrito.set(true);
      } else {
        this.suscrito.set(false);
      }
    } catch {
      this.suscrito.set(false);
    }
  }

  async activar(): Promise<void> {
    if (!this.soportado || this.trabajando()) return;
    this.trabajando.set(true);
    this.error.set(null);
    try {
      const permiso = await Notification.requestPermission();
      this.permiso.set(permiso);
      if (permiso !== 'granted') {
        this.error.set('No se concedió el permiso de notificaciones.');
        return;
      }
      const registro = await navigator.serviceWorker.ready;
      let sub = await registro.pushManager.getSubscription();
      if (!sub) {
        const { clave } = await firstValueFrom(this.http.get<{ clave: string }>(`${this.baseUrl}/clave-publica`));
        sub = await registro.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: aUint8Array(clave) });
      }
      await this.enviarSuscripcion(sub);
      this.suscrito.set(true);
    } catch {
      this.error.set('No se pudieron activar los avisos en este dispositivo.');
    } finally {
      this.trabajando.set(false);
    }
  }

  async desactivar(): Promise<void> {
    if (!this.soportado || this.trabajando()) return;
    this.trabajando.set(true);
    this.error.set(null);
    try {
      const registro = await navigator.serviceWorker.ready;
      const sub = await registro.pushManager.getSubscription();
      if (sub) {
        await firstValueFrom(this.http.post(`${this.baseUrl}/desuscribir`, { endpoint: sub.endpoint }));
        await sub.unsubscribe();
      }
      this.suscrito.set(false);
    } catch {
      this.error.set('No se pudieron desactivar los avisos.');
    } finally {
      this.trabajando.set(false);
    }
  }

  /** Al cerrar sesión: este dispositivo deja de recibir los avisos de ese usuario (la suscripción del
   * navegador se conserva, así que el siguiente usuario la reactiva sin volver a pedir permiso). */
  async alCerrarSesion(): Promise<void> {
    if (!this.soportado) return;
    try {
      const registro = await navigator.serviceWorker.ready;
      const sub = await registro.pushManager.getSubscription();
      if (sub) await firstValueFrom(this.http.post(`${this.baseUrl}/desuscribir`, { endpoint: sub.endpoint }));
    } catch {
      // sin red: el servidor la limpia cuando el navegador la rechace
    }
  }

  private enviarSuscripcion(sub: PushSubscription): Promise<unknown> {
    return firstValueFrom(
      this.http.post(`${this.baseUrl}/suscribir`, {
        endpoint: sub.endpoint,
        p256dh: aBase64Url(sub.getKey('p256dh')),
        auth: aBase64Url(sub.getKey('auth')),
      }),
    );
  }
}
