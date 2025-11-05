#!/bin/bash
# Git-safe helper functions for idempotent git operations
# Prevents unnecessary `cd` operations and ensures commands work from any directory

set -Eeuo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Determine repository root
# Uses git rev-parse --show-toplevel if in a git repo, otherwise falls back to script directory
ensure_repo_root() {
    local script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    local candidate_root="$(cd "$script_dir/.." && pwd)"
    
    # Try to find git root
    if command -v git &> /dev/null; then
        # Check if we're in a git repo
        if git rev-parse --git-dir &> /dev/null; then
            # Use git to find root (works from any subdirectory)
            REPO_ROOT="$(git rev-parse --show-toplevel)"
            if [[ -n "$REPO_ROOT" && -d "$REPO_ROOT" ]]; then
                echo -e "${GREEN}Repository root: $REPO_ROOT${NC}"
                return 0
            fi
        fi
    fi
    
    # Fallback: check if script directory's parent looks like a repo root
    if [[ -d "$candidate_root/.git" ]]; then
        REPO_ROOT="$candidate_root"
        echo -e "${GREEN}Repository root (from script): $REPO_ROOT${NC}"
        return 0
    fi
    
    # Last fallback: use script directory's parent
    REPO_ROOT="$candidate_root"
    echo -e "${YELLOW}Warning: Could not determine git root, using: $REPO_ROOT${NC}"
    return 0
}

# Safe git wrapper - uses git -C "$REPO_ROOT" instead of cd
safe_git() {
    ensure_repo_root
    
    local current_dir="$PWD"
    local target_dir="$REPO_ROOT"
    
    # Log if we're not already in the repo root
    if [[ "$current_dir" != "$target_dir" ]]; then
        echo -e "${YELLOW}Running git from: $current_dir (target: $target_dir)${NC}"
    fi
    
    # Use git -C to avoid cd
    git -C "$REPO_ROOT" "$@"
}

# Check if we're in a git repository
is_git_repo() {
    ensure_repo_root
    [[ -d "$REPO_ROOT/.git" ]]
}

# Get current branch
get_current_branch() {
    ensure_repo_root
    if is_git_repo; then
        safe_git rev-parse --abbrev-ref HEAD
    else
        echo ""
    fi
}

# Get current commit hash
get_current_commit() {
    ensure_repo_root
    if is_git_repo; then
        safe_git rev-parse --short HEAD
    else
        echo ""
    fi
}

# Check if working directory is clean
is_clean_working_dir() {
    ensure_repo_root
    if ! is_git_repo; then
        return 1
    fi
    
    # Check if there are uncommitted changes
    safe_git diff-index --quiet HEAD -- || return 1
    
    # Check if there are untracked files (optional - can be ignored)
    # safe_git ls-files --others --exclude-standard | grep -q . && return 1
    
    return 0
}

# Export functions for use in other scripts
export -f ensure_repo_root
export -f safe_git
export -f is_git_repo
export -f get_current_branch
export -f get_current_commit
export -f is_clean_working_dir

# Initialize REPO_ROOT if script is sourced
if [[ "${BASH_SOURCE[0]}" != "${0}" ]]; then
    # Script is being sourced, initialize
    ensure_repo_root
    export REPO_ROOT
fi

