def buildName = "${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(20)}-${env.BUILD_ID}"

pipeline {
  agent {
    kubernetes {
      cloud 'zeebe-ci'
      label "zeebe-ci-build_${buildName}"
      defaultContainer 'jnlp'
      yaml '''\
apiVersion: v1
kind: Pod
metadata:
  labels:
    agent: zeebe-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: slaves
  tolerations:
    - key: "slaves"
      operator: "Exists"
      effect: "NoSchedule"
  containers:
    - name: maven
      image: maven:3.6.0-jdk-8
      command: ["cat"]
      tty: true
      env:
        - name: JAVA_TOOL_OPTIONS
          value: |
            -XX:+UseContainerSupport
      resources:
        limits:
          cpu: 2
          memory: 8Gi
        requests:
          cpu: 1
          memory: 4Gi
'''
    }
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timestamps()
    timeout(time: 45, unit: 'MINUTES')
  }

  environment {
    NEXUS = credentials("camunda-nexus")
  }

  parameters {
    booleanParam(name: 'RELEASE', defaultValue: false, description: 'Build a release from current commit?')
    string(name: 'RELEASE_VERSION', defaultValue: '3.2.0-alpha1', description: 'Which version to release?')
    string(name: 'DEVELOPMENT_VERSION', defaultValue: '3.2.0-SNAPSHOT', description: 'Next development version?')
  }

  stages {
    stage('Prepare') {
      steps {
        container('maven') {
            sh 'mvn clean install -B -s settings.xml -DskipTests -Dmaven.test.skip -Dmaven.javadoc.skip -Ddockerfile.skip'
        }
      }
    }

    stage('Build') {
      when { not { expression { params.RELEASE } } }
      steps {
        container('maven') {
            sh 'mvn install -B -s settings.xml -Ddockerfile.skip -Dmaven.test.redirectTestOutputToFile -Dsurefire.rerunFailingTestsCount=3'
        }
      }

      post {
        always {
            junit testResults: "**/*/TEST-*.xml", keepLongStdio: true
        }
      }
    }

    stage('Upload') {
      when {
        allOf {
            branch 'develop'; not { expression { params.RELEASE } }
        }
      }
      steps {
        container('maven') {
            sh 'mvn -B -s settings.xml generate-sources source:jar javadoc:jar deploy -DskipTests -Ddockerfile.skip'
        }
      }
    }

    stage('Release') {
      when {
        allOf {
          branch 'develop'; expression { params.RELEASE }
        }
      }

      environment {
        MAVEN_CENTRAL = credentials('maven_central_deployment_credentials')
        GPG_PASS = credentials('password_maven_central_gpg_signing_key')
        GPG_PUB_KEY = credentials('maven_central_gpg_signing_key_pub')
        GPG_SEC_KEY = credentials('maven_central_gpg_signing_key_sec')
        RELEASE_VERSION = "${params.RELEASE_VERSION}"
        DEVELOPMENT_VERSION = "${params.DEVELOPMENT_VERSION}"
      }

      steps {
        container('maven') {
          sshagent(['camunda-jenkins-github-ssh']) {
            sh 'export GPG_TTY=$(tty)'
            sh 'gpg -q --import ${GPG_PUB_KEY} '
            sh 'gpg -q --allow-secret-key-import --import --no-tty --batch --yes ${GPG_SEC_KEY}'
            sh 'git config --global user.email "ci@camunda.com"'
            sh 'git config --global user.name "camunda-jenkins"'
            sh 'mkdir ~/.ssh/ && ssh-keyscan github.com >> ~/.ssh/known_hosts'
            sh 'mvn -B -s settings.xml -DskipTests source:jar javadoc:jar release:prepare release:perform'
          }
        }
      }
    }
  }

  post {
      always {
          // Retrigger the build if the node disconnected
          script {
              if (nodeDisconnected()) {
                  build job: currentBuild.projectName, propagate: false, quietPeriod: 60, wait: false
              }
          }
      }
  }
}

boolean nodeDisconnected() {
  return currentBuild.rawBuild.getLog(500).join('') ==~ /.*(ChannelClosedException|KubernetesClientException|ClosedChannelException).*/
}
