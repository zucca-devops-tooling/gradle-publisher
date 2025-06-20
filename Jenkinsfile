pipeline {
    agent any

    environment {
        GRADLE_OPTS = '-Dorg.gradle.jvmargs="-Xmx2g -XX:+HeapDumpOnOutOfMemoryError"'

        GH_CREDENTIALS  = credentials('GITHUB_PACKAGES')
        // JFROG_CREDENTIALS  = credentials('JFROG_CREDENTIALS')
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
                    setStatus('build','NEUTRAL','Building the project...')
                    try {
                        sh '''#!/bin/bash

                            set -euo pipefail
                            ./gradlew clean assemble --refresh-dependencies --info --no-daemon \
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
                    setStatus('spotless','NEUTRAL','Checking code format...')
                    try {
                        sh './gradlew check -x test --no-daemon'
                        setStatus('spotless','SUCCESS','Spotless passed')
                    } catch (Exception e) {
                        setStatus('spotless','FAILURE','Spotless failed')
                    }
                }
            }
        }
        stage('Test') {
            steps {
                script {
                    setStatus('test','NEUTRAL','Running tests...')
                    try {
                        sh './gradlew test --no-daemon'
                        setStatus('test','SUCCESS','Tests passed')
                    } catch (Exception e) {
                        setStatus('test','FAILURE','Tests failed')
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
                            -PgithubPackagesUsername=$GH_CREDENTIALS_USR \
                            -PgithubPackagesPassword=$GH_CREDENTIALS_PSW \
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
    publishChecks name: context, conclusion: status, title: 'Jenkins CI', summary: message
}