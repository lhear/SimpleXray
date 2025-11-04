# 🔏 Commit Signing Guide

## Overview

Commit signing provides cryptographic proof that commits were created by a trusted source. This is critical for supply chain security and preventing unauthorized code changes.

## Current Status

⚠️ **Commit signing is not currently enforced** in this repository.

## Why Sign Commits?

1. **Authenticity**: Verifies commits were made by you
2. **Integrity**: Prevents tampering with commit history
3. **Supply Chain Security**: Protects against malicious commits
4. **Audit Trail**: Provides cryptographic proof of authorship
5. **Enterprise Compliance**: Required by many security policies

## Setting Up GPG Signing

### 1. Generate GPG Key

```bash
# Generate a new GPG key
gpg --full-generate-key

# Choose:
# - Key type: RSA and RSA (default)
# - Key size: 4096 bits (recommended)
# - Expiration: Set appropriate expiration (e.g., 1 year)
# - Real name: Your name
# - Email: Your git email
```

### 2. List Your GPG Keys

```bash
gpg --list-secret-keys --keyid-format LONG
```

Look for a line like:
```
sec   rsa4096/ABC123DEF4567890 2025-01-XX [SC]
```

Copy the key ID (e.g., `ABC123DEF4567890`)

### 3. Configure Git to Use GPG Key

```bash
# Set your GPG signing key
git config --global user.signingkey ABC123DEF4567890

# Enable commit signing by default
git config --global commit.gpgsign true
```

### 4. Add GPG Key to GitHub

```bash
# Export your public key
gpg --armor --export ABC123DEF4567890

# Copy the output and add it to GitHub:
# Settings → SSH and GPG keys → New GPG key
```

### 5. Verify Setup

```bash
# Test signing a commit
git commit --allow-empty -m "test: verify GPG signing"

# Check if signed
git log --show-signature -1
```

## Signing Existing Commits

### Sign Last Commit

```bash
git commit --amend --no-edit -S
```

### Sign Multiple Commits

```bash
# Sign last N commits
git rebase --exec 'git commit --amend --no-edit -n -S' -i HEAD~N
```

**Warning**: Only do this on commits that haven't been pushed or are on a private branch.

## Enforcing Signed Commits

### Option 1: Git Hooks (Recommended)

Create `.git/hooks/pre-commit`:

```bash
#!/bin/sh
# Reject unsigned commits
if ! git log -1 --pretty='%G?' | grep -q '^[GS]$'; then
    echo "ERROR: Commit is not signed!"
    echo "Please sign your commit with: git commit -S"
    exit 1
fi
```

Make it executable:
```bash
chmod +x .git/hooks/pre-commit
```

### Option 2: GitHub Branch Protection

1. Go to repository Settings → Branches
2. Add branch protection rule for `main` branch
3. Enable "Require signed commits"

### Option 3: CI/CD Check

Add to `.github/workflows/build.yml`:

```yaml
- name: Verify commit signatures
  run: |
    git log --show-signature --oneline | grep -E "^[0-9a-f]+ .* Verified" || exit 1
```

## Troubleshooting

### "gpg: signing failed: No secret key"

**Cause**: GPG key not found or not configured

**Solution**:
```bash
# Check if key exists
gpg --list-secret-keys

# Set correct key
git config --global user.signingkey YOUR_KEY_ID
```

### "gpg: signing failed: Inappropriate ioctl for device"

**Cause**: GPG agent not running in non-interactive environment

**Solution**:
```bash
# Set GPG_TTY
export GPG_TTY=$(tty)

# Or add to ~/.bashrc or ~/.zshrc
echo 'export GPG_TTY=$(tty)' >> ~/.bashrc
```

### "error: gpg failed to sign the data"

**Cause**: Various (permissions, agent, key)

**Solutions**:
1. Check GPG agent: `gpg-agent --daemon`
2. Verify key: `gpg --list-secret-keys`
3. Test signing: `echo "test" | gpg --clearsign`

## Best Practices

1. ✅ **Always sign commits** in production repositories
2. ✅ **Use 4096-bit RSA keys** for maximum security
3. ✅ **Set key expiration** (rotate keys periodically)
4. ✅ **Backup your private key** securely
5. ✅ **Enable signing by default** (`commit.gpgsign true`)
6. ✅ **Enforce signed commits** on protected branches
7. ❌ **Never share private keys**
8. ❌ **Never commit private keys** to repository

## Migration Plan

To enable commit signing for this repository:

1. **Phase 1**: Set up GPG keys for all contributors
2. **Phase 2**: Enable signing by default (`git config commit.gpgsign true`)
3. **Phase 3**: Add pre-commit hook to warn about unsigned commits
4. **Phase 4**: Enable GitHub branch protection requiring signed commits
5. **Phase 5**: Retroactively sign recent commits (optional, risky)

## Related Security Measures

- ✅ Dependency checksum verification
- ✅ Reproducible builds
- ✅ Certificate pinning
- ⚠️ Commit signing (not yet enforced)

---

**Status**: Commit signing documentation added  
**Next Step**: Enable GPG signing for all contributors

