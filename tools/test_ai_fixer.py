#!/usr/bin/env python3
"""
Test script for AI Fixer Bot - validates script structure and syntax
"""
import os
import sys
import subprocess


def test_script_syntax(script_path):
    """Test if a Python script has valid syntax"""
    try:
        result = subprocess.run(
            [sys.executable, "-m", "py_compile", script_path],
            capture_output=True,
            text=True
        )
        if result.returncode == 0:
            print(f"✓ {script_path} - Syntax OK")
            return True
        else:
            print(f"✗ {script_path} - Syntax Error:")
            print(result.stderr)
            return False
    except Exception as e:
        print(f"✗ {script_path} - Error: {e}")
        return False


def test_imports(script_path):
    """Test if script imports are valid (without running)"""
    try:
        with open(script_path, 'r') as f:
            content = f.read()

        # Check for required imports
        required = ['import os', 'import sys', 'import json']
        found = []
        for req in required:
            if req in content or req.replace('import ', 'from ') in content:
                found.append(req)

        # Check for optional imports
        optional = ['from openai import', 'from github import']
        optional_found = []
        for opt in optional:
            if opt in content:
                optional_found.append(opt)

        print(f"  Required imports: {len(found)}/{len(required)}")
        if optional_found:
            print(
                f"  Optional imports: {', '.join(opt.split()[-1] for opt in optional_found)}")
        return True
    except Exception as e:
        print(f"✗ Error reading {script_path}: {e}")
        return False


def test_workflow_files():
    """Test workflow YAML files exist"""
    workflows = [
        '.github/workflows/inline-fixer.yml',
        '.github/workflows/fixer.yml'
    ]

    all_ok = True
    for wf in workflows:
        if os.path.exists(wf):
            print(f"✓ {wf} - Exists")
        else:
            print(f"✗ {wf} - Missing")
            all_ok = False

    return all_ok


def main():
    print("=" * 60)
    print("AI Fixer Bot - Test Suite")
    print("=" * 60)

    # Test scripts
    scripts = [
        'tools/ai_inline_review.py',
        'tools/ai_analyze.py',
        'tools/ai_patch.py',
        'tools/ai_pr_commenter.py'
    ]

    print("\n[1] Testing Python script syntax...")
    syntax_ok = True
    for script in scripts:
        if not test_script_syntax(script):
            syntax_ok = False

    print("\n[2] Testing script imports...")
    for script in scripts:
        print(f"\n{script}:")
        test_imports(script)

    print("\n[3] Testing workflow files...")
    workflow_ok = test_workflow_files()

    print("\n[4] Testing file structure...")
    required_files = [
        'tools/__init__.py',
        'docs/review-checklist.md',
        'README_AI_FIXER.md',
        '.github/pull_request_template.md'
    ]

    for file in required_files:
        if os.path.exists(file):
            print(f"✓ {file}")
        else:
            print(f"✗ {file} - Missing")

    print("\n" + "=" * 60)
    if syntax_ok and workflow_ok:
        print("✓ All basic tests passed!")
        print("\nNext steps:")
        print("1. Add OPENAI_API_KEY to GitHub Secrets")
        print("2. Create a test PR to trigger workflows")
        print("3. Check GitHub Actions for workflow execution")
    else:
        print("✗ Some tests failed - review errors above")
    print("=" * 60)


if __name__ == "__main__":
    main()

