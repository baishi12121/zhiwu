# Service Interface Impl Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split business service classes into `service` interfaces and `service.impl` implementation classes.

**Architecture:** The `service` package exposes contracts used by controllers, MQ consumers, config, and other services. Concrete business logic moves to `service.impl` with `XxxServiceImpl` names and Spring `@Service` annotations. Existing model-like files and already-interface files remain in place.

**Tech Stack:** Java 17, Spring Boot 3.5.14, Maven multi-module, PowerShell verification scripts.

## Global Constraints

- Do not revert unrelated working tree changes.
- Preserve existing public method signatures for callers.
- Keep `PayService` as the existing payment strategy interface.
- Keep `PayRequest`, `PayResponse`, and `PayNotifyResult` as model objects in `mall-order-service`.
- Keep `mall-common-oss` out of this business service refactor.

---

### Task 1: Structure Guard

**Files:**
- Create: `scripts/check_service_interface_impl.ps1`

**Interfaces:**
- Consumes: Java source files under `mall-*-service/src/main/java/**/service`.
- Produces: a non-zero exit code when business service implementation classes remain directly under `service`.

- [ ] **Step 1: Write the failing structure check**

Create a PowerShell script that scans business service source files and fails when a concrete service class remains under a `service` package outside `service.impl`, excluding `PayService`, pay DTO/model files, and common OSS.

- [ ] **Step 2: Run it to verify it fails**

Run: `powershell -ExecutionPolicy Bypass -File scripts/check_service_interface_impl.ps1`
Expected: FAIL listing existing concrete service classes such as `AuthService.java`.

### Task 2: Interface And Impl Migration

**Files:**
- Modify: all business module Java files under `mall-*-service/src/main/java/**/service/*.java`
- Create: corresponding `service/impl/XxxServiceImpl.java` files
- Modify: callers importing concrete services when necessary

**Interfaces:**
- Consumes: public methods from each current concrete service class.
- Produces: `XxxService` interface with the same public method signatures, and `XxxServiceImpl implements XxxService`.

- [ ] **Step 1: Generate interfaces**

For each concrete service class, replace the original file with an interface containing its public method signatures and required imports. Do not include private helpers, fields, annotations, or implementation code.

- [ ] **Step 2: Move implementations**

Move each original implementation into `service.impl`, rename the class to `XxxServiceImpl`, update package/imports, add `implements XxxService`, and retain Spring annotations.

- [ ] **Step 3: Update references**

Update tests and any direct instantiation to use `XxxServiceImpl` where they need constructors. Keep Spring-managed collaborators injecting interface types.

### Task 3: Verification

**Files:**
- Run-only verification task.

**Interfaces:**
- Consumes: migrated Java source tree.
- Produces: passing structure check and Maven compile/test evidence.

- [ ] **Step 1: Run structure check**

Run: `powershell -ExecutionPolicy Bypass -File scripts/check_service_interface_impl.ps1`
Expected: PASS with no direct concrete business services under `service`.

- [ ] **Step 2: Run Maven verification**

Run: `mvn -f E:/zhiwu-mall/pom.xml test`
Expected: exit code 0, or report exact compilation/test failures if existing unrelated work prevents a full pass.
