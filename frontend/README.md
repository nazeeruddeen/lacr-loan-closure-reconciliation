# LACR Frontend

Angular operator console for the Loan Account Closure and Reconciliation workflow.

## What the frontend now does
- Authenticates operators with HTTP Basic credentials against the backend
- Loads live summary cards and closure queue data
- Creates closure requests
- Calculates settlement adjustments
- Starts reconciliation and applies bank-confirmed reconciliation
- Advances valid workflow statuses only when the backend allows them
- Triggers workflow actions that are protected by Redis-backed idempotency on the backend
- Displays status history and audit events sourced from the backend audit store
- Exports summary, event, and queue CSVs

## Configuration
- `src/environments/environment.ts` points to `http://localhost:8012`
- `src/environments/environment.prod.ts` points to relative API paths for reverse-proxy deployment

## Run
```bash
npm install
npm start
```

Dev server port:
- `4500`

## Build
```bash
npm run build
```

## Operator sign-in
Use one of the backend operator accounts from the root README, for example:
- `closureops / Closure@123`
- `reconlead / Recon@123`
- `auditor / Auditor@123`
- `opsadmin / Ops@123`
