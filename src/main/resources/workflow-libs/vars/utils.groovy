#!/usr/bin/env groovy

def call() {
    this
}

Map deepMerge(Map left = [:], Map right = [:]) {
    Map result = [:]
    if (left) {
        result.putAll(left)
    }
    right?.each { key, value ->
        def existing = result[key]
        if (existing instanceof Map && value instanceof Map) {
            result[key] = deepMerge(existing as Map, value as Map)
        } else {
            result[key] = value
        }
    }
    result
}

List ensureList(def value) {
    if (value == null) {
        return []
    }
    if (value instanceof List) {
        return value.flatten()
    }
    [value]
}

void requireFields(Map config, List<String> fields, String context) {
    fields.each { field ->
        def value = config[field]
        if (value == null || (value instanceof String && value.trim().isEmpty())) {
            error "${context} requires \`${field}\` to be provided."
        }
    }
}

void requireExactlyOne(Map config, List<String> fields, String context) {
    int provided = fields.count { field ->
        def value = config[field]
        !(value == null || (value instanceof String && value.trim().isEmpty()))
    }
    if (provided != 1) {
        error "${context} requires exactly one of ${fields.collect { "`$it`" }.join(', ')} to be provided."
    }
}

String joinShellParts(List parts) {
    ensureList(parts).findAll { part ->
        part != null && part.toString().trim()
    }.collect { part ->
        part.toString()
    }.join(' \\\n    ')
}

String buildDockerRunCommand(Map config) {
    Map defaults = [
        remove: true,
        tty: true,
        mounts: [],
        env: [:],
        options: [],
        command: []
    ]
    Map merged = deepMerge(defaults, config ?: [:])

    if (!merged.image) {
        error 'Docker command requires an `image` to be specified.'
    }

    List parts = ['docker', 'run']
    if (merged.remove) {
        parts << '--rm'
    }
    if (merged.tty) {
        parts << '-t'
    }

    ensureList(merged.options).each { opt ->
        parts << opt
    }

    ensureList(merged.mounts).each { mount ->
        if (!mount?.source || !mount?.target) {
            error 'Docker mount requires `source` and `target`.'
        }
        String flag = "-v \"${mount.source}\":${mount.target}"
        if (mount.readOnly) {
            flag += ':ro'
        }
        parts << flag
    }

    (merged.env ?: [:]).each { key, value ->
        parts << "-e ${key}=${value}"
    }

    if (merged.workdir) {
        parts << "-w ${merged.workdir}"
    }

    if (merged.network) {
        parts << "--network ${merged.network}"
    }

    if (merged.entrypoint) {
        parts << "--entrypoint ${merged.entrypoint}"
    }

    parts << merged.image
    parts.addAll(ensureList(merged.command))

    joinShellParts(parts)
}

Map applyDefaultDockerCommand(Map dockerConfig, List command) {
    Map configCopy = [:]
    if (dockerConfig) {
        configCopy.putAll(dockerConfig)
    }
    List existingCommand = ensureList(configCopy.command)
    if (existingCommand.isEmpty()) {
        configCopy.command = command
    }
    configCopy
}
