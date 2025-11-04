# 🔒 Dependency Security & Checksum Verification

## Overview

This project uses Gradle's dependency verification feature to protect against supply chain attacks. All dependencies are verified using SHA-256 checksums to ensure integrity.

## How It Works

1. **Initial Setup**: When verification is first enabled, run the build to generate checksums
2. **Automatic Verification**: Gradle automatically verifies all dependencies during build
3. **Build Failure**: If a checksum mismatch is detected, the build fails with an error

## Generating Checksums

### First Time Setup

To generate the initial checksum file:

```bash
# On Unix/Mac
./gradlew --write-verification-metadata sha256

# On Windows
gradlew.bat --write-verification-metadata sha256
```

This will:
1. Download all dependencies
2. Calculate SHA-256 checksums
3. Write them to `gradle/verification-metadata.xml`

### Reviewing Generated Checksums

After generation, review `gradle/verification-metadata.xml`:
- Verify checksums match expected values (if known)
- Check for any unexpected dependencies
- Commit the file to version control

### Updating Checksums

When adding new dependencies:

1. Add dependency to `app/build.gradle`
2. Run `./gradlew --write-verification-metadata sha256`
3. Review the diff in `gradle/verification-metadata.xml`
4. Commit both the dependency change and checksum update

## Verification Modes

Currently configured: **STRICT** mode
- All dependencies must have valid checksums
- Build fails if checksum is missing or invalid
- Protects against compromised repositories

### Changing Verification Mode

Edit `gradle.properties`:
- `org.gradle.dependency.verification=strict` - Require all checksums (current)
- `org.gradle.dependency.verification=warn` - Warn but don't fail
- `org.gradle.dependency.verification=off` - Disable verification (NOT RECOMMENDED)

## Security Benefits

✅ **Supply Chain Protection**: Prevents malicious artifacts from compromised repositories  
✅ **Integrity Verification**: Ensures dependencies haven't been tampered with  
✅ **Reproducible Builds**: Same dependencies produce same checksums  
✅ **Audit Trail**: All dependencies are tracked and verified  

## Troubleshooting

### Build Fails with "Checksum verification failed"

**Cause**: Dependency checksum doesn't match expected value

**Solutions**:
1. Verify the dependency version is correct
2. Check if repository was compromised (unlikely but possible)
3. Regenerate checksums: `./gradlew --write-verification-metadata sha256`

### Build Fails with "No checksum found"

**Cause**: New dependency added without updating checksums

**Solution**: Run `./gradlew --write-verification-metadata sha256` to generate checksums

### Checksum File is Large

**Normal**: The file contains checksums for all transitive dependencies (hundreds of entries)

**Note**: Don't manually edit this file - let Gradle manage it

## Best Practices

1. ✅ **Always commit** `gradle/verification-metadata.xml` to version control
2. ✅ **Review changes** to checksums when updating dependencies
3. ✅ **Regenerate checksums** after adding/updating dependencies
4. ✅ **Use strict mode** in production builds
5. ❌ **Never disable** verification in production
6. ❌ **Never skip** checksum verification for "convenience"

## Advanced: Signature Verification

For even stronger security, enable GPG signature verification:

1. Edit `gradle/verification-metadata.xml`:
   ```xml
   <verify-signatures>true</verify-signatures>
   ```

2. Add trusted GPG keys to `<trusted-keys>` section

3. Re-generate metadata: `./gradlew --write-verification-metadata sha256,pgp`

**Note**: Signature verification requires GPG keys for all dependencies, which may not be available for all artifacts.

## Related Security Measures

- ✅ Certificate pinning (for network requests)
- ✅ Reproducible builds (`SOURCE_DATE_EPOCH`)
- ✅ Dependency version locking (explicit versions)
- ✅ Dependency checksum verification (this file)

---

**Last Updated**: 2025-01-XX  
**Status**: Dependency verification enabled in strict mode

