#!/usr/bin/env python3
"""Check or apply PGR4 Android SDK patches onto an FH2 Recompiled checkout."""

import argparse
from pathlib import Path
import subprocess
import sys

EXPECTED_COMMIT = "ff062497da83f0c9ca8f9da924e90feab55ac416"
PATCHES = [
    ("0001-xmp-capture-output.patch", []),
    ("0003-vulkan-profile-xenos-bins.patch", []),
    ("0004-profile-pgr4-pm4-bin-replay.patch", []),
    ("0005-vulkan-hoist-shared-memory-uploads.patch", ["--ignore-whitespace"]),
    ("0006-vulkan-stage1-stage2-optimizations.patch", []),
    ("0007-vulkan-stage3-optimizations.patch", []),
]


def run_git(repo: Path, *args: str, check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["git", "-C", str(repo), *args],
        capture_output=True,
        text=True,
        check=check,
    )


def main() -> int:
    repo_root = Path(__file__).resolve().parents[2]
    patches_dir = repo_root / "android" / "patches"
    default_sdk_repo = repo_root.parent / "fh2-reference"

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--sdk-repo",
        type=Path,
        default=default_sdk_repo,
        help=f"Path to fh2-reference git repository (default: {default_sdk_repo})",
    )
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument(
        "--status",
        action="store_true",
        help="Verify HEAD commit and check which patches are applied or pending",
    )
    mode.add_argument(
        "--apply",
        action="store_true",
        help="Apply all SDK patches to a clean fh2-reference working tree",
    )
    args = parser.parse_args()

    sdk_repo = args.sdk_repo.resolve()
    if not (sdk_repo / ".git").exists():
        print(f"ERROR: Not a git repository: {sdk_repo}", file=sys.stderr)
        return 1

    head = run_git(sdk_repo, "rev-parse", "HEAD").stdout.strip()
    if head != EXPECTED_COMMIT:
        print(
            f"WARNING: SDK repo HEAD is {head}, expected {EXPECTED_COMMIT}",
            file=sys.stderr,
        )
    else:
        print(f"SDK repo HEAD matches pinned commit ({head[:12]}).")

    dirty = run_git(sdk_repo, "status", "--porcelain").stdout.strip()

    if args.status:
        if not dirty:
            print("Working tree is clean (no patches applied).")
            for name, flags in PATCHES:
                patch_path = patches_dir / name
                res = run_git(sdk_repo, "apply", "--check", *flags, str(patch_path), check=False)
                state = "cleanly applicable" if res.returncode == 0 else "CONFLICT"
                print(f"  {name}: {state}")
            return 0

        print("Working tree has local modifications:")
        for name, flags in PATCHES:
            patch_path = patches_dir / name
            rev = run_git(
                sdk_repo, "apply", "--reverse", "--check", *flags, str(patch_path), check=False
            )
            fwd = run_git(sdk_repo, "apply", "--check", *flags, str(patch_path), check=False)
            if rev.returncode == 0:
                state = "applied"
            elif fwd.returncode == 0:
                state = "NOT applied"
            else:
                state = "modified/stacked (depends on surrounding patches)"
            print(f"  {name}: {state}")
        return 0

    if args.apply:
        if dirty:
            print(
                "ERROR: SDK repo working tree is not clean. Reset it before --apply.",
                file=sys.stderr,
            )
            return 1
        for name, flags in PATCHES:
            patch_path = patches_dir / name
            if not patch_path.is_file():
                print(f"ERROR: Missing patch {patch_path}", file=sys.stderr)
                return 1
            res = run_git(sdk_repo, "apply", *flags, str(patch_path), check=False)
            if res.returncode != 0:
                print(f"ERROR: Failed to apply {name}:\n{res.stderr}", file=sys.stderr)
                return 1
            print(f"Applied {name}")
        print("All SDK patches applied successfully.")
        return 0

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
