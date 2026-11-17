import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import {
  ActivatedRouteSnapshot,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { AppConfigService } from '../config/app-config.service';
import { AuthService } from './auth.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AppConfigService, useValue: { apiBaseUrl: 'http://api.test/api/v1' } },
      ],
    });
    auth = TestBed.inject(AuthService);
  });

  function run() {
    return TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url: '/employees' } as RouterStateSnapshot),
    );
  }

  it('lets a signed-in user through', () => {
    auth.establishSession('tok', { id: 'u-1', email: 'a@b.c', displayName: 'A' });

    expect(run()).toBe(true);
  });

  it('sends a signed-out visitor to the sign-in page', () => {
    const result = run();

    expect(result).toBeInstanceOf(UrlTree);
    expect((result as UrlTree).toString()).toBe('/auth');
  });
});
