# AI Fixer Bot - Quick Start Guide

This repository includes automated AI-powered code review and fix suggestions for PRs.

## Setup

### 1. Add GitHub Secrets

Go to your repository settings → Secrets and variables → Actions, and add:

- **`OPENAI_API_KEY`** — Your OpenAI API key (organization key recommended)
- **`GITHUB_TOKEN`** — Usually provided automatically by GitHub Actions, but you can set a custom PAT if needed

### 2. Workflow Permissions

The workflows require:
- `contents: read` — To read repository files
- `pull-requests: write` — To post comments on PRs

These are set in the workflow files. If you enable auto-commit (disabled by default), you'll also need `contents: write`.

## How It Works

### Inline Fixer (`inline-fixer.yml`)

- Runs on PR events: `opened`, `reopened`, `synchronize`, `ready_for_review`
- Posts inline review comments directly on changed lines
- Uses GPT-5 to analyze JNI leaks, format specifiers, null checks, etc.

### AI Fixer Bot (`fixer.yml`)

- Runs on PR events: `opened`, `synchronize`, `reopened`
- Creates `ai_report.json` with analysis results
- Generates `auto.patch` if fixes are available
- Posts summary comment to PR
- **Uploads patch as artifact** (preview mode — no auto-commit by default)

## Usage

1. **Commit the workflows and tools:**
   ```bash
   git add .github/workflows/*.yml tools/ docs/ README_AI_FIXER.md .github/pull_request_template.md
   git commit -m "feat: Add AI fixer workflows & tools"
   git push
   ```

2. **Open a PR** — Workflows will automatically run

3. **Review the results:**
   - Check inline comments on code changes
   - Read the PR summary comment
   - Download `ai-auto-patch` artifact if available
   - Review and apply the patch manually if desired

## Patch Preview Mode

By default, the bot runs in **patch preview mode**:
- ✅ Analysis and patch generation enabled
- ✅ Patch uploaded as workflow artifact
- ❌ Auto-commit disabled (for safety)

### Applying Patches Manually

If a patch is generated:

```bash
# Download the artifact from workflow run
# Or check the PR comment for instructions

# Apply the patch
git apply auto.patch

# Review the changes
git diff

# Commit if satisfied
git add .
git commit -m "Apply AI fixes"
```

## Enabling Auto-Commit (Optional)

⚠️ **Warning:** Auto-commit can make changes without human review. Use with caution.

To enable auto-commit:

1. Edit `.github/workflows/fixer.yml`
2. Change permissions:
   ```yaml
   permissions:
     contents: write  # Changed from 'read'
     pull-requests: write
   ```
3. Uncomment the "Auto commit patch" step
4. Set `if: false` to `if: steps.create_patch.outcome == 'success' && hashFiles('auto.patch') != ''`

## Customization

### Model Selection

By default, the scripts use `gpt-5`. To change:

Edit `tools/*.py` files and replace:
```python
model="gpt-5"
```
with your preferred model (e.g., `gpt-4o`, `gpt-4-turbo`, `gpt-3.5-turbo`).

### Prompt Tuning

Edit the `prompt` variables in:
- `tools/ai_inline_review.py` — For inline comment style
- `tools/ai_analyze.py` — For analysis focus areas
- `tools/ai_patch.py` — For patch generation rules

### Focus Areas

The AI is configured to focus on:
- JNI memory leaks
- Format specifier mismatches
- Null pointer checks
- Concurrency issues
- Performance hotspots
- Security vulnerabilities

Adjust these in the prompt strings as needed.

## Troubleshooting

### "Missing env variables"
- Check that `OPENAI_API_KEY` is set in repository secrets
- Verify `GITHUB_TOKEN` is available (usually automatic)

### "No diff found"
- PR may be empty or base branch is incorrect
- Check that `fetch-depth: 0` is set in checkout step

### "Patch does not apply cleanly"
- The patch may be outdated if PR changed after analysis
- Review the patch manually and apply selected hunks

### API Rate Limits
- OpenAI has rate limits; large PRs may hit limits
- Consider using organization API keys with higher limits

## Security Notes

1. **Token Security**: Never commit API keys or tokens to the repository
2. **Review AI Output**: Always review AI-generated patches before applying
3. **Model Access**: Ensure your OpenAI account has access to the model you're using
4. **Cost Management**: Monitor API usage; large PRs can consume significant tokens

## Files Structure

```
.github/
  workflows/
    inline-fixer.yml    # Inline comment workflow
    fixer.yml           # Full analysis + patch workflow
tools/
  ai_inline_review.py   # Inline comment generator
  ai_analyze.py         # Static analysis runner
  ai_patch.py           # Patch generator
  ai_pr_commenter.py    # PR summary commenter
  __init__.py           # Package marker
docs/
  review-checklist.md   # Manual review checklist
.github/
  pull_request_template.md  # PR template
```

## Support

For issues or questions:
1. Check workflow logs in GitHub Actions
2. Review `ai_report.json` for analysis details
3. Check the PR comment for summary information

---

**Note:** This is a preview mode setup. Auto-commit is disabled by default for safety. Review all AI-generated changes before merging.

