#!/usr/bin/env python3
"""
AI Static Analyzer - Analyzes PR diff and produces JSON report
"""
import os
import subprocess
import json
import sys
from openai import OpenAI

OPENAI_API_KEY = os.environ.get("OPENAI_API_KEY")
PR_NUMBER = os.environ.get("PR_NUMBER", "0")
REPO = os.environ.get("REPO", "")

if not OPENAI_API_KEY:
    print("Set OPENAI_API_KEY")
    sys.exit(1)

client = OpenAI(api_key=OPENAI_API_KEY)

# Get base branch (default to main, fallback to master)
base_branch = "main"
try:
    result = subprocess.run(["git", "branch", "-r"], capture_output=True, text=True, check=False)
    if "origin/main" in result.stdout:
        base_branch = "main"
    elif "origin/master" in result.stdout:
        base_branch = "master"
except:
    pass

# Get diff of PR HEAD vs base
try:
    diff = subprocess.check_output(
        ["git", "diff", "--unified=3", f"origin/{base_branch}...HEAD"],
        stderr=subprocess.DEVNULL
    ).decode(errors="ignore")
except Exception as e:
    print(f"Error getting diff: {e}")
    # Fallback to current branch vs origin
    try:
        diff = subprocess.check_output(
            ["git", "diff", "--unified=3", "HEAD~1"],
            stderr=subprocess.DEVNULL
        ).decode(errors="ignore")
    except:
        diff = ""

if not diff or len(diff.strip()) == 0:
    print("No diff found, creating empty report.")
    json.dump([], open("ai_report.json", "w"), indent=2)
    sys.exit(0)

prompt = f"""You are a senior static analyzer for Android/JNI code. Analyze the diff and return a JSON array of issues.

Focus on:
- JNI memory leaks (missing Release* calls)
- GlobalRef leaks (missing DeleteGlobalRef)
- Format string mismatches
- Null pointer dereferences
- Unused parameters (should cast to void)
- Race conditions
- Performance issues
- Security vulnerabilities

Output format - JSON array only:
[
  {{
    "severity": "low|medium|high|critical",
    "file": "path/to/file.cpp",
    "line": 123,
    "issue": "Description of the issue",
    "proposed_fix": "Code snippet or description of fix"
  }},
  ...
]

Diff:
{diff}

Important: Return ONLY valid JSON array, no markdown code blocks, no explanations outside the JSON."""

try:
    res = client.chat.completions.create(
        model="gpt-5",
        messages=[{"role": "user", "content": prompt}],
        temperature=0.0,
        max_tokens=2000
    )
except Exception as e:
    print(f"OpenAI API error: {e}")
    sys.exit(1)

content = res.choices[0].message.content.strip()

# Try to extract JSON if wrapped in code blocks
import re
json_match = re.search(r'```(?:json)?\s*(\[.*?\])\s*```', content, re.DOTALL)
if json_match:
    content = json_match.group(1)

# Try to parse JSON
try:
    issues = json.loads(content)
    if not isinstance(issues, list):
        print("AI output is not a list, wrapping in array.")
        issues = [{"raw_output": content}]
    json.dump(issues, open("ai_report.json", "w"), indent=2)
    print(f"Saved ai_report.json with {len(issues)} issues")
except Exception as e:
    print(f"AI output not valid JSON: {e}")
    print("Writing raw output to ai_report.json")
    open("ai_report.json", "w").write(json.dumps([{"error": "Parse failed", "raw_output": content}], indent=2))

