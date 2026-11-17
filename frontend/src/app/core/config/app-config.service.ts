import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

interface RawConfig {
  apiBaseUrl?: string;
}

/**
 * Deployment settings read from /config.json when the app starts, so one build can run against any
 * environment. Nothing environment-specific is baked into the bundle.
 */
@Injectable({ providedIn: 'root' })
export class AppConfigService {
  private readonly http = inject(HttpClient);
  private baseUrl: string | null = null;

  async load(): Promise<void> {
    const config = await firstValueFrom(this.http.get<RawConfig>('/config.json'));
    if (!config?.apiBaseUrl) {
      throw new Error('config.json must define apiBaseUrl');
    }
    this.baseUrl = config.apiBaseUrl.replace(/\/+$/, '');
  }

  get apiBaseUrl(): string {
    if (this.baseUrl === null) {
      throw new Error('The app configuration is not loaded yet');
    }
    return this.baseUrl;
  }
}
