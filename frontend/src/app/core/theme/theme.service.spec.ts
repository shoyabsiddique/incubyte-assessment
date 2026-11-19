import { TestBed } from '@angular/core/testing';
import { THEME_KEY, ThemeService } from './theme.service';

describe('ThemeService', () => {
  const root = document.documentElement;

  beforeEach(() => {
    localStorage.removeItem(THEME_KEY);
    root.removeAttribute('data-theme');
    TestBed.resetTestingModule();
  });

  afterEach(() => {
    localStorage.removeItem(THEME_KEY);
    root.removeAttribute('data-theme');
  });

  it('follows the system setting until a choice is made', () => {
    const theme = TestBed.inject(ThemeService);

    expect(theme.mode()).toBe('system');
    expect(root.hasAttribute('data-theme')).toBe(false);
  });

  it('applies and remembers an explicit choice', () => {
    const theme = TestBed.inject(ThemeService);

    theme.set('dark');

    expect(theme.mode()).toBe('dark');
    expect(root.getAttribute('data-theme')).toBe('dark');
    expect(localStorage.getItem(THEME_KEY)).toBe('dark');
  });

  it('goes back to the system setting', () => {
    const theme = TestBed.inject(ThemeService);
    theme.set('light');

    theme.set('system');

    expect(root.hasAttribute('data-theme')).toBe(false);
    expect(localStorage.getItem(THEME_KEY)).toBeNull();
  });

  it('cycles system, light, dark and back', () => {
    const theme = TestBed.inject(ThemeService);

    theme.cycle();
    expect(theme.mode()).toBe('light');
    theme.cycle();
    expect(theme.mode()).toBe('dark');
    theme.cycle();
    expect(theme.mode()).toBe('system');
  });

  it('restores a saved choice on the next visit', () => {
    localStorage.setItem(THEME_KEY, 'dark');

    const theme = TestBed.inject(ThemeService);

    expect(theme.mode()).toBe('dark');
    expect(root.getAttribute('data-theme')).toBe('dark');
  });

  it('ignores a saved value it does not know', () => {
    localStorage.setItem(THEME_KEY, 'sepia');

    expect(TestBed.inject(ThemeService).mode()).toBe('system');
  });

  it('describes the current choice and the next one for the button', () => {
    const theme = TestBed.inject(ThemeService);

    expect(theme.label()).toBe('Colour theme: system. Switch to light.');
    theme.cycle();
    expect(theme.label()).toBe('Colour theme: light. Switch to dark.');
    theme.cycle();
    expect(theme.label()).toBe('Colour theme: dark. Switch to system.');
  });
});
