#!/usr/bin/env python3
"""
AI Patch Generator - Creates unified git patch from AI analysis report
"""
import os
import json
import subprocess
import sys
from openai import OpenAI

OPENAI_API_KEY = os.environ.get("OPENAI_API_KEY")
if not OPENAI_API_KEY:
    print("Set OPENAI_API_KEY")
    sys.exit(1)

client = OpenAI(api_key=OPENAI_API_KEY)

report_path = "ai_report.json"
if not os.path.exists(report_path):
    print("No ai_report.json found, exiting.")
    sys.exit(0)

with open(report_path, "r") as f:
    try:
        issues = json.load(f)
        if not isinstance(issues, list):
            print("ai_report.json is not a list - aborting")
            sys.exit(0)
    except Exception as e:
        print(f"ai_report.json not valid JSON - aborting: {e}")
        sys.exit(0)

if len(issues) == 0:
    print("No issues in report, exiting.")
    sys.exit(0)

# Filter out errors and low severity issues (optional)
filtered_issues = [
    i for i in issues
    if isinstance(i, dict) and i.get("severity") not in (None, "low") and "error" not in i
]

if len(filtered_issues) == 0:
    print("No actionable issues after filtering, exiting.")
    sys.exit(0)

# Create concise instruction for the model
fix_instructions = []
for it in filtered_issues:
    file_path = it.get("file", "")
    line = it.get("line", 0)
    issue = it.get("issue", "")
    proposed_fix = it.get("proposed_fix", "")
    
    if not file_path or not line:
        continue
    
    fix_instructions.append(f"{file_path}:{line} - {issue} -> {proposed_fix}")

if len(fix_instructions) == 0:
    print("No valid fix instructions, exiting.")
    sys.exit(0)

# Read relevant source files for context
file_contexts = {}
for it in filtered_issues:
    file_path = it.get("file", "")
    if file_path and os.path.exists(file_path):
        try:
            with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
                file_contexts[file_path] = f.read()
        except:
            pass

prompt = f"""You are a trustworthy C/C++/Kotlin patch generator. Given the file/line fixes below, produce a single unified git patch (git diff -U3 style) that applies minimal but correct changes.

Rules:
- Use existing context lines from the files
- Only modify lines that need fixing
- Preserve code style and indentation
- If a suggested fix is not directly applicable, produce a safe no-op comment insertion near the line
- Ensure patch is syntactically correct and applies cleanly

Fixes to apply:
{chr(10).join(fix_instructions[:20])}  # Limit to 20 fixes to avoid token limits

File contexts (for reference):
{chr(10).join(f"{k}:\n{v[:500]}..." for k, v in list(file_contexts.items())[:5])}

Output format: Return ONLY the unified diff patch, no markdown code blocks, no explanations. Start directly with "diff --git"."""

try:
    res = client.chat.completions.create(
        model="gpt-5",
        messages=[{"role": "user", "content": prompt}],
        temperature=0.0,
        max_tokens=2500
    )
except Exception as e:
    print(f"OpenAI API error: {e}")
    sys.exit(1)

patch = res.choices[0].message.content.strip()

# Remove markdown code blocks if present
if patch.startswith("```"):
    lines = patch.split("\n")
    if lines[0].startswith("```"):
        lines = lines[1:]
    if lines[-1].strip() == "```":
        lines = lines[:-1]
    patch = "\n".join(lines)

# Validate patch format
if not patch.startswith("diff --git"):
    print("Warning: Patch doesn't start with 'diff --git', may be malformed.")

open("auto.patch", "w", encoding="utf-8").write(patch)
print("Wrote auto.patch")

# Attempt to validate patch
ret = subprocess.call(["git", "apply", "--check", "auto.patch"], stderr=subprocess.DEVNULL)
if ret == 0:
    print("✓ Patch applies cleanly (validation check passed).")
else:
    print("⚠ Patch does not apply cleanly; reviewer should inspect manually.")

