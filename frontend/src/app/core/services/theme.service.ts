import { Injectable, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'we_theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>(this.readInitialTheme());

  constructor() {
    this.applyTheme(this.theme());
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
  }

  private readInitialTheme(): Theme {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === 'dark' ? 'dark' : 'light';
  }
}
