import { Injectable, computed, signal } from '@angular/core';

export type ThemeMode = 'system' | 'light' | 'dark';

export const THEME_KEY = 'acme.theme';

const ORDER: readonly ThemeMode[] = ['system', 'light', 'dark'];

/** Light, dark or follow the system. The choice is remembered in the viewer's browser only. */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly mode = signal<ThemeMode>(read());

  /** What the toolbar button says: the current choice and what pressing it does. */
  readonly label = computed(() => `Colour theme: ${this.mode()}. Switch to ${this.next()}.`);

  constructor() {
    apply(this.mode());
  }

  set(mode: ThemeMode): void {
    this.mode.set(mode);
    apply(mode);
    try {
      if (mode === 'system') {
        localStorage.removeItem(THEME_KEY);
      } else {
        localStorage.setItem(THEME_KEY, mode);
      }
    } catch {
      // Not remembering the choice is better than failing to change it.
    }
  }

  cycle(): void {
    this.set(this.next());
  }

  private next(): ThemeMode {
    return ORDER[(ORDER.indexOf(this.mode()) + 1) % ORDER.length];
  }
}

function read(): ThemeMode {
  try {
    const saved = localStorage.getItem(THEME_KEY);
    return saved === 'light' || saved === 'dark' ? saved : 'system';
  } catch {
    return 'system';
  }
}

function apply(mode: ThemeMode): void {
  const root = document.documentElement;
  if (mode === 'system') {
    root.removeAttribute('data-theme');
  } else {
    root.setAttribute('data-theme', mode);
  }
}
