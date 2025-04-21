pipeline {
    agent any

    environment {
        GRADLE_OPTS = '-Dorg.gradle.jvmargs="-Xmx2g -XX:+HeapDumpOnOutOfMemoryError"'

        JFROG_CREDENTIALS  = credentials('jfrog-credentials')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('Build') {
            steps {
                script {
                    setStatus('build','PENDING','Building the project...')
                    try {
                        sh '''#!/bin/bash

                            set -euo pipefail
                            ./gradlew clean assemble --refresh-dependencies --info --no-daemon \
                                -PjfrogUser=$JFROG_CREDENTIALS_USR \
                                -PjfrogPassword=$JFROG_CREDENTIALS_PSW \
                        '''
                        setStatus('build','SUCCESS','Build succeeded')
                    } catch (Exception e) {
                        setStatus('build','FAILURE','Build failed')
                        throw e
                    }
                }
            }
        }
        stage('Spotless') {
            steps {
                script {
                    setStatus('spotless','PENDING','Checking code format...')
                    try {
                        sh './gradlew check --no-daemon'
                        setStatus('spotless','SUCCESS','Spotless passed')
                    } catch (Exception e) {
                        setStatus('spotless','FAILURE','Spotless failed')
                        throw e
                    }
                }
            }
        }
        stage('Test') {
            steps {
                script {
                    setStatus('test','PENDING','Running tests...')
                    try {
                        sh './gradlew test --no-daemon'
                        setStatus('test','SUCCESS','Tests passed')
                    } catch (Exception e) {
                        setStatus('test','FAILURE','Tests failed')
                        throw e
                    }
                }
            }
        }
        stage('Publish to Maven repository') {
            environment {
                GPG_KEY_ID    = credentials('GPG_KEY_ID')
                GPG_KEY_PASS  = credentials('GPG_KEY_PASS')
                OSSRH_CREDENTIALS  = credentials('OSSRH_CREDENTIALS')
            }
            steps {
                withCredentials([
                    file(credentialsId: 'GPG_SECRET_KEY', variable: 'GPG_KEY_PATH')
                ]) {
                    sh '''#!/bin/bash
                        set -euo pipefail

                        export GPG_ASC_ARMOR="$(cat $GPG_KEY_PATH)"

                        ./gradlew publish --info --no-daemon \
                            -Psigning.keyId=$GPG_KEY_ID \
                            -Psigning.password=$GPG_KEY_PASS \
                            -Psigning.secretKeyRingFile=$GPG_KEY_PATH \
                            -PjfrogUser=$JFROG_CREDENTIALS_USR \
                            -PjfrogPassword=$JFROG_CREDENTIALS_PSW \
                            -PmavenCentralUsername=$OSSRH_CREDENTIALS_USR \
                            -PmavenCentralPassword=$OSSRH_CREDENTIALS_PSW
                    '''
                }
            }
        }

        stage('Publish to Gradle Plugin Portal') {
            when {
                branch 'main'
            }
            environment {
                GPG_KEY_ID            = credentials('GPG_KEY_ID')
                GPG_KEY_PASS          = credentials('GPG_KEY_PASS')
                GRADLE_PORTAL_KEY     = credentials('GRADLE_PUBLISH_KEY')
                GRADLE_PORTAL_SECRET  = credentials('GRADLE_PUBLISH_SECRET')
            }
            steps {
                withCredentials([
                    file(credentialsId: 'GPG_SECRET_KEY', variable: 'GPG_KEY_PATH')
                ]) {
                    sh '''#!/bin/bash

                        set -euo pipefail

                        export GPG_ASC_ARMOR="$(cat $GPG_KEY_PATH)"

                        ./gradlew publishPlugins --info --no-daemon \
                            -Psigning.keyId=$GPG_KEY_ID \
                            -Psigning.password=$GPG_KEY_PASS \
                            -Psigning.secretKeyRingFile=$GPG_KEY_PATH \
                            -Pgradle.publish.key=$GRADLE_PORTAL_KEY \
                            -Pgradle.publish.secret=$GRADLE_PORTAL_SECRET
                    '''
                }
            }
        }
    }
}

def setStatus(context, status, message) {
    publishChecks name: context, conclusion: status, output: [
        title: 'Jenkins CI',
        summary: message
    ]
}