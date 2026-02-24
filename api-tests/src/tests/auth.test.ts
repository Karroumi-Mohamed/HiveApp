import { post, log } from "../client";

const testUser = {
  email: `test_${Date.now()}@hiveapp.com`,
  password: "password123",
  firstName: "Test",
  lastName: "User",
  phone: "+1234567890",
};

console.log("=== AUTH TESTS ===");
console.log(`Using test user: ${testUser.email}`);

// 1. Register
const registerRes = await post("/api/v1/auth/register", testUser);
log("POST /api/v1/auth/register", registerRes);

const accessToken = registerRes.data?.accessToken;
const refreshToken = registerRes.data?.refreshToken;

// 2. Register duplicate (should 409)
const duplicateRes = await post("/api/v1/auth/register", testUser);
log("POST /api/v1/auth/register (duplicate — expect 409)", duplicateRes);

// 3. Login
const loginRes = await post("/api/v1/auth/login", {
  email: testUser.email,
  password: testUser.password,
});
log("POST /api/v1/auth/login", loginRes);

// 4. Login wrong password (should 400)
const badLoginRes = await post("/api/v1/auth/login", {
  email: testUser.email,
  password: "wrongpassword",
});
log("POST /api/v1/auth/login (wrong password — expect 400)", badLoginRes);

// 5. Refresh token
if (refreshToken) {
  const refreshRes = await post("/api/v1/auth/refresh", { refreshToken });
  log("POST /api/v1/auth/refresh", refreshRes);
} else {
  console.log("\n⚠️  Skipping refresh test — no refresh token from register");
}

// 6. Access protected route without token (should 401)
const { get } = await import("../client");
const noAuthRes = await get("/api/v1/protected-test");
log("GET /api/v1/protected-test (no token — expect 401)", noAuthRes);

console.log("\n=== AUTH TESTS COMPLETE ===\n");

export { accessToken, refreshToken };
