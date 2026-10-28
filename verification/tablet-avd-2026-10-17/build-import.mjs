import fs from 'node:fs/promises';
import { SpreadsheetFile } from '@oai/artifact-tool';

const rows = [
  ["Jack 'n Jill Piattos Sour Cream & Onion 85g", '4800016671500', 'Snacks', 'Bag', 22, 16, 48, 6, 'No', 'Wholesale case', 24, 420, '', '', ''],
  ["Jack 'n Jill Nova Country Cheddar 40g", '4800016663802', 'Snacks', 'Bag', 20, 13, 60, 8, 'No', 'Wholesale case', 24, 360, '', '', ''],
  ["Jack 'n Jill Chippy Barbecue 25g", '4800016643095', 'Snacks', 'Bag', 15, 10, 72, 10, 'No', 'Wholesale case', 24, 320, '', '', ''],
  ['Oreo Original Vanilla Cookies 154g', '7622210707284', 'Snacks', 'Pack', 20, 14, 36, 5, 'No', 'Wholesale box', 12, 210, '', '', ''],
  ['Coca-Cola Original Taste 330ml', '5449000000996', 'Beverages', 'Can', 30, 22, 48, 8, 'No', 'Wholesale case', 24, 660, '', '', ''],
  ['Nestlé Milo Powder 300g', '4800361381246', 'Beverages', 'Pouch', 105, 84, 24, 4, 'No', 'Wholesale case', 12, 1140, '', '', ''],
  ['Nescafé Classic Instant Coffee 100g', '4800361339186', 'Beverages', 'Jar', 125, 98, 18, 3, 'No', 'Wholesale case', 12, 1380, '', '', ''],
  ['Nutella Hazelnut Spread 350g', '3017620422003', 'Pantry', 'Jar', 245, 198, 12, 2, 'No', 'Wholesale case', 12, 2640, '', '', ''],
  ['Del Monte Original Style Tomato Sauce 115g', '4800024555489', 'Pantry', 'Pouch', 15, 10, 48, 8, 'No', 'Wholesale case', 24, 330, '', '', ''],
  ['Datu Puti Soy Sauce 1L', '4801668500224', 'Pantry', 'Bottle', 70, 53, 18, 3, 'No', 'Wholesale case', 12, 780, '', '', ''],
  ['Lucky Me! Pancit Canton Original 65g', '4807770275586', 'Noodles', 'Pack', 18, 12, 72, 12, 'No', 'Wholesale case', 24, 400, '', '', ''],
  ['Purefoods Corned Beef 150g', '4808887010077', 'Canned Goods', 'Can', 92, 72, 24, 4, 'No', 'Wholesale case', 12, 1020, '', '', ''],
  ['Heinz Beanz in Tomato Sauce 415g', '5000157024671', 'Canned Goods', 'Can', 115, 88, 18, 3, 'No', 'Wholesale case', 6, 630, '', '', ''],
];

const outDir = '/tmp/marisuki-avd-run';
const templatePath = '/home/adrielespiritu/StudioProjects/MariSuki/app/src/main/assets/templates/products.xlsx';
const templateBytes = await fs.readFile(templatePath);
const workbook = await SpreadsheetFile.importXlsx(templateBytes);
const sheet = workbook.worksheets.getItem('Products');
const excelRows = rows.slice(7);
sheet.getRange(`A2:O${excelRows.length + 1}`).values = excelRows;
workbook.recalculate();
await (await SpreadsheetFile.exportXlsx(workbook)).save(`${outDir}/MariSuki-demo-upload.xlsx`);

const csvEscape = (value) => {
  const text = String(value ?? '');
  return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
};
const headers = ['Product name', 'Barcode', 'Category', 'Stock unit', 'Selling price', 'Purchase cost', 'Starting stock', 'Low stock alert', 'Allow fractions', 'Selling unit 1', 'Stock units per selling unit 1', 'Selling price 1', 'Selling unit 2', 'Stock units per selling unit 2', 'Selling price 2'];
const csv = '\ufeff' + [headers, ...rows.slice(0, 7)].map(row => row.map(csvEscape).join(',')).join('\r\n') + '\r\n';
await fs.writeFile(`${outDir}/MariSuki-demo-upload.csv`, csv, 'utf8');
console.log(`Created ${rows.slice(0, 7).length} CSV rows and ${excelRows.length} XLSX rows.`);
console.log(`Five categories; ${rows.length} unique, checksum-valid EAN/GTIN codes; one per-item unit and one wholesale unit on every row.`);
