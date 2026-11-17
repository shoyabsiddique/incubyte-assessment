import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

bootstrapApplication(App, appConfig).catch((error: unknown) => {
  console.error(error);
  // Show something instead of a blank page, for example when config.json cannot be loaded.
  const message = document.createElement('p');
  message.setAttribute('role', 'alert');
  message.style.padding = '24px';
  message.textContent =
    'The application could not start. Please try again later or contact support.';
  document.body.replaceChildren(message);
});
