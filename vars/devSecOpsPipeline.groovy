#!/usr/bin/env groovy
/**
 * DevSecOps shared library helper for orchestrating common security scanners.
 */
def call(Map config = [:]) {
    def helper = utils()

    Map defaults = [
        parallel: false,
        zap: [enabled: false],
        grype: [enabled: false],
        trivy: [enabled: false],
        semgrep: [enabled: false],
        snyk: [enabled: false],
        dependencyCheck: [enabled: false]
    ]

    Map options = helper.deepMerge(defaults, config ?: [:])

    List<Map> sequentialScans = []
    Map<String, Closure> parallelScans = [:]

    List<Map> tools = [
        [name: 'OWASP ZAP', key: 'zap', runner: this.&zapScan],
        [name: 'Grype', key: 'grype', runner: this.&grypeScan],
        [name: 'Trivy', key: 'trivy', runner: this.&trivyScan],
        [name: 'Semgrep', key: 'semgrep', runner: this.&semgrepScan],
        [name: 'Snyk', key: 'snyk', runner: this.&snykScan],
        [name: 'OWASP Dependency-Check', key: 'dependencyCheck', runner: this.&dependencyCheckScan]
    ]

    tools.each { tool ->
        Map toolConfig = options[tool.key] ?: [:]
        if (toolConfig.enabled) {
            Closure stageRunner = { tool.runner.call(toolConfig) }
            addScanStage(tool.name, stageRunner, options.parallel, sequentialScans, parallelScans)
        }
    }

    if (options.parallel) {
        if (parallelScans) {
            parallel parallelScans
        }
    } else {
        sequentialScans.each { scan ->
            stage(scan.name) {
                scan.executor.call()
            }
        }
    }
}

private void addScanStage(String name, Closure executor, boolean parallelMode, List<Map> sequentialScans, Map<String, Closure> parallelScans) {
    if (parallelMode) {
        parallelScans[name] = {
            stage(name) {
                executor.call()
            }
        }
    } else {
        sequentialScans << [name: name, executor: executor]
    }
}
