---
name: devops-investigate
description: Investigate failing Jenkins builds of a service. Finds the root cause in build logs, checks Bitbucket
   for related pull requests, verifies whether a merged fix worked, and recommends the next action. Use when the user
   names a service and asks why builds fail or whether a problem is already tracked or fixed.
---

# DevOps Investigate

Input: service name. Number of builds N defaults to 5 (max 25 for Jenkins).
Rules in AGENTS.md apply: read-only, never invent data, link every source.

## Steps

1. **Locate the service.** Find the Jenkins job and the Bitbucket project/repo for the service name. If several match or
   none match, ask the user. Do not guess.
2. **Check builds (Jenkins).**
   - Most services are multibranch (a folder of branch jobs); `get_last_builds` on the service name returns `[]`.
     Run `list_jobs` with `folder` = service name first. If it returns nothing, call `get_last_builds` on the service.
   - Write `/` in branch names as `%2F`, e.g. `service-connector-example/release%2F6.6`.
   - `list_jobs` returns `fullName` with a `ROOT/` prefix. `get_last_builds` and `get_build_log` return 404 with it.
     Drop the prefix and use `service-connector-example/<branch>`.
   - Branch `color`: `red` failed, `yellow` unstable, `aborted`, `blue` success; `_anime` = running. Report red/yellow
     and blue branches.
   - Call `get_last_builds` for every red/yellow branch, `master`, and feature branches with recent activity. For
     "the last N builds", collect across them and say how many came from each branch.
   - The current version is the highest `release/X.Y`, compared numerically (`6.10` > `6.9`). Name it. If it is
     red/yellow, it is the main problem.
3. **Find the root cause (Jenkins).** For each failed build call `get_build_log` and take the first real error
   (`Caused by`, `[ERROR] Tests run`, `BUILD FAILURE`), not the summary. Ignore follow-ons like
   `No integration tests were executed!`. If the tail shows only Maven's summary, raise `tailLines` (max 500).
   Check every failed build and group those with the same cause. If all builds passed, report that and stop.
   Investigate every red/yellow branch, including older releases.
4. **Check if it is tracked (Bitbucket).** Look at open and merged pull requests and recent commits. Match them to the
   error by files, keywords, or time window. Open details of candidate PRs (PR block below).
5. **Did the fix help?** For a merged PR, compare builds before and after the merge time and state whether failures
   with this cause stopped. A fix merged to `master` is not on a release branch until `list_commits` (`branch`) shows
   it. If times are missing, say so.
6. **Recommend.**
   - Fixed: say so and what evidence shows it.
   - Fix open: say what to review or merge.
   - Not fixed, or no PR: say so (untracked), give the diagnosis first, then numbered steps. If the repo files make it
     possible, add a proposed code or config change (read the files first). Text only, do not apply it.

## Superseded older release

An older red `release/X.Y` with a newer blue release is "superseded by release/X.Y". Still investigate it:

1. Find the root cause from its failed builds.
2. Find the fixing PR on the newer branch via `list_commits` and `list_pull_requests` (`state` = `MERGED`), starting
   from the first successful build after the failure.
3. Report it with the PR block and cite the newer branch's successful build link. Mark the PR "unverified" if inferred
   only from timing.
4. State that the older branch is closed: `master` is bumped to the next minor version
   (a version-bump commit) after each merge and the next `release/X.Y` is cut from it, so
   the fix already lives in the newer release. Mark this "unverified" if the pom files were not read.

Do NOT report "is it fixed? No", and do NOT recommend fixing, cherry-picking, backporting, rebuilding, or list fix
steps for it.

## PR block

Use `get_pull_request` and `get_pull_request_changes`.
- Link, title, author, merge date, target branch.
- Builds/branches it fixed, with links.
- Changed files with change type and +added/-removed counts.
- 1-3 sentences on what changed, tied to the root cause (e.g. "re-encrypted the `client-id`, `client-secret` and
  `password` values in the test config").
- Optional key diff lines, about 20 max. Never show secret values; keep `****` masking, and for encrypted/rotated
  values say only that the value changed. If the diff is truncated, summarise per file and say so.

## Report

- Service, job, repo, builds checked
- Failed builds and root cause (with log excerpt)
- Related PRs and state
- Verdict: fixed / not fixed / open fix / no fix
- Recommendation
- Unverified items, if any
