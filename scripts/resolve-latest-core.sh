#!/usr/bin/env bash
set -euo pipefail

# Workflow-run listing can return stale history; resolve through published artifacts.
candidates=$(gh api 'repos/yuhaiin/yuhaiin/actions/artifacts?name=yuhaiin.aar&per_page=100' --jq '
  .artifacts
  | map(select(.name == "yuhaiin.aar" and .expired == false and .workflow_run.head_branch == "main"))
  | sort_by([.workflow_run.id, .id]) | reverse | .[]
  | [.id, .workflow_run.id] | @tsv
')

while IFS=$'\t' read -r artifact_id core_run; do
  [ -n "$artifact_id" ] || continue
  core_commit=$(gh api "repos/yuhaiin/yuhaiin/actions/runs/$core_run" --jq '
    select(.status == "completed" and .conclusion == "success"
      and .head_branch == "main" and .path == ".github/workflows/go.yml") | .head_sha
  ')
  [ -n "$core_commit" ] || continue

  printf 'CORE_ARTIFACT_ID=%s\nCORE_RUN_ID=%s\nCORE_COMMIT=%s\n' "$artifact_id" "$core_run" "$core_commit"
  printf 'Selected AAR artifact %s from run %s, commit %s\n' "$artifact_id" "$core_run" "$core_commit" >&2
  exit 0
done <<< "$candidates"

echo 'No available AAR from a successful upstream Go build on main was found.' >&2
exit 1
