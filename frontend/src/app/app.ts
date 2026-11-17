import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { APP_NAME } from './app-name';
import { AuthService } from './core/auth/auth.service';

interface NavItem {
  readonly label: string;
  readonly path: string;
}

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatButtonModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly title = APP_NAME;

  protected readonly navItems: readonly NavItem[] = [
    { label: 'Dashboard', path: '/dashboard' },
    { label: 'Employees', path: '/employees' },
    { label: 'Compensation', path: '/compensation' },
    { label: 'Anomalies', path: '/anomalies' },
    { label: 'Salary simulator', path: '/simulator' },
    { label: 'Audit history', path: '/audit' },
    { label: 'Exchange rates', path: '/exchange-rates' },
  ];

  protected signOut(): void {
    this.auth.logout();
  }
}
