import { expect, Page, test } from '@playwright/test';

const frontendBaseUrl = process.env.LACR_E2E_BASE_URL ?? 'http://127.0.0.1:4500';
const apiBaseUrl = process.env.LACR_E2E_API_BASE_URL ?? 'http://127.0.0.1:8012';
const username = process.env.LACR_E2E_USERNAME ?? 'closureops';
const password = requiredEnv('LACR_E2E_PASSWORD');

test.describe('LACR golden path', () => {
  test('operator can reconcile a closure account end-to-end', async ({ page }) => {
    const suffix = Date.now().toString();
    const requestId = `REQ-${suffix}`;
    const loanAccountNumber = `LACR-${suffix.slice(-8)}`;
    const borrowerName = `Borrower ${suffix}`;

    await login(page);

    const createdClosure = await apiJson<{ id: number }>(page, 'POST', '/api/v1/closure-requests', {
      requestId,
      loanAccountNumber,
      borrowerName,
      closureReason: 'Customer requested foreclosure',
      outstandingPrincipal: 150000,
      accruedInterest: 5000,
      penaltyAmount: 500,
      processingFee: 250,
      remarks: 'Playwright golden path'
    });

    await page.goto(`${frontendBaseUrl}/actions?selectedClosureId=${createdClosure.id}`);
    await expect(page.getByText(requestId)).toBeVisible();
    await expect(page.getByTestId('lacr-selected-status')).toHaveText('REQUESTED');

    await page.getByTestId('lacr-adjustment-amount').fill('1000');
    await page.getByTestId('lacr-settlement-remarks').fill('Waiver approved for E2E run');
    await page.getByTestId('lacr-calculate-settlement').click();
    await expect(page.getByTestId('lacr-selected-status')).toHaveText('SETTLEMENT_CALCULATED');

    const settledClosure = await apiJson<{ settlementAmount: number | string }>(page, 'GET', `/api/v1/closure-requests/${createdClosure.id}`);
    await page.getByTestId('lacr-start-reconciliation').click();
    await expect(page.getByTestId('lacr-selected-status')).toHaveText('RECONCILIATION_PENDING');

    await page.getByTestId('lacr-bank-confirmed-amount').fill(String(settledClosure.settlementAmount));
    await page.getByTestId('lacr-reconcile-remarks').fill('Matched against bank confirmation');
    await page.getByTestId('lacr-reconcile-now').click();
    await expect(page.getByTestId('lacr-selected-status')).toHaveText('RECONCILED');
  });
});

async function login(page: Page): Promise<void> {
  await page.goto(`${frontendBaseUrl}/dashboard`);
  await page.getByTestId('lacr-login-username').fill(username);
  await page.getByTestId('lacr-login-password').fill(password);
  await page.getByTestId('lacr-login-submit').click();
  await expect(page.getByTestId('lacr-tab-dashboard')).toBeVisible();
}

async function apiJson<T>(page: Page, method: 'GET' | 'POST', path: string, data?: unknown): Promise<T> {
  const response = await page.request.fetch(`${apiBaseUrl}${path}`, {
    method,
    data,
    headers: {
      Authorization: `Basic ${Buffer.from(`${username}:${password}`).toString('base64')}`,
      ...(data ? { 'Content-Type': 'application/json' } : {})
    }
  });
  expect(response.ok(), `${method} ${path} should succeed`).toBeTruthy();
  return (await response.json()) as T;
}

function requiredEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing required environment variable ${name}`);
  }
  return value;
}
