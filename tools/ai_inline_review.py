#!/usr/bin/env python3
"""
AI Inline Code Reviewer - Posts inline comments on PR diffs
"""
import os
import subprocess
import json
import re
from openai import OpenAI
from github import Github

OPENAI_API_KEY = os.environ.get("OPENAI_API_KEY")
GITHUB_TOKEN = os.environ.get("GITHUB_TOKEN")
REPO = os.environ.get("REPO")
PR_NUMBER = int(os.environ.get("PR_NUMBER", "0"))

if not OPENAI_API_KEY or not GITHUB_TOKEN or not REPO or PR_NUMBER == 0:
    print("Missing env variables. EXIT.")
    exit(1)

client = OpenAI(api_key=OPENAI_API_KEY)
gh = Github(GITHUB_TOKEN)
repo = gh.get_repo(REPO)
pr = repo.get_pull(PR_NUMBER)

# Fetch PR head vs base diff
base = pr.base.sha
head = pr.head.sha

# Fetch refs
subprocess.run(["git", "fetch", "origin", pr.base.ref, pr.head.ref], check=False, capture_output=True)
diff = subprocess.check_output(["git", "diff", f"{base}..{head}"], stderr=subprocess.DEVNULL).decode(errors="ignore")

if not diff or len(diff.strip()) == 0:
    print("No diff found, exiting.")
    exit(0)

prompt = f"""You are an expert Android/JNI reviewer. Given the following git diff, produce a JSON array of inline review items.

Focus areas:
- JNI leaks (ReleaseByteArray/Release* missing)
- NewGlobalRef/DeleteGlobalRef mismatches
- Unused JNI params (should be marked with (void)cast)
- Format specifier mismatches (%d/%zu/%lld)
- Missing null checks before dereferencing
- Concurrency hazards (race conditions)
- Performance hotspots (unnecessary allocations)
- Crypto nonce reuse
- Memory leaks (malloc without free)

Output format - JSON array only:
[
  {{
    "file": "path/to/file.cpp",
    "line": 123,
    "severity": "low|medium|high|critical",
    "comment": "explanation of the issue",
    "suggest": "suggested code snippet or patch hunk (unified diff format)"
  }},
  ...
]

Diff:
{diff}

Important: Return ONLY valid JSON, no markdown code blocks, no explanations outside the JSON."""

try:
    resp = client.chat.completions.create(
        model="gpt-5",
        messages=[{"role": "user", "content": prompt}],
        temperature=0.1,
        max_tokens=2000
    )
except Exception as e:
    print(f"OpenAI API error: {e}")
    exit(1)

# Parse model output
content = resp.choices[0].message.content.strip()

# Try to extract JSON if wrapped in code blocks
json_match = re.search(r'```(?:json)?\s*(\[.*?\])\s*```', content, re.DOTALL)
if json_match:
    content = json_match.group(1)

try:
    comments = json.loads(content)
    if not isinstance(comments, list):
        print("AI output is not a list, exiting.")
        exit(0)
except Exception as e:
    print(f"Failed parsing AI output: {e}")
    print("Raw output:", content[:500])
    exit(1)

# Create review comments
posted = 0
for c in comments:
    file_path = c.get("file", "")
    line = c.get("line", 0)
    severity = c.get("severity", "low")
    comment_text = c.get("comment", "")
    suggest = c.get("suggest", "")
    
    if not file_path or not line or not comment_text:
        continue
    
    body = f"**🤖 AI Review ({severity}):** {comment_text}"
    if suggest:
        body += f"\n\n**Suggested fix:**\n```diff\n{suggest}\n```"
    
    try:
        pr.create_review_comment(
            body=body,
            commit_id=head,
            path=file_path,
            line=line
        )
        posted += 1
        print(f"✓ Commented: {file_path}:{line}")
    except Exception as e:
        print(f"✗ Failed to post comment on {file_path}:{line} - {e}")

print(f"Posted {posted} review comments.")

