import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ComercializadoraPublica, UnirseResultado } from '../../../core/models/comercializadora.model';
import { ComercializadorasService } from '../../../core/services/comercializadoras.service';

/** /unirse/:token: el enlace que la líder de una comercializadora comparte con su equipo. Cada integrante escribe
 * sus datos, entra directo al equipo de la líder (Comunidades We) y ve su PIN, que usa en los planos públicos y en
 * Registrar venta. Sin sesión: el token del enlace es el único permiso. */
@Component({
  selector: 'app-comercializadora-unirse',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './comercializadora-unirse.component.html',
})
export class ComercializadoraUnirseComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(ComercializadorasService);

  private readonly token = this.route.snapshot.paramMap.get('token') ?? '';

  readonly info = signal<ComercializadoraPublica | null>(null);
  readonly cargando = signal(true);
  readonly enlaceInvalido = signal(false);

  readonly nombre = signal('');
  readonly celular = signal('');
  readonly correo = signal('');
  /** PIN que elige el integrante (opcional: vacío = el sistema le asigna uno) y su repetición. */
  readonly pin = signal('');
  readonly pinRepetido = signal('');
  readonly enviando = signal(false);
  readonly error = signal<string | null>(null);

  readonly resultado = signal<UnirseResultado | null>(null);
  readonly pinCopiado = signal(false);

  constructor() {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    try {
      this.info.set(await this.service.infoPublica(this.token));
    } catch {
      this.enlaceInvalido.set(true);
    } finally {
      this.cargando.set(false);
    }
  }

  async enviar(): Promise<void> {
    if (this.enviando()) return;
    if (this.nombre().trim().length < 3) {
      this.error.set('Escribe tu nombre completo.');
      return;
    }
    if (this.celular().replace(/\D/g, '').length !== 10) {
      this.error.set('El celular debe tener 10 dígitos.');
      return;
    }
    const pin = this.pin().trim();
    if (pin || this.pinRepetido().trim()) {
      if (!/^[A-Za-z0-9]{4}$/.test(pin)) {
        this.error.set('Tu PIN debe tener exactamente 4 letras o números (sin espacios ni símbolos).');
        return;
      }
      if (pin.toUpperCase() !== this.pinRepetido().trim().toUpperCase()) {
        this.error.set('Los dos PIN no coinciden. Escríbelos de nuevo.');
        return;
      }
    }
    this.enviando.set(true);
    this.error.set(null);
    try {
      this.resultado.set(
        await this.service.unirse(this.token, {
          nombre: this.nombre().trim(),
          celular: this.celular().trim(),
          correo: this.correo().trim(),
          pin,
        }),
      );
    } catch (e) {
      const estado = e instanceof HttpErrorResponse ? e.status : 0;
      if (estado === 404) {
        this.enlaceInvalido.set(true);
      } else {
        this.error.set(
          e instanceof HttpErrorResponse && typeof e.error?.message === 'string'
            ? e.error.message
            : 'No se pudo completar tu registro. Intenta de nuevo.',
        );
      }
    } finally {
      this.enviando.set(false);
    }
  }

  async copiarPin(): Promise<void> {
    const r = this.resultado();
    if (!r) return;
    try {
      await navigator.clipboard.writeText(r.pin);
      this.pinCopiado.set(true);
      setTimeout(() => this.pinCopiado.set(false), 2500);
    } catch {
      // sin portapapeles: el PIN sigue a la vista para anotarlo
    }
  }
}
