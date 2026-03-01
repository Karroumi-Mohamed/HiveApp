import { post, get, log } from "../client";

const testUser = {
  email: `test_${Date.now()}@hiveapp.com`,
  password: "password123",
  firstName: "Test",
  lastName: "User",
  phone: "+1234567890",
};

console.log("=== ACCOUNT TESTS ===");
console.log(`Using test user: ${testUser.email}`);

// 1. Register (triggers UserRegisteredEvent → account auto-created)
const registerRes = await post("/api/v1/auth/register", testUser);
log("POST /api/v1/auth/register (expect 201)", registerRes);

const accessToken = registerRes.data?.accessToken;

// 2. Get my account (authenticated — expect 200)
if (accessToken) {
  const accountRes = await get("/api/v1/accounts/me", accessToken);
  log("GET /api/v1/accounts/me (expect 200)", accountRes);
} else {
  console.log("\n⚠️  Skipping account test — no access token from register");
}

// 3. Get my account (unauthenticated — expect 401)
const noAuthRes = await get("/api/v1/accounts/me");
log("GET /api/v1/accounts/me (no token — expect 401)", noAuthRes);

console.log("\n=== ACCOUNT TESTS COMPLETE ===\n");
