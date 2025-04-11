pipeline {
    agent any

    environment {
        GRADLE_OPTS = '-Dorg.gradle.jvmargs="-Xmx2g -XX:+HeapDumpOnOutOfMemoryError"'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {

                withCredentials([
                    file(credentialsId: 'GPG_SECRET_KEY', variable: 'GPG_KEY_PATH'),
                    string(credentialsId: 'GPG_KEY_ID', variable: 'GPG_KEY_ID'),
                    string(credentialsId: 'GPG_KEY_PASS', variable: 'GPG_KEY_PASS'),
                    usernamePassword(credentialsId: 'jfrog-credentials', usernameVariable: 'JFROG_USER', passwordVariable: 'JFROG_PASS'),
                    usernamePassword(credentialsId: 'OSSRH_CREDENTIALS', usernameVariable: 'OSSRH_USER', passwordVariable: 'OSSRH_PASS')
                ]) {
                     sh """#!/bin/bash
                         set -euo pipefail

                         echo '🔐 Importing GPG key into temporary keyring...'

                         # Use a temporary GPG home directory for clean import
                         export GNUPGHOME=\$(mktemp -d)
                         chmod 700 "\$GNUPGHOME"

                         gpg --batch --yes --homedir "\$GNUPGHOME" --import "\$GPG_KEY_PATH"

                         echo "🔍 GNUPGHOME = \$GNUPGHOME"
                         echo '🚀 Running Gradle build with signing...'

                         ./gradlew clean build --refresh-dependencies --info \\
                             -Psigning.keyId=\$GPG_KEY_ID \\
                             -Psigning.password=\$GPG_KEY_PASS \\
                             -PmavenCentralUsername=\$OSSRH_USER \\
                             -PmavenCentralPassword=\$OSSRH_PASS \\
                             -PjfrogUser=\$JFROG_USER \\
                             -PjfrogPassword=\$JFROG_PASS

                         echo '🧹 Cleaning up GPG keyring...'
                         rm -rf "\$GNUPGHOME"
                     """
                }
            }
        }
        stage('Publish to Maven Central') {
            steps {
                withCredentials([
                    file(credentialsId: 'GPG_SECRET_KEY', variable: 'GPG_KEY_PATH'),
                    string(credentialsId: 'GPG_KEY_ID', variable: 'GPG_KEY_ID'),
                    string(credentialsId: 'GPG_KEY_PASS', variable: 'GPG_KEY_PASS'),
                    usernamePassword(credentialsId: 'jfrog-credentials', usernameVariable: 'JFROG_USER', passwordVariable: 'JFROG_PASS'),
                    usernamePassword(credentialsId: 'OSSRH_CREDENTIALS', usernameVariable: 'OSSRH_USER', passwordVariable: 'OSSRH_PASS')
                ]) {
                    sh """#!/bin/bash
                        set -euo pipefail

                        export GNUPGHOME=\$(mktemp -d)
                        chmod 700 "\$GNUPGHOME"
                        gpg --batch --yes --homedir "\$GNUPGHOME" --import "\$GPG_KEY_PATH"

                        echo "🔍 GNUPGHOME = \$GNUPGHOME"

                        GPG_HOME_FLAG="-Dgpg.homedir=\$GNUPGHOME"

                        echo "🔧 Running: ./gradlew publishToMavenCentralPortal \$GPG_HOME_FLAG"

                        ./gradlew publishToMavenCentralPortal --info \\
                            "\$GPG_HOME_FLAG" \\
                            "-Psigning.keyId=\$GPG_KEY_ID" \\
                            "-Psigning.password=\$GPG_KEY_PASS" \\
                            "-PmavenCentralUsername=\$OSSRH_USER" \\
                            "-PmavenCentralPassword=\$OSSRH_PASS" \\
                            "-PjfrogUser=\$JFROG_USER" \\
                            "-PjfrogPassword=\$JFROG_PASS"
                    """
                }
            }
        }
    }
}