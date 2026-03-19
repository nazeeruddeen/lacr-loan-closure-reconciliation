# LACR Frontend Shell

Minimal Angular dashboard shell for the Loan Account Closure and Reconciliation project.

## What it covers
- Executive summary cards
- Closure workflow stages
- Search and filter panel
- Create / settlement / reconciliation / status actions
- Event/audit stream
- Report shortcuts
- Search pagination
- Live API integration with mock fallback when the backend is offline

## Configuration
- `src/environments/environment.ts` points to `http://localhost:8012/api/v1/closure-requests`
- `src/environments/environment.prod.ts` points to `/api/v1/closure-requests`

## Run
```bash
npm install
npm start
```

Default dev-server port:

- `4500`

## Build
```bash
npm run build
```
