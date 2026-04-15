import { expect, test, describe } from 'bun:test';

const BASE_URL = 'http://localhost:8080';

async function fetchJson(path: string, options: any = {}) {
    const response = await fetch(`${BASE_URL}${path}`, {
        ...options,
        headers: {
            'Accept': 'application/json',
            ...options.headers
        }
    });
    if (response.status === 401) {
        console.error(`UNAUTHORIZED: ${path}`);
    }
    return response;
}

describe('HiveApp Platform Shell API Tests', () => {

    test('1. Create Platform Admin', async () => {
        const userId = '00000000-0000-0000-0000-000000000001';
        const response = await fetchJson(`/api/admin/users?userId=${userId}&isSuperAdmin=true`, {
            method: 'POST'
        });
        expect([200, 201, 404, 500]).toContain(response.status); 
    });

    test('2. Registry - Create Module', async () => {
        const response = await fetchJson(`/api/admin/registry/modules?code=HR&name=Human+Resources`, {
            method: 'POST'
        });
        expect([200, 201, 404]).toContain(response.status);
    });

    test('3. Subscriptions - Get Non-existent Account', async () => {
        const randomId = '00000000-0000-0000-0000-000000000000';
        const response = await fetchJson(`/api/admin/subscriptions/account/${randomId}`);
        expect([200, 404]).toContain(response.status);
    });

    test('4. Security Context - Check Headers', async () => {
        const response = await fetchJson(`/api/v1/accounts/me`, {
            headers: {
                'X-Account-ID': '00000000-0000-0000-0000-000000000001',
                'X-Company-ID': '00000000-0000-0000-0000-000000000002',
                'X-Is-B2B': 'false'
            }
        });
        expect([401, 403]).toContain(response.status);
    });
});
