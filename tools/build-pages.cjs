/* Build a public static artifact without historical logs or device evidence. */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const output = path.resolve(process.argv[2] || path.join(require('node:os').tmpdir(), 'mosaicobot-pages'));
if (fs.existsSync(output)) throw Error('Output already exists; choose a fresh output directory.');
const copy = (source, destination) => {
  fs.mkdirSync(path.dirname(destination), { recursive: true });
  fs.copyFileSync(path.join(root, source), destination);
};
for (const name of ['index.html', 'styles.css', 'app.js', 'scenes.js']) {
  copy('docs/site/' + name, path.join(output, name));
}
const index = path.join(output, 'index.html');
// The source uses ../index.html for local preview; the artifact exposes /prototype/.
fs.writeFileSync(index, fs.readFileSync(index, 'utf8').replaceAll('../index.html', 'prototype/index.html').replaceAll('../hardware-redesign/index.html', 'prototype/hardware-redesign/index.html'));
const prototype = [
  'index.html', 'styles.css', 'app.js', 'device-apps.js', 'device-works.js',
  'device-works.css', 'app-center.js', 'app-center.css', 'game-mode.js', 'game-mode.css',
  'interaction.md', 'game-mode.md', 'game-mode-delivery.md',
  'hardware-redesign/index.html', 'hardware-redesign/design.css', 'hardware-redesign/design.js',
  'hardware-redesign/overview.png', 'hardware-redesign/README.md',
];
for (const name of prototype) {
  const destination = path.join(output, 'prototype', name);
  if (name.endsWith('.md')) {
    fs.mkdirSync(path.dirname(destination), { recursive: true });
    let guide = fs.readFileSync(path.join(root, 'docs/site/prototype-guide.md'), 'utf8');
    if (name.startsWith('hardware-redesign/')) {
      guide = guide.replaceAll('(../index.html)', '(../../index.html)')
        .replaceAll('(index.html', '(../index.html')
        .replaceAll('(hardware-redesign/index.html)', '(index.html)');
    }
    fs.writeFileSync(destination, guide);
  } else copy('docs/' + name, destination);
}
fs.writeFileSync(path.join(output, '.nojekyll'), '');
console.log('Static Pages artifact: ' + output);
console.log('Public files: ' + (4 + prototype.length + 1));
