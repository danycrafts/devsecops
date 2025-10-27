# DevSecOps Jenkins Plugin

This repository packages a DevSecOps-focused Jenkins Pipeline shared library as a first-class Jenkins plugin. It orchestrates
popular application security and cloud-native scanning tools while bundling build tooling, linting, and release configuration so
teams can publish the plugin to an update center or internal artifact repository with minimal customization.

## Tooling Overview

| Use Case | Best Match | Why |
| --- | --- | --- |
| Web app security testing | 🕷️ OWASP ZAP | Strong DAST scanner for live apps |
| Container & IaC scanning | 🐳 Trivy | One tool for images + configs + secrets |
| Deep dependency vulnerability scanning | 🧬 Grype | SBOM-based, strong CVE coverage |
| Source code vulnerability & linting | 🧠 Semgrep | Fast, customizable static rules |
| Developer-friendly all-in-one solution | ⚡ Snyk | Combines SAST + SCA + IaC + Fix guidance |
| Legacy Java project CVE scanning | ☕ OWASP Dependency-Check | Built for Maven/Gradle ecosystems |

## Getting Started

### Build the Plugin

```bash
mvn --batch-mode verify
```

The build runs Spotless, Checkstyle, and SpotBugs to keep both Java and Groovy sources linted before packaging the plugin HPI.
Artifacts are published to `target/` and archived automatically when using the included Jenkins Pipeline (`Jenkinsfile`).

### Install into Jenkins

1. Upload `target/devsecops-pipeline.hpi` through `Manage Jenkins` → `Manage Plugins` → `Advanced` → `Upload Plugin` (or push to your
   update center).
2. Restart Jenkins if prompted.
3. The `devsecops` shared library is loaded implicitly for all Pipelines—no extra configuration in *Global Pipeline Libraries* is required.

## Using the Pipeline Steps

Invoke the `devSecOpsPipeline` step inside your Jenkins Pipeline to orchestrate the scanners you need. Each scanner can be configured
independently, and execution can run sequentially or in parallel.

```groovy
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

### Common Docker Overrides (`docker` map)
- `mounts`: List of mount definitions (`[source: '/host/path', target: '/container/path', readOnly: true]`).
- `env`: Environment variables to expose to the container (`[VAR_NAME: 'value']`).
- `options`: Additional raw Docker CLI flags (for example, `['--network host']`).
- `remove` / `tty`: Toggle `--rm` and `-t` flags respectively (default: `true`).
- `workdir`, `network`, `entrypoint`: Optional Docker run flags passed through to the container.

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
- `command`: Underlying Semgrep CLI subcommand (default: `ci`).
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

Set `parallel: true` to run all enabled scanners concurrently. Jenkins creates separate stages for each scanner. When `parallel`
is `false`, scans run sequentially in the order defined above.

## Requirements

- Jenkins agents must have Docker available to run the scanner containers.
- Necessary credentials or tokens (for example, `SNYK_TOKEN`) must be configured in the Jenkins environment.
- Target applications should be accessible from the Jenkins agent running OWASP ZAP.

## Publishing Guidance

1. Update the SCM coordinates in `pom.xml` to reflect your organization.
2. Run `mvn -DskipTests=false release:prepare release:perform` (after configuring credentials) to publish to an update center or Maven
   repository.
3. Tag the release in source control and update any downstream documentation.

The repository ships with Spotless, Checkstyle, and SpotBugs enabled during `verify` to keep code quality consistent before release.
