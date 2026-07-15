# AgentWorkspace Android

AgentWorkspace is a project-first AI agent workspace for Android. It combines a minimal monochrome interface with durable, task-based agent execution.

## Core capabilities

- Project and task-centered agent workflow
- Configurable AI connections and model selection
- Local and GitHub workspace execution
- Room-backed run state, messages, events, approvals, and leases
- Foreground execution with pause, resume, and cancel controls
- Process-death recovery with uncertain side-effect protection
- Checkpoints, diffs, history, usage, and trust modes

## Architecture

The UI observes persisted Room projections and sends commands through application use cases. Agent execution is owned by a foreground service and supervised through durable run state, leases, and heartbeats. Existing local and GitHub execution engines are isolated behind a compatibility adapter.

## Build

Requirements:

- Android Studio with Android SDK 34
- JDK 17

From the repository root:

```powershell
.\gradlew.bat testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug lintDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Repository scope

Generated build output, local SDK configuration, credentials, logs, analysis graphs, and internal agent-planning artifacts are intentionally excluded from version control.
