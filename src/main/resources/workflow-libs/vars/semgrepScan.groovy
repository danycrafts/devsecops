#!/usr/bin/env groovy

def call(Map overrides = [:]) {
    def helper = utils()
    Map defaults = [
        config: 'auto',
        severity: '',
        output: '',
        args: [],
        containerImage: 'returntocorp/semgrep:latest',
        command: 'ci',
        docker: [
            mounts: [[source: '$WORKSPACE', target: '/src', readOnly: false]],
            env: [:],
            options: [],
            remove: true,
            tty: true
        ]
    ]

    Map config = helper.deepMerge(defaults, overrides ?: [:])

    String severityFlag = config.severity ? "--severity ${config.severity}" : ''
    String outputFlag = config.output ? "--output ${config.output}" : ''

    List<String> command = [config.command, '--config', config.config, severityFlag, outputFlag]
    command.addAll(helper.ensureList(config.args))

    Map dockerConfig = helper.deepMerge(config.docker, [image: config.containerImage])
    dockerConfig = helper.applyDefaultDockerCommand(dockerConfig, command)

    String dockerCommand = helper.buildDockerRunCommand(dockerConfig)

    sh """
    ${dockerCommand}
    """
}
