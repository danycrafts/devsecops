#!/usr/bin/env groovy

def call(Map overrides = [:]) {
    def helper = utils()
    Map defaults = [
        image: null,
        sbom: null,
        dir: null,
        failOn: 'medium',
        containerImage: 'anchore/grype:latest',
        args: [],
        docker: [
            mounts: [
                [source: '/var/run/docker.sock', target: '/var/run/docker.sock', readOnly: false],
                [source: '$WORKSPACE', target: '/workspace', readOnly: true]
            ],
            env: [:],
            options: [],
            remove: true,
            tty: true
        ]
    ]

    Map config = helper.deepMerge(defaults, overrides ?: [:])
    helper.requireExactlyOne(config, ['image', 'sbom', 'dir'], 'Grype')

    String target
    if (config.image) {
        target = config.image
    } else if (config.sbom) {
        target = "sbom:${config.sbom}"
    } else {
        target = "dir:${config.dir ?: '.'}"
    }

    List<String> command = [target, '--fail-on', config.failOn]
    command.addAll(helper.ensureList(config.args))

    Map dockerConfig = helper.deepMerge(config.docker, [image: config.containerImage])
    dockerConfig = helper.applyDefaultDockerCommand(dockerConfig, command)

    String dockerCommand = helper.buildDockerRunCommand(dockerConfig)

    sh """
    ${dockerCommand}
    """
}
