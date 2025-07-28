def call(Map config = [:]) {
    pipeline {
        agent any
        
        environment {
            ANSIBLE_HOST_KEY_CHECKING = 'False'
            ANSIBLE_FORKS = '2'
            ANSIBLE_CONFIG = '/tmp/ansibleJenkins/ansible/ansible.cfg'
        }
        
        parameters {
            string(
                name: 'ANSIBLE_ROLE',
                defaultValue: config.defaultRole ?: 'homeassistant',
                description: '''🎯 Rôle Ansible à déployer:
                Variables par défaut dans roles/{role}/vars/main.yml'''
            )
            string(
                name: 'TARGET',
                defaultValue: config.defaultTarget ?: 'homeassistant',
                description: '''🎯 Serveur ou groupe cible (inventory/hosts.ini)'''
            )
            string(
                name: 'PLAYBOOK',
                defaultValue: config.defaultPlaybook ?: 'site.yml',
                description: '''📋 Playbook à exécuter (playbooks/)'''
            )
            text(
                name: 'EXTRA_VARS',
                defaultValue: config.defaultExtraVars ?: '',
                description: '''🔧 Variables à override (--extra-vars)'''
            )
            choice(
                name: 'ANSIBLE_OPTIONS',
                choices: config.ansibleOptions ?: ['', '--check --diff', '--verbose', '--check --diff --verbose'],
                description: 'Options Ansible supplémentaires'
            )
        }
        
        stages {
            stage('Configuration') {
                steps {
                    script {
                        echo "=== ANSIBLE SHARED LIBRARY ==="
                        echo "Rôle: ${params.ANSIBLE_ROLE}"
                        echo "Target: ${params.TARGET}"
                        echo "Playbook: ${params.PLAYBOOK}"
                        echo "Extra vars: ${params.EXTRA_VARS}"
                        echo "Options: ${params.ANSIBLE_OPTIONS}"
                        
                        env.ANSIBLE_ROLE = params.ANSIBLE_ROLE
                        env.TARGET = params.TARGET
                        env.PLAYBOOK = params.PLAYBOOK
                        env.EXTRA_VARS = params.EXTRA_VARS ?: ''
                        env.ANSIBLE_OPTIONS = params.ANSIBLE_OPTIONS ?: ''
                        env.ANSIBLE_DIR = config.ansibleDir ?: '/tmp/ansibleJenkins/ansible'
                    }
                }
            }
            
            stage('Validation') {
                steps {
                    script {
                        ansibleValidate([
                            ansibleDir: env.ANSIBLE_DIR,
                            role: env.ANSIBLE_ROLE,
                            target: env.TARGET,
                            playbook: env.PLAYBOOK
                        ])
                    }
                }
            }
            
            stage('Execute Ansible') {
                steps {
                    script {
                        executeAnsiblePlaybook(config)
                    }
                }
            }
        }
        
        post {
            always {
                script {
                    ansibleNotify([
                        role: env.ANSIBLE_ROLE,
                        target: env.TARGET,
                        playbook: env.PLAYBOOK,
                        result: currentBuild.currentResult,
                        buildNumber: env.BUILD_NUMBER,
                        buildUrl: env.BUILD_URL,
                        emailTo: config.emailTo ?: 'admin@company.com'
                    ])
                }
            }
            success {
                echo "✅ Déploiement ${env.ANSIBLE_ROLE} réussi"
            }
            failure {
                echo "❌ Échec ${env.ANSIBLE_ROLE}"
            }
            cleanup {
                cleanWs()
            }
        }
    }
}

// Fonction helper pour exécution Ansible
def executeAnsiblePlaybook(Map config) {
    def cmd = """
        cd ${env.ANSIBLE_DIR}
        
        ansible-playbook \\
            -i inventory/hosts.ini \\
            playbooks/${env.PLAYBOOK} \\
            --limit ${env.TARGET}
    """
    
    if (env.EXTRA_VARS.trim()) {
        cmd += " -e \"${env.EXTRA_VARS}\""
    }
    
    if (env.ANSIBLE_OPTIONS.trim()) {
        cmd += " ${env.ANSIBLE_OPTIONS}"
    }
    
    echo "Commande Ansible:"
    echo cmd
    
    withCredentials([
        file(credentialsId: config.sshKeyCredentialsId ?: 'ssh-key-ansible-user-secret-file', variable: 'SSH_PRIVATE_KEY_FILE'),
        string(credentialsId: config.tokenCredentialsId ?: 'ha-long-lived-token', variable: 'HA_TOKEN')
    ]) {
        
        if (env.ANSIBLE_ROLE == 'homeassistant' && env.EXTRA_VARS.contains('ha_long_lived_token')) {
            cmd = cmd.replace('ha_long_lived_token=PLACEHOLDER', "ha_long_lived_token=\${HA_TOKEN}")
        }
        
        sh """
            echo "=== ANSIBLE SHARED LIBRARY EXECUTION ==="
            echo "Rôle: ${env.ANSIBLE_ROLE}"
            echo "Target: ${env.TARGET}"
            echo ""
            
            ${cmd}
            
            echo ""
            echo "=== TERMINÉ ==="
        """
    }
}