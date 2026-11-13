import { HttpErrorResponse } from '@angular/common/http';

/** Texto de error del servidor (`message` de la respuesta) o el mensaje por defecto si no viene. */
export function mensajeDe(error: unknown, porDefecto: string): string {
  return error instanceof HttpErrorResponse && typeof error.error?.message === 'string' ? error.error.message : porDefecto;
}

/** Monto con coma de miles y dos decimales, sin símbolo ("12,345.60"). */
export function dinero(valor: number): string {
  return valor.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/** Mes en curso del navegador ("2026-11"). */
export function mesActual(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}
