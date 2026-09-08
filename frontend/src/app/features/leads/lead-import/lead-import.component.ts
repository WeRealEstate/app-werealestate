import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import * as XLSX from 'xlsx';
import { AuthService } from '../../../core/services/auth.service';
import { LeadsService } from '../../../core/services/leads.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { ToastService } from '../../../core/services/toast.service';
import { generarPlantillaLeads } from '../../../core/utils/plantilla-leads';
import { Desarrollo, LeadImportResultado } from '../../../core/models/lead.model';
import { Usuario } from '../../../core/models/user.model';

/** Roles que efectivamente trabajan leads y por lo tanto pueden recibir la asignación. */
const ROLES_ASIGNABLES = new Set(['ASESOR', 'LIDER_AREA']);

/** Una fila ya parseada del Excel, editable en la vista previa antes de confirmar la importación. */
interface FilaImportacion {
  nombreCliente: string;
  telefono: string;
  email: string;
  desarrolloId: number | null;
  origen: string;
  notas: string;
  incluir: boolean;
}

/** Quita acentos y normaliza a minúsculas para comparar encabezados/nombres de forma tolerante. */
function normalizar(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .trim();
}

@Component({
  selector: 'app-lead-import',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './lead-import.component.html',
})
export class LeadImportComponent {
  private readonly leadsService = inject(LeadsService);
  private readonly usuariosService = inject(UsuariosService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly esAdmin = computed(() => this.auth.currentUser()?.rol === 'ADMIN');

  readonly desarrollos = signal<Desarrollo[]>([]);
  readonly asesores = signal<Usuario[]>([]);
  readonly asesorId = signal<number | null>(null);
  readonly desarrolloDefaultId = signal<number | null>(null);

  readonly archivoNombre = signal<string | null>(null);
  readonly filas = signal<FilaImportacion[]>([]);
  readonly isProcesando = signal(false);
  readonly isImportando = signal(false);
  readonly errorArchivo = signal<string | null>(null);
  readonly resultado = signal<LeadImportResultado | null>(null);

  readonly totalIncluidas = computed(() => this.filas().filter((f) => f.incluir).length);

  constructor() {
    this.cargarInicial();
  }

  private async cargarInicial(): Promise<void> {
    this.desarrollos.set(await this.leadsService.listarDesarrollos());
    if (this.esAdmin()) {
      const usuarios = await this.usuariosService.listar();
      this.asesores.set(usuarios.filter((u) => u.activo && ROLES_ASIGNABLES.has(u.rol)));
    }
  }

  esFilaInvalida(fila: FilaImportacion): boolean {
    return !fila.nombreCliente.trim() || !fila.telefono.trim();
  }

  toggleIncluir(fila: FilaImportacion): void {
    if (this.esFilaInvalida(fila)) return;
    fila.incluir = !fila.incluir;
    this.filas.update((lista) => [...lista]);
  }

  onCampoEditado(): void {
    this.filas.update((lista) =>
      lista.map((f) => (this.esFilaInvalida(f) ? { ...f, incluir: false } : f)),
    );
  }

  async onArchivoSeleccionado(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const archivo = input.files?.[0];
    if (!archivo) return;

    this.errorArchivo.set(null);
    this.resultado.set(null);
    this.archivoNombre.set(archivo.name);
    this.isProcesando.set(true);

    try {
      const buffer = await archivo.arrayBuffer();
      const libro = XLSX.read(buffer, { type: 'array' });
      const hoja = libro.Sheets[libro.SheetNames[0]];
      const filasCrudas: string[][] = XLSX.utils.sheet_to_json(hoja, { header: 1, defval: '' });

      if (filasCrudas.length < 2) {
        this.errorArchivo.set('El archivo no tiene filas de datos.');
        this.filas.set([]);
        return;
      }

      const encabezados = filasCrudas[0].map((h) => normalizar(String(h)));
      const indiceDe = (...alias: string[]): number =>
        encabezados.findIndex((h) => alias.some((a) => h.includes(a)));

      const idxNombre = indiceDe('nombre');
      const idxTelefono = indiceDe('telefono');
      const idxCorreo = indiceDe('correo', 'email');
      const idxDesarrollo = indiceDe('desarrollo');
      const idxOrigen = indiceDe('origen');
      const idxNotas = indiceDe('nota');

      const celda = (fila: string[], idx: number): string =>
        idx >= 0 && fila[idx] !== undefined && fila[idx] !== null ? String(fila[idx]).trim() : '';

      const desarrollosPorNombre = new Map(this.desarrollos().map((d) => [normalizar(d.nombre), d.id]));

      const filasParseadas: FilaImportacion[] = filasCrudas
        .slice(1)
        .map((fila) => {
          const nombreCliente = celda(fila, idxNombre);
          const telefono = celda(fila, idxTelefono);
          const textoDesarrollo = celda(fila, idxDesarrollo);
          const desarrolloId = textoDesarrollo
            ? (desarrollosPorNombre.get(normalizar(textoDesarrollo)) ?? null)
            : null;

          return {
            nombreCliente,
            telefono,
            email: celda(fila, idxCorreo),
            desarrolloId,
            origen: celda(fila, idxOrigen),
            notas: celda(fila, idxNotas),
            incluir: Boolean(nombreCliente) && Boolean(telefono),
          };
        })
        .filter((f) => f.nombreCliente || f.telefono || f.email || f.origen || f.notas);

      this.filas.set(filasParseadas);
      if (filasParseadas.length === 0) {
        this.errorArchivo.set('No se encontraron filas con datos en el archivo.');
      }
    } catch {
      this.errorArchivo.set('No se pudo leer el archivo. Verifica que sea un .xlsx válido.');
      this.filas.set([]);
    } finally {
      this.isProcesando.set(false);
      input.value = '';
    }
  }

  async descargarPlantilla(): Promise<void> {
    await generarPlantillaLeads(this.desarrollos().map((d) => d.nombre));
  }

  async importar(): Promise<void> {
    if (this.isImportando() || this.totalIncluidas() === 0) return;

    const defaultId = this.desarrolloDefaultId();
    const incluidas = this.filas().filter((f) => f.incluir);
    const sinDesarrollo = incluidas.some((f) => f.desarrolloId === null && defaultId === null);
    if (sinDesarrollo) {
      this.toast.error('Elige un desarrollo por defecto para las filas que no traen uno.');
      return;
    }
    if (this.esAdmin() && this.asesorId() === null) {
      this.toast.error('Elige a qué asesor se le asignarán estos leads.');
      return;
    }

    this.isImportando.set(true);
    try {
      const resultado = await this.leadsService.importar({
        asesorId: this.esAdmin() ? this.asesorId() : null,
        leads: incluidas.map((f) => ({
          nombreCliente: f.nombreCliente,
          telefono: f.telefono,
          email: f.email || null,
          desarrolloId: f.desarrolloId ?? defaultId!,
          origen: f.origen || null,
          notas: f.notas || null,
        })),
      });
      this.resultado.set(resultado);
      this.filas.set([]);
      this.archivoNombre.set(null);
      if (resultado.errores.length === 0) {
        this.toast.success(`${resultado.creados} lead${resultado.creados === 1 ? '' : 's'} importado${resultado.creados === 1 ? '' : 's'}.`);
      }
    } catch {
      this.toast.error('No se pudo importar el archivo.');
    } finally {
      this.isImportando.set(false);
    }
  }
}
