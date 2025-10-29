# DevSecOps Jenkins Shared Library

This repository provides a Jenkins Shared Library **and** an accompanying reference Jenkins pipeline that orchestrate popular application security and cloud-native scanning tools. The pipeline focuses on delivering a practical DevSecOps workflow that now supports multi-repository synchronization, Docker image publishing metadata, and end-to-end GitLab commit status reporting.

## Jenkins Pipeline Overview

The top-level `Jenkinsfile` demonstrates how to run the shared library in a hardened CI/CD process. Key characteristics include:

- **Environment bootstrapping:** The first stage performs a clean checkout of this repository, authenticates to the configured Docker registry, and synchronizes any extra Git repositories defined in [`config/repositories.yaml`](config/repositories.yaml). Repositories are described through a map of entries that declare the Git URL, the destination folder, the preferred branch, and a type label such as `backend`, `frontend`, or `api`.
- **Docker image metadata:** Pipeline parameters expose `DOCKER_IMAGE`, `DOCKER_TAG`, and `DOCKER_REGISTRY`, while credentials referenced by `DOCKER_REGISTRY_CREDENTIALS` are used to log in automatically before any build activity takes place.
- **Commit status visibility:** Every pipeline stage is wrapped with `gitLabCommitStatus` so GitLab merge requests and commits reflect the precise stage outcome.
- **Report publishing:** Maven Surefire HTML reports are generated and published via the Jenkins HTML Publisher plugin alongside the existing JUnit XML archiving.

> **Tip:** Update `config/repositories.yaml` to enable cloning of additional services that should be present in the Jenkins workspace. Set `enabled: true` for the repositories you need, and provide per-entry `branch` and `type` values to organize the checkout targets.

## Tooling Overview

| Use Case | Best Match | Why |
| --- | --- | --- |
| Web app security testing | 🕷️ OWASP ZAP | Strong DAST scanner for live apps |
| Container & IaC scanning | 🐳 Trivy | One tool for images + configs + secrets |
| Deep dependency vulnerability scanning | 🧬 Grype | SBOM-based, strong CVE coverage |
| Source code vulnerability & linting | 🧠 Semgrep | Fast, customizable static rules |
| Developer-friendly all-in-one solution | ⚡ Snyk | Combines SAST + SCA + IaC + Fix guidance |
| Legacy Java project CVE scanning | ☕ OWASP Dependency-Check | Built for Maven/Gradle ecosystems |

## Library Usage

1. Add this repository as a Jenkins Shared Library (`Manage Jenkins` → `Configure System` → `Global Pipeline Libraries`).
2. Reference the library in your pipeline using `@Library('devsecops') _` (adjust the name to match your configuration).
3. Invoke the `devSecOpsPipeline` step inside a pipeline stage and enable the scanners you need.

```groovy
@Library('devsecops') _

pipeline {
    agent any

    stages {
        stage('Security Scans') {
            steps {
                devSecOpsPipeline(
                    parallel: false,
                    zap: [
                        enabled: true,
                        targetUrl: 'https://example.com',
                        reportName: 'zap-report.xml'
                    ],
                    grype: [
                        enabled: true,
                        image: 'example/app:latest',
                        failOn: 'high'
                    ],
                    trivy: [
                        enabled: true,
                        type: 'fs',
                        target: '.',
                        format: 'table'
                    ],
                    semgrep: [
                        enabled: true,
                        config: 'p/owasp-top-ten'
                    ],
                    snyk: [
                        enabled: true,
                        command: 'test',
                        target: '--file=package.json',
                        tokenEnvVar: 'SNYK_TOKEN'
                    ],
                    dependencyCheck: [
                        enabled: true,
                        project: 'legacy-app',
                        outputDir: 'dependency-check'
                    ]
                )
            }
        }
    }
}
```

## Configuration Options

Each scanner accepts a configuration map. Key options include:

### OWASP ZAP (`zap`)
- `targetUrl` (**required**): URL of the running web application to scan.
- `image`: Docker image to use (default: `owasp/zap2docker-stable`).
- `baselineCommand`: Script to invoke within the container (default: `zap-baseline.py`).
- `reportName`: Output XML report name (default: `zap-report.xml`).
- `args`: Additional command-line arguments (default: `-a`).

### Grype (`grype`)
- One of `image`, `sbom`, or `dir` (**exactly one required**).
- `containerImage`: Docker image for Grype (default: `anchore/grype:latest`).
- `failOn`: Minimum severity to fail the build (default: `medium`).

### Trivy (`trivy`)
- `type`: Scan type (`fs`, `image`, `config`, etc.; default: `fs`).
- `target`: File system path or image reference (default: `.`).
- `format`: Report format (`table`, `json`, `sarif`, etc.; default: `table`).
- `output`: Optional report file path.
- `severity`: Severities to include (default: `CRITICAL,HIGH`).
- `ignoreUnfixed`: Skip unfixed CVEs (default: `true`).
- `hideProgress`: Disable progress bar in CI logs (default: `true`).
- `args`: Extra command-line arguments.

### Semgrep (`semgrep`)
- `config`: Semgrep configuration (`auto`, registry IDs, or local files; default: `auto`).
- `severity`: Limit findings to specific severities.
- `output`: Optional output file.
- `args`: Extra command-line arguments.

### Snyk (`snyk`)
- `command`: Snyk CLI command (`test`, `monitor`, etc.; default: `test`).
- `target`: CLI target arguments such as `--file=package.json`.
- `tokenEnvVar`: Jenkins environment variable containing the Snyk API token (default: `SNYK_TOKEN`).
- `args`: Extra command-line arguments.

### OWASP Dependency-Check (`dependencyCheck`)
- `project`: Project name for reports (default: `dependency-check`).
- `format`: Report format (default: `ALL`).
- `outputDir`: Directory for reports (default: `dependency-check-report`).
- `suppressionFile`: Optional suppression XML path.
- `args`: Extra command-line arguments.

## Parallel Execution

Set `parallel: true` to run all enabled scanners concurrently. Jenkins will create separate stages for each scanner. When `parallel` is `false`, scans run sequentially in the order defined above.

## Requirements

- Jenkins agents must have Docker available to run the scanner containers.
- Necessary credentials or tokens (for example, `SNYK_TOKEN`) must be configured in the Jenkins environment.
- Target applications should be accessible from the Jenkins agent running OWASP ZAP.

## Reports

Most scanners write reports into the workspace. Archive them or publish results after the `devSecOpsPipeline` step using Jenkins post actions or additional stages tailored to your organization.

