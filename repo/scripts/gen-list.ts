import { readdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const pluginsDir = join(__dirname, '..', 'public', 'plugins');
const outputFile = join(__dirname, '..', 'public', '_list.json');

const files = readdirSync(pluginsDir)
  .filter(file => file.endsWith('.json'))
  .map(file => file.replace('.json', ''));

files.sort();

writeFileSync(outputFile, JSON.stringify(files, null, 2));

console.log(`Generated _list.json with ${files.length} plugins`);
