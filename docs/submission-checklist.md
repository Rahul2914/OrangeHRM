# Submission Checklist

Status of each item at the time of submission.

| Check | Status |
|---|---|
| Tenant URL correct (`opensource-demo.orangehrmlive.com`) | Done |
| Admin credentials valid | Done - used in every live run |
| API auth and employee endpoints match the tenant | Done - verified by `shouldVerifyEmployeeThroughApi` |
| `smoke` suite executed live | Done - 5 passed, 0 failed |
| `role-based` suite executed live | Done - 1 passed, 0 failed |
| `regression` suite executed live | Done - 6 passed, 0 failed |
| Allure report generated and committed | Done - `report/allure` |
| TestNG report committed | Done - `report/testng/index.html` |
| Screenshot and video captured on a real failure | Done - `report/artifacts`, from a deliberate wrong-password run |
| Restricted-role coverage is genuinely executed, not described | Done - the test provisions and then removes its own ESS user |
| Only synthetic test data in the repository | Done - all records are generated and deleted by the suite |
| CI workflow present | Done - `.github/workflows/ci.yml` |

One item is owner action rather than code: the GitHub Actions secrets referenced by the workflow
must exist in the repository settings before the first CI run, otherwise the suite starts against
blank credentials and fails at login.

Expected repository contents:

- source code,
- GitHub Actions workflow,
- generated report artifacts,
- README with setup, execution, and design decisions.