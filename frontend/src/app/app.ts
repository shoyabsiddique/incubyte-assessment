import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { APP_NAME } from './app-name';

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
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
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
}
