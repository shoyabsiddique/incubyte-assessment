import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AppConfigService } from '../../core/config/app-config.service';

export interface ExchangeRate {
  currency: string;
  /** How many US dollars one unit of the currency is worth, as a plain decimal string. */
  rate: string;
  version: number;
  editable: boolean;
}

@Injectable({ providedIn: 'root' })
export class ExchangeRateService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfigService);

  list(): Observable<ExchangeRate[]> {
    return this.http.get<ExchangeRate[]>(`${this.config.apiBaseUrl}/exchange-rates`);
  }

  update(currency: string, rate: string, version: number): Observable<ExchangeRate> {
    return this.http.put<ExchangeRate>(`${this.config.apiBaseUrl}/exchange-rates/${currency}`, {
      rate,
      version,
    });
  }
}
