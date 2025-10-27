#!/usr/bin/env groovy

def call(Map overrides = [:]) {
    def helper = utils()
    Map defaults = [
        project: 'dependency-check',
        format: 'ALL',
        outputDir: 'dependency-check-report',
        suppressionFile: '',
        args: [],
        containerImage: 'owasp/dependency-check:latest',
        docker: [
            mounts: [[source: '$WORKSPACE', target: '/src', readOnly: false]],
            env: [:],
            options: [],
            remove: true,
            tty: true
        ]
    ]

    Map config = helper.deepMerge(defaults, overrides ?: [:])

    String suppressionFlag = config.suppressionFile ? "--suppression /src/${config.suppressionFile}" : ''

    List<String> command = [
        '--project', "${config.project}",
        '--scan', '/src',
        '--format', config.format,
        '--out', "/src/${config.outputDir}",
        suppressionFlag
    ]
    command.addAll(helper.ensureList(config.args))

    Map dockerConfig = helper.deepMerge(config.docker, [image: config.containerImage])
    dockerConfig = helper.applyDefaultDockerCommand(dockerConfig, command)

    String dockerCommand = helper.buildDockerRunCommand(dockerConfig)

    sh """
    mkdir -p ${config.outputDir}
    ${dockerCommand}
    """
}
