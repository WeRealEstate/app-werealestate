import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import * as XLSX from 'xlsx';
import { Desarrollo } from '../../../core/models/lead.model';
import { LoteImportResultado } from '../../../core/models/lote.model';
import { LeadsService } from '../../../core/services/leads.service';
import { LotesService } from '../../../core/services/lotes.service';
import { generarPlantillaLotes } from '../../../core/utils/plantilla-lotes';

/** Una fila ya parseada del Excel, editable en la vista previa antes de confirmar la importación. */
interface FilaImportacion {
  desarrolloId: number | null;
  manzana: string;
  numeroLote: string;
  superficie: string;
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
  selector: 'app-lote-import',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './lote-import.component.html',
})
export class LoteImportComponent {
  private readonly leadsService = inject(LeadsService);
  private readonly lotesService = inject(LotesService);

  readonly desarrollos = signal<Desarrollo[]>([]);

  readonly archivoNombre = signal<string | null>(null);
  readonly filas = signal<FilaImportacion[]>([]);
  readonly isProcesando = signal(false);
  readonly isImportando = signal(false);
  readonly errorArchivo = signal<string | null>(null);
  readonly resultado = signal<LoteImportResultado | null>(null);

  readonly totalIncluidas = computed(() => this.filas().filter((f) => f.incluir).length);

  constructor() {
    this.leadsService.listarDesarrollosGestionables().then((d) => this.desarrollos.set(d));
  }

  esFilaInvalida(fila: FilaImportacion): boolean {
    return (
      fila.desarrolloId === null ||
      !fila.manzana.trim() ||
      !fila.numeroLote.trim() ||
      !fila.superficie.trim() ||
      Number.isNaN(Number(fila.superficie)) ||
      Number(fila.superficie) <= 0
    );
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

      const idxDesarrollo = indiceDe('desarrollo');
      const idxManzana = indiceDe('manzana');
      const idxLote = indiceDe('lote');
      const idxSuperficie = indiceDe('superficie', 'm2', 'metros');

      const celda = (fila: string[], idx: number): string =>
        idx >= 0 && fila[idx] !== undefined && fila[idx] !== null ? String(fila[idx]).trim() : '';

      const desarrollosPorNombre = new Map(this.desarrollos().map((d) => [normalizar(d.nombre), d.id]));

      const filasParseadas: FilaImportacion[] = filasCrudas
        .slice(1)
        .map((fila) => {
          const textoDesarrollo = celda(fila, idxDesarrollo);
          const desarrolloId = textoDesarrollo ? (desarrollosPorNombre.get(normalizar(textoDesarrollo)) ?? null) : null;
          const manzana = celda(fila, idxManzana);
          const numeroLote = celda(fila, idxLote);
          const superficie = celda(fila, idxSuperficie);

          const filaParseada: FilaImportacion = {
            desarrolloId,
            manzana,
            numeroLote,
            superficie,
            incluir: false,
          };
          filaParseada.incluir = !this.esFilaInvalida(filaParseada);
          return filaParseada;
        })
        .filter((f) => f.manzana || f.numeroLote || f.superficie);

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
    await generarPlantillaLotes(this.desarrollos().map((d) => d.nombre));
  }

  async importar(): Promise<void> {
    if (this.isImportando() || this.totalIncluidas() === 0) return;

    this.isImportando.set(true);
    try {
      const incluidas = this.filas().filter((f) => f.incluir);
      const resultado = await this.lotesService.importar({
        lotes: incluidas.map((f) => ({
          desarrolloId: f.desarrolloId!,
          manzana: f.manzana,
          numeroLote: f.numeroLote,
          superficie: Number(f.superficie),
        })),
      });
      this.resultado.set(resultado);
      this.filas.set([]);
      this.archivoNombre.set(null);
    } catch {
      this.errorArchivo.set('No se pudo importar el archivo.');
    } finally {
      this.isImportando.set(false);
    }
  }
}
