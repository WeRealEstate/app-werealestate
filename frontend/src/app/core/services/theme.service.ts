import { Injectable, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'we_theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>(this.readInitialTheme());

  constructor() {
    this.applyTheme(this.theme());
    // El color de la barra del sistema (título en escritorio, estado en el teléfono) sigue al tema y al tamaño.
    window.matchMedia('(min-width: 1024px)').addEventListener('change', () => this.applyThemeColor(this.theme()));
  }

  toggle(): void {
    this.setTheme(this.theme() === 'dark' ? 'light' : 'dark');
  }

  setTheme(theme: Theme): void {
    this.theme.set(theme);
    this.applyTheme(theme);
    localStorage.setItem(STORAGE_KEY, theme);
  }

  private applyTheme(theme: Theme): void {
    document.documentElement.classList.toggle('dark', theme === 'dark');
    this.applyThemeColor(theme);
  }

  /** Escritorio (menú lateral fijo): la barra de título de la app instalada toma el color del tema.
   * Teléfono: la barra superior de la app siempre es azul marino, así que la barra de estado también. */
  private applyThemeColor(theme: Theme): void {
    const escritorio = window.matchMedia('(min-width: 1024px)').matches;
    const color = escritorio ? (theme === 'dark' ? '#0f172a' : '#f7f9ff') : '#0b132b';
    let meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
    if (!meta) {
      meta = document.createElement('meta');
      meta.name = 'theme-color';
      document.head.appendChild(meta);
    }
    meta.content = color;
  }

  private readInitialTheme(): Theme {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === 'dark' ? 'dark' : 'light';
  }
}
