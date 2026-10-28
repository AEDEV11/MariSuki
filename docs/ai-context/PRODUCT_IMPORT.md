# Product catalogs and bulk import

Implemented 2026-10-17. This extends the existing Room schema version 6 without changing tables or requiring a migration.

## Reusable choices

- Category names remain in `categories`. The product editor selects an existing name or saves a new name and selects it immediately. Category matching uses the existing case-insensitive repository lookup.
- Stock-unit and selling-unit names combine defaults, existing product units, and saved choices. Saved names use `app_settings` keys beginning `catalog.stock_unit.` or `catalog.selling_unit.`. They are included in existing encrypted backups.
- Creating, importing, or editing a product remembers its unit names. Editing also remembers the previous names before replacing product units. A removed unit therefore remains available for another product.
- Only names are shared. Conversion quantities and selling prices belong to the individual product; choosing a name never copies another product’s price or pack size.
- Saving a choice requires product viewing and either product addition or editing permission.

## File contract

`data/importing/ProductImport.kt` owns the CSV headings and validation. `assets/templates/products.xlsx` contains a blank `Products` sheet and a bilingual `Guide` sheet with instructions and examples. The spreadsheet was generated and rendered with the spreadsheet artifact tool. The generated template has a contract test to detect mismatched headings or accidentally importable examples.

The 15 headings are:

1. Product name
2. Barcode
3. Category
4. Stock unit
5. Selling price
6. Purchase cost
7. Starting stock
8. Low stock alert
9. Allow fractions
10. Selling unit 1
11. Stock units per selling unit 1
12. Selling price 1
13. Selling unit 2
14. Stock units per selling unit 2
15. Selling price 2

Heading order may change, but every heading must remain present once. Product name, Stock unit, and Selling price are required. Other numeric fields default to zero. Allow fractions defaults to No and accepts Yes/No, true/false, 1/0, or oo/hindi. An optional selling-unit group must supply all three values, with a positive conversion and a different name from the other units.

Money uses pesos with at most two significant decimal places; quantities use at most three. Values must be nonnegative plain decimals, with at most nine integer digits. Whole stock units require whole opening quantities and conversions. Barcode text is preserved; the Excel barcode column is preformatted as Text to retain leading zeros.

The importer supports UTF-8 CSV (including BOM, quoted commas, escaped quotes and embedded line breaks) and `.xlsx`. Older binary `.xls` files must be saved as `.xlsx`. XLSX uses workbook relationships to prefer the `Products` sheet, falling back to the first sheet for other workbooks. The `Guide` sheet is not read when `Products` exists. It supports shared and inline strings and sparse cells. Formula/error cells are rejected; formulas are never evaluated. File size is limited to 8 MiB, expanded ZIP content to 32 MiB and 512 entries, and nonempty product rows to 1,000. External XML entities and DTDs are rejected.

## Import behavior

- Products → Bulk add products offers Android document pickers to save CSV/Excel templates and choose an edited file. Reading and writing run on IO coroutines in the ViewModel.
- Preview shows all product rows in pages of 30, including prices, barcode, category, opening stock, low-stock threshold, fraction rules and additional unit conversions/prices. Purchase cost is displayed only with cost-view/edit permission. Row/column errors block the entire import until the corrected file is selected again.
- Duplicate barcodes within a file or against any existing product, including archived products, are rejected. Preview also checks stock and purchase-cost permissions; commit reauthorizes the batch.
- The batch authorizes each required permission once, then uses the same private product-creation helper as individual creation inside one Room transaction. An invalid row rolls back products, categories, units, balances, movements, remembered names and audit rows together. Existing products are never updated by this workflow.
- A canonical batch digest stored under `catalog.import.` makes a repeat of the same batch idempotent, including reordered rows. This is insertion only, not an update or inventory-replenishment workflow.
- Busy state prevents double submission and dismissal during an active operation. No real store products were inserted during manual verification; transaction tests use in-memory databases.
