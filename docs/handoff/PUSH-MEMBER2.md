# Review and push the fixed Member 2 work

This directory is an independent checkout. The branch is **member2-submission-snapshot**, tracking the same branch on https://github.com/mhdAfrin/emergency-help-network.git. The starting commit is 8bdf413. Existing member1-foundation/ and member2-review/ were preserved. No commit/push/merge is performed by the repair session.

From PowerShell:

```powershell
cd "C:\Users\Afrin\Desktop\5TH SEM\Network Programming\project\member2-fixed"
git branch --show-current
git status
git diff --stat
.\application\scripts\project.ps1 build -Offline
.\application\scripts\project.ps1 smoke
```

Use build without -Offline when dependencies are not already cached. Review the changed source and actual evidence. Member 2 should use their own real Git identity when committing their work; do not invent authors or reviewer approvals.

When the student explicitly requests/approves committing and pushing:

```powershell
git add README.md application docs
git commit -m "fix: complete Member 2 reporting and assignment workflow"
git push origin HEAD:member2-submission-snapshot
```

No new branch name is necessary. Do not push to main or force-push. If the remote branch advanced, fetch and inspect the new commits and resolve differences before a normal push. Open/update a PR from member2-submission-snapshot to main, obtain another member's review and merge before Member 3 continues.

The entire application/.local directory, module target directories and private config stay ignored. The member folders are separate local repositories; do not run Git commands from their shared project/ container.
