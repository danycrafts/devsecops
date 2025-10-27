#!/usr/bin/env groovy

def call(Map overrides = [:]) {
    def helper = utils()
    Map defaults = [
        type: 'fs',
        target: '.',
        format: 'table',
        output: '',
        severity: 'CRITICAL,HIGH',
        ignoreUnfixed: true,
        hideProgress: true,
        args: [],
        containerImage: 'aquasec/trivy:latest',
        docker: [
            mounts: [
                [source: '/var/run/docker.sock', target: '/var/run/docker.sock', readOnly: false],
                [source: '$WORKSPACE', target: '/workspace', readOnly: false]
            ],
            env: [:],
            options: [],
            remove: true,
            tty: true
        ]
    ]

    Map config = helper.deepMerge(defaults, overrides ?: [:])

    String outputFlag = config.output ? "--output ${config.output}" : ''
    String ignoreUnfixedFlag = config.ignoreUnfixed ? '--ignore-unfixed' : ''
    String hideProgressFlag = config.hideProgress ? '--no-progress' : ''
    String commandTarget = config.type == 'image' ? config.target : ("/workspace/${config.target}").replaceAll('/+', '/')

    List<String> command = [
        config.type,
        commandTarget,
        '--severity', config.severity,
        ignoreUnfixedFlag,
        hideProgressFlag,
        '--format', config.format,
        outputFlag
    ]
    command.addAll(helper.ensureList(config.args))

    Map dockerConfig = helper.deepMerge(config.docker, [image: config.containerImage])
    dockerConfig = helper.applyDefaultDockerCommand(dockerConfig, command)

    String dockerCommand = helper.buildDockerRunCommand(dockerConfig)

    sh """
    ${dockerCommand}
    """
}
