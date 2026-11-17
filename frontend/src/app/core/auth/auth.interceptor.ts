import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AppConfigService } from '../config/app-config.service';
import { AuthService } from './auth.service';

/**
 * Sends the token with calls to our API only (never to other origins) and, when the API says the
 * session is no longer valid, signs the user out. A failed sign-in is not a lost session.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const apiBaseUrl = inject(AppConfigService).apiBaseUrl;

  const isApiCall = request.url.startsWith(apiBaseUrl);
  const token = auth.token();
  const outgoing =
    isApiCall && token
      ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : request;

  return next(outgoing).pipe(
    catchError((error: unknown) => {
      const isLogin = request.url === `${apiBaseUrl}/auth/login`;
      if (isApiCall && !isLogin && error instanceof HttpErrorResponse && error.status === 401) {
        auth.logout();
      }
      return throwError(() => error);
    }),
  );
};
