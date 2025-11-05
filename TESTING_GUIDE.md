# 🧪 Testing Guide - Security Audit Fixes

## Overview

This guide provides testing instructions for all critical bug fixes and OpenSSL integration.

---

## Test Categories

### 1. Unit Tests

**Location:** `app/src/test/kotlin/com/simplexray/an/performance/`

**Test Files:**
- `CryptoTest.kt` - Crypto function tests (AES, ChaCha20)
- `JNIThreadSafetyTest.kt` - Background thread JNI tests
- `MemoryLeakTest.kt` - Memory leak prevention tests

**Run Tests:**
```bash
./gradlew test
```

**Expected Results:**
- All tests pass (or skip gracefully if native library not available)
- No crashes or SIGSEGV errors
- Functions return appropriate error codes when OpenSSL not installed

---

### 2. Integration Tests

#### JNI Thread Safety Test

**Manual Test:**
1. Start VPN service
2. Call JNI methods from multiple background coroutines
3. Verify no crashes occur

**Expected:**
- No SIGSEGV crashes
- All operations complete successfully
- Thread attachment/detachment working correctly

#### Memory Leak Test

**Manual Test:**
1. Run VPN service for extended period (>1 hour)
2. Monitor memory usage with Android Profiler
3. Check TLS session cache growth
4. Verify cleanup on service shutdown

**Expected:**
- Memory usage remains stable
- TLS cache cleaned up on `JNI_OnUnload`
- No OOM errors

#### Connection Pool Test

**Manual Test:**
1. Get multiple pooled sockets
2. Return sockets from different threads simultaneously
3. Verify no double-free crashes

**Expected:**
- No heap corruption
- No double-free errors
- Sockets properly invalidated before close

#### Ring Buffer Test

**Manual Test:**
1. High-throughput data transfer
2. Wraparound scenarios (buffer full/empty)
3. Multiple concurrent readers/writers

**Expected:**
- No data corruption
- Sequence numbers prevent ABA problem
- No crashes on wraparound

---

### 3. Crypto Function Tests

#### AES-128 Encryption Test

**Prerequisites:**
- OpenSSL libraries installed in `app/src/main/jni/openssl/`

**Test Steps:**
1. Prepare 16-byte plaintext and key
2. Call `nativeAES128Encrypt()`
3. Verify ciphertext differs from plaintext
4. Decrypt and verify plaintext matches

**Expected:**
- Function returns positive value (encrypted length)
- Ciphertext is different from plaintext
- Decryption works correctly

**Without OpenSSL:**
- Function returns -1
- Error message in logcat guides user to install OpenSSL

#### ChaCha20 Encryption Test

**Prerequisites:**
- OpenSSL libraries installed

**Test Steps:**
1. Prepare plaintext, 32-byte key, and 12-byte nonce
2. Call `nativeChaCha20NEON()`
3. Verify ciphertext differs from plaintext
4. Decrypt and verify plaintext matches

**Expected:**
- Function returns input length
- Ciphertext is different from plaintext
- Decryption works correctly

---

### 4. Stress Tests

#### Long-Running Session Test

**Duration:** 1+ hours

**Steps:**
1. Start VPN service
2. Monitor memory usage
3. Monitor CPU usage
4. Check for crashes or errors

**Expected:**
- No memory leaks
- No crashes
- Stable performance

#### Concurrent Operations Test

**Steps:**
1. Multiple threads calling JNI methods simultaneously
2. Multiple threads using connection pool
3. Multiple threads using ring buffer

**Expected:**
- No race conditions
- No data corruption
- No crashes

---

### 5. Performance Tests

#### Crypto Performance

**Test:**
- Measure encryption/decryption speed
- Compare with broken implementation (if available)
- Verify hardware acceleration working

**Expected:**
- OpenSSL performance better than broken implementation
- Hardware acceleration active on supported devices

#### Memory Usage

**Test:**
- Monitor memory usage over time
- Check for leaks
- Verify cleanup on shutdown

**Expected:**
- Stable memory usage
- No leaks
- Proper cleanup

---

## Running All Tests

### Unit Tests
```bash
./gradlew test
```

### Instrumented Tests (on device/emulator)
```bash
./gradlew connectedAndroidTest
```

### Build Test
```bash
./gradlew clean assembleDebug
```

---

## Test Results Interpretation

### Success Criteria

✅ **All unit tests pass**
✅ **No crashes in integration tests**
✅ **No memory leaks detected**
✅ **Crypto functions work when OpenSSL installed**
✅ **Crypto functions safely disabled when OpenSSL not installed**

### Failure Scenarios

❌ **SIGSEGV crashes** → JNI thread safety issue
❌ **Memory leaks** → Cleanup not working
❌ **Double-free errors** → Connection pool issue
❌ **Data corruption** → Ring buffer ABA problem
❌ **Crypto returns garbage** → Broken implementation (should be disabled)

---

## Continuous Integration

### CI Test Plan

1. **Unit Tests** (automated)
   - Run on every commit
   - Must pass before merge

2. **Build Tests** (automated)
   - Verify compilation succeeds
   - Check for OpenSSL integration

3. **Integration Tests** (manual/periodic)
   - Run before releases
   - Long-running sessions
   - Stress tests

---

## Troubleshooting

### Tests Fail with UnsatisfiedLinkError

**Cause:** Native library not available in test environment

**Solution:** Tests are designed to skip gracefully. This is expected behavior.

### Crypto Tests Return -1

**Cause:** OpenSSL libraries not installed

**Solution:** Install OpenSSL (see `OPENSSL_SETUP_INSTRUCTIONS.md`)

### Crashes During Tests

**Cause:** Bug in implementation

**Solution:** Check logcat for error details, review code changes

---

**Status:** ✅ Test Suite Ready  
**Next:** Run `./gradlew test` to execute unit tests


