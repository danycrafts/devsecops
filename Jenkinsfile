#!/usr/bin/env groovy

/**
 * Jenkins pipeline for DevSecOps project.
 *
 * Key capabilities:
 *  - Repository synchronization from an external map (config/repositories.yaml)
 *  - Docker registry authentication with configurable image name/tag
 *  - Build, test, archive and publish HTML reports for Maven project outputs
 *  - GitLab commit status reporting for every stage
 */

def dockerRegistryCredentialsId = 'docker-registry-credentials'
def repositoriesConfigPath = 'config/repositories.yaml'

def dockerLogin = { String registry, String credentialsId ->
    if (!registry?.trim()) {
        error 'Docker registry URL was not provided. Set the DOCKER_REGISTRY environment variable or parameter.'
    }
    if (!credentialsId?.trim()) {
        error 'Docker registry credentials ID was not provided.'
    }
    withCredentials([usernamePassword(credentialsId: credentialsId, usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_PASS')]) {
        sh "echo $REGISTRY_PASS | docker login ${registry} --username $REGISTRY_USER --password-stdin"
    }
}

pipeline {
    agent any

    tools {
        maven 'mvn'
        jdk 'jdk11'
    }

    options {
        skipDefaultCheckout()
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 1, unit: 'HOURS')
    }

    parameters {
        string(name: 'DOCKER_IMAGE', defaultValue: 'devsecops/app', description: 'Docker image name to be used for build artifacts')
        string(name: 'DOCKER_TAG', defaultValue: 'latest', description: 'Docker image tag to be used for build artifacts')
        string(name: 'DOCKER_REGISTRY', defaultValue: 'registry.example.com', description: 'Docker registry hostname that will be used for authentication')
    }

    environment {
        DOCKER_REGISTRY_CREDENTIALS = "${dockerRegistryCredentialsId}"
    }

    stages {
        stage('Environment Preparation') {
            steps {
                script {
                    gitLabCommitStatus(name: 'Environment Preparation') {
                        checkout scm

                        def repositoryConfig = fileExists(repositoriesConfigPath) ? readYaml(file: repositoriesConfigPath) : [:]
                        def repositories = repositoryConfig?.repositories instanceof List ? repositoryConfig.repositories : []

                        echo "Preparing Docker environment for ${params.DOCKER_IMAGE}:${params.DOCKER_TAG}"
                        dockerLogin(params.DOCKER_REGISTRY, env.DOCKER_REGISTRY_CREDENTIALS)

                        if (repositories.isEmpty()) {
                            echo 'No external repositories configured for synchronization.'
                        } else {
                            repositories.findAll { it?.enabled != false }.each { repo ->
                                if (!repo?.url || !repo?.folder) {
                                    error "Invalid repository configuration detected: ${repo}"
                                }

                                def repoType = repo.type ?: 'unspecified'
                                def repoBranch = repo.branch ?: 'main'

                                echo "Synchronizing ${repoType} repository '${repo.folder}' from ${repo.url} (branch: ${repoBranch})"

                                dir(repo.folder) {
                                    if (fileExists('.git')) {
                                        sh 'git remote set-url origin ' + repo.url
                                        sh 'git fetch --all --prune'
                                        sh "git checkout ${repoBranch}"
                                        sh "git reset --hard origin/${repoBranch}"
                                    } else {
                                        sh "git clone --branch ${repoBranch} ${repo.url} ."
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        stage('Build & Test') {
            steps {
                script {
                    gitLabCommitStatus(name: 'Build & Test') {
                        sh 'mvn --batch-mode -Dmaven.test.failure.ignore=false clean verify'
                    }
                }
            }
        }

        stage('Generate Reports') {
            steps {
                script {
                    gitLabCommitStatus(name: 'Generate Reports') {
                        sh 'mvn --batch-mode surefire-report:report'
                    }
                }
            }
        }

        stage('Archive Plugin') {
            steps {
                script {
                    gitLabCommitStatus(name: 'Archive Plugin') {
                        archiveArtifacts artifacts: 'target/*.hpi', fingerprint: true
                    }
                }
            }
        }

        stage('Publish Reports') {
            steps {
                script {
                    gitLabCommitStatus(name: 'Publish Reports') {
                        publishHTML(target: [
                            allowMissing: true,
                            alwaysLinkToLastBuild: true,
                            keepAll: true,
                            reportDir: 'target/site',
                            reportFiles: 'surefire-report.html',
                            reportName: 'Surefire Test Report'
                        ])
                    }
                }
            }
        }
    }

    post {
        always {
            junit 'target/surefire-reports/*.xml'
        }
    }
}
