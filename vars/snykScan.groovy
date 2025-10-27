#!/usr/bin/env groovy

def call(Map overrides = [:]) {
    def helper = utils()
    Map defaults = [
        command: 'test',
        target: '.',
        args: [],
        tokenEnvVar: 'SNYK_TOKEN',
        containerImage: 'snyk/snyk:docker',
        docker: [
            mounts: [[source: '$WORKSPACE', target: '/project', readOnly: false]],
            env: [:],
            options: [],
            remove: true,
            tty: true
        ]
    ]

    Map config = helper.deepMerge(defaults, overrides ?: [:])

    String tokenEnvVar = config.tokenEnvVar ?: 'SNYK_TOKEN'
    if (!env[tokenEnvVar]) {
        error "Snyk token environment variable ${tokenEnvVar} is not set."
    }

    Map dockerConfig = helper.deepMerge(config.docker, [image: config.containerImage])
    Map dockerEnv = (dockerConfig.env ?: [:]) + ['SNYK_TOKEN': "\$${tokenEnvVar}"]
    dockerConfig.env = dockerEnv
    List<String> command = [config.command, config.target]
    command.addAll(helper.ensureList(config.args))
    dockerConfig = helper.applyDefaultDockerCommand(dockerConfig, command)

    String dockerCommand = helper.buildDockerRunCommand(dockerConfig)

    sh """
    ${dockerCommand}
    """
}
