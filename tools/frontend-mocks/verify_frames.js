const fs = require('fs');
const path = require('path');

const webappDir = path.resolve(__dirname, '../../src/main/webapp');
const pagesDir = path.join(webappDir, 'pages');
const categories = ['buyer', 'auth', 'organizer', 'checkin', 'admin'];
const expectedFrames = [
  ...Array.from({ length: 8 }, (_, index) => `UI-${String(index + 1).padStart(2, '0')}-`),
  'UI-09-', 'UI-10-', 'UI-11-', 'UI-12-', 'UI-13-', 'UI-14-', 'UI-15-', 'UI-16-',
  'UI-17-', 'UI-18-', 'UI-19-', 'UI-20-', 'UI-21-', 'UI-22-', 'UI-23-', 'UI-24-'
];

console.log('--- VERIFYING 24 FRAMES ARCHITECTURE ---');

const frameFiles = categories.flatMap(category =>
  fs.readdirSync(path.join(pagesDir, category))
    .filter(file => file.startsWith('UI-') && file.endsWith('.html'))
    .map(file => path.join(category, file))
);

const missingFrames = expectedFrames.filter(prefix =>
  !frameFiles.some(file => path.basename(file).startsWith(prefix))
);

if (missingFrames.length > 0 || frameFiles.length !== 24) {
  console.error(`[FAIL] Expected 24 UI frames, found ${frameFiles.length}.`);
  if (missingFrames.length > 0) console.error(`Missing prefixes: ${missingFrames.join(', ')}`);
  process.exit(1);
}

if (!fs.existsSync(path.join(webappDir, 'index.html'))) {
  console.error('[FAIL] Main index.html is missing.');
  process.exit(1);
}

console.log('[SUCCESS] All 24 frame files are grouped under pages/{buyer,organizer,checkin,admin}.');
