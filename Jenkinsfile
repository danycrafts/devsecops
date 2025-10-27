pipeline {
    agent any
    tools {
        maven 'mvn'
        jdk 'jdk11'
    }
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 1, unit: 'HOURS')
    }
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('Build & Test') {
            steps {
                sh 'mvn --batch-mode -Dmaven.test.failure.ignore=false verify'
            }
        }
        stage('Archive Plugin') {
            steps {
                archiveArtifacts artifacts: 'target/*.hpi', fingerprint: true
            }
        }
    }
    post {
        always {
            junit 'target/surefire-reports/*.xml'
        }
    }
}
