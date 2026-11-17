import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, map, tap } from 'rxjs';
import { AppConfigService } from '../config/app-config.service';

export interface SessionUser {
  id: string;
  email: string;
  displayName: string;
}

interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: SessionUser;
}

/**
 * The signed-in session. The token lives only in memory: it is never written to web storage, so a
 * page reload signs the user out, which is intended.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfigService);
  private readonly router = inject(Router);

  private readonly tokenSignal = signal<string | null>(null);
  private readonly userSignal = signal<SessionUser | null>(null);

  readonly token = this.tokenSignal.asReadonly();
  readonly user = this.userSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.tokenSignal() !== null);

  login(email: string, password: string): Observable<void> {
    return this.http
      .post<LoginResponse>(`${this.config.apiBaseUrl}/auth/login`, { email, password })
      .pipe(
        tap((response) => this.establishSession(response.accessToken, response.user)),
        map(() => undefined),
      );
  }

  establishSession(token: string, user: SessionUser): void {
    this.tokenSignal.set(token);
    this.userSignal.set(user);
  }

  logout(): void {
    this.tokenSignal.set(null);
    this.userSignal.set(null);
    void this.router.navigate(['/auth']);
  }
}
