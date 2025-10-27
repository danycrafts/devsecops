#!/usr/bin/env groovy

def call(Map overrides = [:]) {
    def helper = utils()
    Map defaults = [
        containerImage: 'owasp/zap2docker-stable',
        script: 'zap-baseline.py',
        reportName: 'zap-report.xml',
        targetUrl: null,
        args: ['-a'],
        docker: [
            mounts: [[source: '$WORKSPACE', target: '/zap/wrk', readOnly: false]],
            env: [:],
            options: [],
            remove: true,
            tty: true
        ]
    ]

    Map config = helper.deepMerge(defaults, overrides ?: [:])
    helper.requireFields(config, ['targetUrl'], 'OWASP ZAP')

    List<String> command = [
        config.script,
        '-t', config.targetUrl,
        '-x', config.reportName
    ]
    command.addAll(helper.ensureList(config.args))

    Map dockerConfig = helper.deepMerge(config.docker, [image: config.containerImage])
    dockerConfig = helper.applyDefaultDockerCommand(dockerConfig, command)

    String dockerCommand = helper.buildDockerRunCommand(dockerConfig)

    sh """
    ${dockerCommand}
    """
}
