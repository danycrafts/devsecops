#!/usr/bin/env groovy
/**
 * DevSecOps shared library helper for orchestrating common security scanners.
 */
def call(Map config = [:]) {
    Map defaults = [
        parallel: false,
        zap: [enabled: false],
        grype: [enabled: false],
        trivy: [enabled: false],
        semgrep: [enabled: false],
        snyk: [enabled: false],
        dependencyCheck: [enabled: false]
    ]

    Map options = defaults + config.collectEntries { key, value ->
        if (value instanceof Map && defaults[key] instanceof Map) {
            [(key): defaults[key] + value]
        } else {
            [(key): value]
        }
    }

    List sequentialScans = []
    Map parallelScans = [:]

    if (options.zap.enabled) {
        Closure runner = {
            runZap(options.zap)
        }
        addScan('OWASP ZAP', runner, options.parallel, sequentialScans, parallelScans)
    }

    if (options.grype.enabled) {
        Closure runner = {
            runGrype(options.grype)
        }
        addScan('Grype', runner, options.parallel, sequentialScans, parallelScans)
    }

    if (options.trivy.enabled) {
        Closure runner = {
            runTrivy(options.trivy)
        }
        addScan('Trivy', runner, options.parallel, sequentialScans, parallelScans)
    }

    if (options.semgrep.enabled) {
        Closure runner = {
            runSemgrep(options.semgrep)
        }
        addScan('Semgrep', runner, options.parallel, sequentialScans, parallelScans)
    }

    if (options.snyk.enabled) {
        Closure runner = {
            runSnyk(options.snyk)
        }
        addScan('Snyk', runner, options.parallel, sequentialScans, parallelScans)
    }

    if (options.dependencyCheck.enabled) {
        Closure runner = {
            runDependencyCheck(options.dependencyCheck)
        }
        addScan('OWASP Dependency-Check', runner, options.parallel, sequentialScans, parallelScans)
    }

    if (options.parallel) {
        parallel parallelScans
    } else {
        sequentialScans.each { scan ->
            stage(scan.name) {
                scan.runner.call()
            }
        }
    }
}

private void addScan(String name, Closure runner, boolean parallelMode, List sequentialScans, Map parallelScans) {
    if (parallelMode) {
        parallelScans[name] = {
            stage(name) {
                runner.call()
            }
        }
    } else {
        sequentialScans << [name: name, runner: runner]
    }
}

private void runZap(Map options) {
    if (!options.targetUrl) {
        error 'OWASP ZAP requires `targetUrl` to be provided.'
    }

    String image = options.get('image', 'owasp/zap2docker-stable')
    String baselineScript = options.get('baselineCommand', 'zap-baseline.py')
    String reportName = options.get('reportName', 'zap-report.xml')
    String extraArgs = options.get('args', '-a')

    sh """
    docker run --rm -t \
        -v \"$WORKSPACE\":/zap/wrk \
        ${image} ${baselineScript} \
        -t ${options.targetUrl} \
        -x ${reportName} \
        ${extraArgs}
    """
}

private void runGrype(Map options) {
    String imageRef = options.get('image', null)
    String sbom = options.get('sbom', null)
    String dir = options.get('dir', null)
    String dockerImage = options.get('containerImage', 'anchore/grype:latest')
    String failOn = options.get('failOn', 'medium')

    if ([imageRef, sbom, dir].count { it != null } != 1) {
        error 'Grype requires exactly one of `image`, `sbom`, or `dir` to be provided.'
    }

    String target
    if (imageRef) {
        target = imageRef
    } else if (sbom) {
        target = "sbom:${sbom}"
    } else {
        target = "dir:${dir ?: '.'}"
    }

    sh """
    docker run --rm -t \
        -v /var/run/docker.sock:/var/run/docker.sock \
        -v \"$WORKSPACE\":/workspace:ro \
        ${dockerImage} ${target} \
        --fail-on ${failOn}
    """
}

private void runTrivy(Map options) {
    String scanType = options.get('type', 'fs')
    String target = options.get('target', '.')
    String format = options.get('format', 'table')
    String output = options.get('output', '')
    String severity = options.get('severity', 'CRITICAL,HIGH')
    String ignoreUnfixed = options.get('ignoreUnfixed', true) ? '--ignore-unfixed' : ''
    String hideProgress = options.get('hideProgress', true) ? '--no-progress' : ''
    String additionalArgs = options.get('args', '')
    String dockerImage = options.get('containerImage', 'aquasec/trivy:latest')

    String outputFlag = output ? "--output ${output}" : ''
    String commandTarget = scanType == 'image' ? target : "/workspace/${target}"
    String volume = scanType == 'image' ? '' : '-v "$WORKSPACE":/workspace'

    sh """
    docker run --rm -t \
        -v /var/run/docker.sock:/var/run/docker.sock \
        ${volume} \
        ${dockerImage} ${scanType} ${commandTarget} \
        --severity ${severity} \
        ${ignoreUnfixed} \
        ${hideProgress} \
        --format ${format} \
        ${outputFlag} \
        ${additionalArgs}
    """
}

private void runSemgrep(Map options) {
    String config = options.get('config', 'auto')
    String severity = options.get('severity', '')
    String output = options.get('output', '')
    String additionalArgs = options.get('args', '')
    String dockerImage = options.get('containerImage', 'returntocorp/semgrep:latest')

    String severityFlag = severity ? "--severity ${severity}" : ''
    String outputFlag = output ? "--output ${output}" : ''

    sh """
    docker run --rm -t \
        -v \"$WORKSPACE\":/src \
        ${dockerImage} semgrep ci \
        --config ${config} \
        ${severityFlag} \
        ${outputFlag} \
        ${additionalArgs}
    """
}

private void runSnyk(Map options) {
    String command = options.get('command', 'test')
    String target = options.get('target', '.')
    String additionalArgs = options.get('args', '')
    String dockerImage = options.get('containerImage', 'snyk/snyk:docker')
    String authTokenVar = options.get('tokenEnvVar', 'SNYK_TOKEN')

    sh """
    if [ -z \"\$${authTokenVar}\" ]; then
        echo 'Snyk token environment variable ${authTokenVar} is not set.'
        exit 1
    fi
    docker run --rm -t \
        -e SNYK_TOKEN=\"\$${authTokenVar}\" \
        -v \"$WORKSPACE\":/project \
        ${dockerImage} ${command} ${target} ${additionalArgs}
    """
}

private void runDependencyCheck(Map options) {
    String project = options.get('project', 'dependency-check')
    String format = options.get('format', 'ALL')
    String output = options.get('outputDir', 'dependency-check-report')
    String suppressionFile = options.get('suppressionFile', '')
    String additionalArgs = options.get('args', '')
    String dockerImage = options.get('containerImage', 'owasp/dependency-check:latest')

    String suppressionFlag = suppressionFile ? "--suppression /src/${suppressionFile}" : ''

    sh """
    mkdir -p ${output}
    docker run --rm -t \
        -v \"$WORKSPACE\":/src \
        ${dockerImage} \
        --project \"${project}\" \
        --scan /src \
        --format ${format} \
        --out /src/${output} \
        ${suppressionFlag} \
        ${additionalArgs}
    """
}
