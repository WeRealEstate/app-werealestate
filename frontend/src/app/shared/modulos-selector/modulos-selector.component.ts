import { Component, computed, effect, inject, input, model, signal, untracked } from '@angular/core';
import { Modulo, ModuloCatalogo, Role } from '../../core/models/user.model';
import { UsuariosService } from '../../core/services/usuarios.service';
import { WeLoaderComponent } from '../we-loader/we-loader.component';

/** Casillas de qué módulos puede usar un usuario, según su rol: solo muestra los que ese rol puede
 * llegar a tener (el catálogo viene del backend, ver ModulosAcceso). Un admin siempre tiene todo.
 *
 * `valor` es el valor de dos vías con la selección. Al cambiar `rol` (no al crearse) la selección se
 * reinicia a la de ese rol por defecto — igual que el backend al cambiar el rol de un usuario. Con
 * `iniciarConDefecto` también arranca así (formulario de usuario nuevo). */
@Component({
  selector: 'app-modulos-selector',
  standalone: true,
  imports: [WeLoaderComponent],
  templateUrl: './modulos-selector.component.html',
})
export class ModulosSelectorComponent {
  private readonly usuariosService = inject(UsuariosService);

  readonly rol = input.required<Role>();
  readonly valor = model<Modulo[]>([]);
  readonly iniciarConDefecto = input(false);

  readonly catalogo = signal<ModuloCatalogo[]>([]);
  readonly cargando = signal(true);
  readonly error = signal(false);

  readonly disponibles = computed(() => this.catalogo().filter((m) => m.permitidoPara.includes(this.rol())));
  readonly esAdmin = computed(() => this.rol() === 'ADMIN');

  private rolPrevio: Role | null = null;

  constructor() {
    this.usuariosService
      .catalogoModulos()
      .then((c) => this.catalogo.set(c))
      .catch(() => this.error.set(true))
      .finally(() => this.cargando.set(false));

    effect(() => {
      const rol = this.rol();
      if (this.catalogo().length === 0) return;
      untracked(() => {
        const cambioDeRol = this.rolPrevio !== null && this.rolPrevio !== rol;
        if (cambioDeRol || (this.rolPrevio === null && this.iniciarConDefecto())) this.restablecer();
        this.rolPrevio = rol;
      });
    });
  }

  estaActivo(codigo: Modulo): boolean {
    return this.valor().includes(codigo);
  }

  alternar(codigo: Modulo): void {
    this.valor.update((v) => (v.includes(codigo) ? v.filter((m) => m !== codigo) : [...v, codigo]));
  }

  /** Los módulos con los que arranca este rol (ver porDefectoPara del catálogo). */
  restablecer(): void {
    const rol = this.rol();
    this.valor.set(this.catalogo().filter((m) => m.porDefectoPara.includes(rol)).map((m) => m.codigo));
  }
}
