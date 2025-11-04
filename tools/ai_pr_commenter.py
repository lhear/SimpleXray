#!/usr/bin/env python3
"""
AI PR Commenter - Posts summary comment to PR with analysis results
"""
import os
import json
import sys
from github import Github

GITHUB_TOKEN = os.environ.get("GITHUB_TOKEN")
PR_NUMBER = int(os.environ.get("PR_NUMBER", "0"))
REPO = os.environ.get("REPO", "")

if not GITHUB_TOKEN or PR_NUMBER == 0 or not REPO:
    print("Missing env variables")
    sys.exit(1)

gh = Github(GITHUB_TOKEN)
repo = gh.get_repo(REPO)
pr = repo.get_pull(PR_NUMBER)

report_file = "ai_report.json"
body = "### 🤖 AI Fixer Bot Report\n\n"

if os.path.exists(report_file):
    try:
        with open(report_file, "r") as f:
            issues = json.load(f)
        
        if isinstance(issues, list) and len(issues) > 0:
            # Group by severity
            severity_counts = {"critical": 0, "high": 0, "medium": 0, "low": 0}
            for i in issues:
                if isinstance(i, dict):
                    sev = i.get("severity", "low").lower()
                    if sev in severity_counts:
                        severity_counts[sev] += 1
            
            body += f"**Summary:** {len(issues)} issue(s) found\n\n"
            body += f"- 🔴 Critical: {severity_counts['critical']}\n"
            body += f"- 🟠 High: {severity_counts['high']}\n"
            body += f"- 🟡 Medium: {severity_counts['medium']}\n"
            body += f"- 🔵 Low: {severity_counts['low']}\n\n"
            
            body += "**Details:**\n\n"
            for i in issues:
                if isinstance(i, dict) and "error" not in i:
                    file_path = i.get("file", "unknown")
                    line = i.get("line", 0)
                    severity = i.get("severity", "low")
                    issue_text = i.get("issue", "No description")
                    body += f"- **{severity}** `{file_path}:{line}` — {issue_text}\n"
                elif isinstance(i, dict) and "error" in i:
                    body += f"- ⚠️ Error: {i.get('error', 'Unknown error')}\n"
        else:
            body += "✅ No issues found!\n"
    except Exception as e:
        body += f"⚠️ Could not parse ai_report.json: {e}\n\n"
        body += "Raw content:\n```\n"
        try:
            body += open(report_file, "r").read()[:1000] + "\n```\n"
        except:
            body += "Could not read file\n```\n"
else:
    body += "⚠️ No ai_report.json found. Analysis may have failed.\n"

# Check if patch was generated
if os.path.exists("auto.patch"):
    patch_size = os.path.getsize("auto.patch")
    if patch_size > 0:
        body += "\n---\n\n"
        body += "📦 **Auto-patch generated** — Check the workflow artifacts for `ai-auto-patch` to download and review.\n"
        body += "\nTo apply the patch locally:\n```bash\n"
        body += "git apply auto.patch\n```\n"

try:
    pr.create_issue_comment(body)
    print("✓ Posted PR comment")
except Exception as e:
    print(f"✗ Failed to post PR comment: {e}")
    sys.exit(1)

