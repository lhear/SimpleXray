# Code Review Checklist

## JNI Safety

- [ ] JNI global refs released? (`DeleteGlobalRef` called for every `NewGlobalRef`)
- [ ] `GetByteArrayElements` → `ReleaseByteArrayElements` present?
- [ ] `GetStringUTFChars` → `ReleaseStringUTFChars` present?
- [ ] All JNI calls have error handling?

## Type Safety

- [ ] Format specifiers match arg types (`%d` for int, `%zu` for size_t, `%lld` for long long)
- [ ] No signed/unsigned mismatches?
- [ ] No integer overflow risks?

## Code Quality

- [ ] No unused parameters (or marked with `(void)cast`)?
- [ ] No TODO left in production code?
- [ ] Memory leaks checked (malloc/free pairs)?
- [ ] Resource cleanup in all code paths?

## Concurrency

- [ ] Thread-safe access to shared data?
- [ ] No race conditions?
- [ ] Proper synchronization primitives used?

## Performance

- [ ] No unnecessary allocations in hot paths?
- [ ] Algorithm complexity acceptable?
- [ ] No redundant operations?

## Security

- [ ] Input validation present?
- [ ] No buffer overflows?
- [ ] Crypto operations use proper nonces?
- [ ] Sensitive data cleared after use?

