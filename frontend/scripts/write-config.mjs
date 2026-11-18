// Writes public/config.json from the API_BASE_URL environment variable so the same build can be pointed at
// any API at deploy time. With no variable set the committed local default is left alone.
import { writeFileSync } from 'node:fs';

const url = process.env.API_BASE_URL;
if (!url) {
  console.log('API_BASE_URL is not set: keeping public/config.json as it is.');
} else {
  if (!/^https:\/\/.+\/api\/v1\/?$/.test(url)) {
    console.error(`API_BASE_URL must be an https address ending in /api/v1, got: ${url}`);
    process.exit(1);
  }
  const apiBaseUrl = url.replace(/\/$/, '');
  writeFileSync(new URL('../public/config.json', import.meta.url), JSON.stringify({ apiBaseUrl }, null, 2) + '\n');
  console.log(`public/config.json now points at ${apiBaseUrl}`);
}
