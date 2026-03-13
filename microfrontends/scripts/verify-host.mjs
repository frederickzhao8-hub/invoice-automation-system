import { chromium } from 'playwright';

const checks = [
  {
    url: 'http://127.0.0.1:3000/operations/overview',
    texts: ['Operations Intelligence', 'Weekly AI Summary', 'Recommended Actions'],
  },
  {
    url: 'http://127.0.0.1:3000/invoices',
    texts: ['Invoice Automation System Supply Chain', 'Bulk PDF Import', 'Manual Upload'],
  },
  {
    url: 'http://127.0.0.1:3000/supply-chain/dashboard',
    texts: ['Supply Chain Module', 'AI Operations Insight', 'Orders needing intervention'],
  },
];

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage();
const browserErrors = [];

page.on('pageerror', (error) => {
  browserErrors.push(error.message);
});

page.on('console', (message) => {
  if (message.type() === 'error') {
    browserErrors.push(message.text());
  }
});

for (const check of checks) {
  await page.goto(check.url, { waitUntil: 'networkidle' });
  const bodyText = await page.textContent('body');

  for (const text of check.texts) {
    if (!bodyText || !bodyText.includes(text)) {
      throw new Error(`Missing expected text "${text}" on ${check.url}`);
    }
  }

  console.log(`verified ${check.url}`);
}

if (browserErrors.length > 0) {
  for (const error of browserErrors) {
    console.error(error);
  }

  throw new Error('Browser reported console or page errors while loading the host app.');
}

await browser.close();
