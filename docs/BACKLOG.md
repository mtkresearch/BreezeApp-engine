# BreezeApp-engine Technical Backlog

**Purpose**: Record future refactoring and architectural improvements that are out-of-scope for the current documentation reorganization.

**Status**: Planning Only (No Active Work)

---

## Priority 1: Single Interface Refactor

**Goal**: Make `EdgeAI` the sole interface for all engine interactions, including configuration.

**Current Gap**: `EngineSettingsActivity` bypasses `EdgeAI` and talks directly to `RunnerManager` (internal implementation).

**Target State**: All engine interactions (inference + configuration) go through `EdgeAI` SDK.

### Phase 1: AIDL Extension (Estimated: 2-3 weeks)

**Objective**: Extend the AIDL contract to support configuration operations.

**Changes to `IBreezeAppEngineService.aidl`**:
```java
// Configuration Management (NEW)
void setRunner(String capability, String runnerName);
void setRunnerParameter(String runnerName, String key, String value);
List<String> getAvailableRunners(String capability);
Map getRunnerParameters(String runnerName);
```

**Versioning Strategy**:
- Use `@nullable` annotations for backward compatibility
- Increment API version from `1` to `2`
- Maintain support for v1 clients (read-only mode)

**Risks**:
- Breaking existing clients if not properly versioned
- AIDL serialization overhead for complex parameter maps

---

### Phase 2: EdgeAI SDK Enhancement (Estimated: 1-2 weeks)

**Objective**: Expose configuration APIs in the `EdgeAI` Kotlin SDK.

**New Public Methods**:
```kotlin
// Runner Discovery
suspend fun EdgeAI.getRunners(capability: CapabilityType): List<RunnerInfo>

// Runner Selection
suspend fun EdgeAI.selectRunner(capability: CapabilityType, runnerName: String): Result<Unit>

// Configuration
suspend fun EdgeAI.configure(runnerName: String, params: Map<String, Any>): Result<Unit>
suspend fun EdgeAI.getConfiguration(runnerName: String): Map<String, Any>
```

**Dependencies**:
- Requires Phase 1 (AIDL Extension) to be complete
- Requires updated `RunnerInfo` parcelable with full parameter schemas

---

### Phase 3: Settings UI Refactor (Estimated: 1 week)

**Objective**: Rewrite `EngineSettingsActivity` to use `EdgeAI` SDK exclusively.

**Changes**:
- Remove direct `RunnerManager` access
- Replace with `EdgeAI.getRunners()`, `EdgeAI.selectRunner()`, etc.
- Validate that UI behavior is identical

**Success Criteria**:
- Settings UI works identically via SDK
- No direct imports of `com.mtkresearch.breezeapp.engine.runner.core.*` in UI code
- Litmus Test passes: "Could we move EngineSettingsActivity to a separate app?" → **Yes**

---

## Priority 2: Documentation Improvements

**Goal**: Maintain documentation quality as the codebase evolves.

### Task 2.1: I18n Strategy Decision
**Status**: ✅ **DECIDED - Option A (Delete)**

**Decision Date**: 2026-01-06  
**Chosen Option**: **Option A** - Delete `docs/i18n/` directory

**Rationale**: Simplest approach with zero maintenance cost. Option D (AI-powered translation) remains in backlog for future implementation when resources allow.

**Current State**: `docs/i18n/` contains 5 Chinese (zh-TW) translations that will go stale after doc updates in Milestone 3.

**Options Considered**:

1. **Option A: Delete** `docs/i18n/` ✅ **CHOSEN**
   - **Pros**: Zero maintenance cost, eliminates sync issues
   - **Cons**: Loses existing translations, not friendly for Chinese-speaking engineers
   - **Effort**: 5 minutes (delete directory)

2. **Option B: Freeze with Warning** (Low effort)
   - **Pros**: Preserves existing work, minimal effort
   - **Cons**: Confusing for users (outdated translations)
   - **Effort**: 10 minutes (add warning banner to each file)

3. **Option C: Manual Maintenance** (High cost)
   - **Pros**: Always accurate translations
   - **Cons**: High maintenance burden (2x documentation work)
   - **Effort**: Ongoing (update translations for every doc change)

4. **Option D: AI-Powered Automated Translation** 📋 **BACKLOG**
   - **Pros**: Low maintenance, always up-to-date, scalable
   - **Cons**: Initial setup effort, requires CI/CD integration
   - **Effort**: 1-2 days initial setup, then automated
   - **Status**: Moved to backlog for future implementation

**Option D Implementation Plan** (For Future Reference):

```yaml
# .github/workflows/translate-docs.yml
name: Auto-Translate Documentation

on:
  push:
    paths:
      - 'docs/**/*.md'
      - '!docs/i18n/**'  # Don't trigger on translation changes

jobs:
  translate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Translate to Chinese (Traditional)
        uses: openai/gpt-translate-action@v1  # Example action
        with:
          source-lang: en
          target-lang: zh-TW
          source-dir: docs/
          output-dir: docs/i18n/
          model: gpt-4  # Or use Claude API, Gemini API, etc.
          
      - name: Create PR with translations
        uses: peter-evans/create-pull-request@v5
        with:
          commit-message: "chore: auto-translate docs to zh-TW"
          title: "🤖 Auto-translated documentation updates"
          body: "Automated translation of English docs to Traditional Chinese"
```

**Alternative Tools for Option D**:
- **GitHub Actions**: `gpt-translate-action`, `deepl-action`
- **Self-hosted**: Use `EdgeAI` SDK with LLM runner for translation (dogfooding!)
- **Commercial**: DeepL API, Google Translate API

---

### Task 2.2: API Documentation Sync
**Status**: Future Work

**Objective**: Ensure `docs/api/` matches actual AIDL contracts.

**Actions**:
- Auto-generate API docs from AIDL files (consider using `aidl2md` tool)
- Add CI check to detect drift between docs and implementation

---

## Priority 3: Build & Release Improvements

**Goal**: Streamline the release process.

### Task 3.1: Automated Release Notes
**Status**: Future Work

**Objective**: Auto-generate release notes from Git commits.

**Tools**: Consider `conventional-changelog` or GitHub Actions

---

### Task 3.2: EdgeAI SDK Versioning
**Status**: Future Work

**Objective**: Implement semantic versioning for `EdgeAI` SDK releases.

**Current**: Manual version bumps in `build.gradle.kts`
**Target**: Automated versioning based on API changes

---

## Priority 4: Testing Strategy

**Goal**: Improve test coverage for AIDL interface.

### Task 4.1: AIDL Contract Tests
**Status**: Future Work

**Objective**: Add integration tests for the AIDL boundary.

**Test Cases**:
- Client connects to service successfully
- Request/Response flow works for all capabilities (LLM, ASR, TTS, etc.)
- Configuration changes persist across service restarts

---

## Appendix: Rejected Ideas

### Idea: Convert Engine to AAR Library
**Reason for Rejection**: Service App model provides better isolation (engine crashes don't affect client). Independent update capability is critical for AI model evolution.

**Decision Date**: 2026-01-06

---

## Maintenance

**Last Updated**: 2026-01-06  
**Owner**: MX (Repository Architect)  
**Review Cadence**: Quarterly
