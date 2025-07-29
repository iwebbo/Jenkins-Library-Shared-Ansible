#!/usr/bin/env groovy

/**
 * Fonction principale de déploiement Ansible simplifiée
 * Usage: ansibleDeploy([
 *   playbook: 'site.yml', 
 *   targetServers: 'web01,web02',
 *   ansibleVars: [app_version: '1.2.3', debug_mode: 'true']
 * ])
 */
def call(Map config = [:]) {
    // Validation des paramètres obligatoires
    if (!config.playbook) {
        error("Parameters 'playbook' mandatory")
    }
    if (!config.targetServers) {
        error("Parameters 'targetServers' mandatory")
    }
    
    // Configuration par défaut
    def defaultConfig = readAnsibleConfig(config.config_path ?: '.')

    // Ajout des valeurs qui ne sont pas dans ansible.cfg
    defaultConfig.ansible_path = ''
    defaultConfig.config_path = '.'
    defaultConfig.targetServers = ''
    defaultConfig.playbook = ''
    defaultConfig.ansibleVars = [:]
    defaultConfig.tags = ''
    defaultConfig.checkMode = false
    defaultConfig.verbose = false
    defaultConfig.timeout = 3600
    defaultConfig.notification = true
    defaultConfig.forks = 10
    defaultConfig.become = true
    defaultConfig.becomeUser = 'root'

    config = defaultConfig + config

    echo "Starting of Deployment Ansible"
    echo "Playbook: ${config.playbook}"
    echo "Target Servers: ${config.targetServers}"
    if (config.ansibleVars) {
        echo "Var Ansible: ${config.ansibleVars}"
    }
    
    try {
        // Étape 1: Détection du type de serveurs et credentials
        stage('Détection Credentials') {
            config.credentialInfo = detectServerCredentials(config.targetServers, config.inventory)
            echo "🔑 Credentials detected: ${config.credentialInfo}"
        }
        
        // Étape 2: Validation
        stage('Validation Ansible') {
            ansibleValidate(config)
        }
        
        // Étape 3: Préparation des variables
        stage('Préparation Variables') {
            prepareAnsibleVars(config)
        }
        
        // Étape 4: Exécution du playbook avec credentials
        stage('Exécution Playbook') {
            executeAnsiblePlaybookWithCredentials(config)
        }
        
        // Étape 5: Notification de succès
        if (config.notification) {
            stage('Notification') {
                ansibleNotify([
                    status: 'success',
                    playbook: config.playbook,
                    targetServers: config.targetServers,
                    ansibleVars: config.ansibleVars,
                    duration: currentBuild.durationString
                ])
            }
        }
        
    } catch (Exception e) {
        // Notification d'échec
        if (config.notification) {
            ansibleNotify([
                status: 'failure',
                playbook: config.playbook,
                targetServers: config.targetServers,
                error: e.message,
                duration: currentBuild.durationString
            ])
        }
        throw e
    }
}
/**
 * Lit la configuration depuis ansible.cfg
 */
private def readAnsibleConfig(String configPath) {
    def config = [:]
    
    try {
        def configFile = "${configPath}/ansible.cfg"
        if (fileExists(configFile)) {
            def content = readFile(configFile)
            
            content.split('\n').each { line ->
                line = line.trim()
                if (line.contains('inventory =')) {
                    config.inventory = line.split('=')[1].trim()
                    echo "Load configuration from ansible.cfg: ${config.inventory}"
                }
                if (line.contains('playbook_dir =')) {
                    config.playbook_dir = line.split('=')[1].trim()
                    echo "Playbook directory: ${config.playbook_dir}"
                }
            }
        }
    } catch (Exception e) {
        echo "⚠️ Error load from ansible.cfg: ${e.message}"
    }
    
    return config
}

/**
 * Détecte le type de serveurs et retourne les credentials appropriés
 */
private def detectServerCredentials(String targetServers, String inventory) {
    def credentialInfo = [
        hasWindows: false,
        hasLinux: false,
        windowsCredentialId: 'credentials-id-windows-user-password',
        linuxCredentialId: 'ssh-key-ansible-user-secret-file',
        mixedEnvironment: false
    ]
    
    script {
        try {
            // Récupération des informations sur les serveurs cibles
            def serverInfo = sh(
                script: """
                    ansible ${targetServers} -i ${inventory} -m setup -a "filter=ansible_os_family" --one-line 2>/dev/null || \
                    ansible ${targetServers} -i ${inventory} --list-hosts 2>/dev/null
                """,
                returnStdout: true
            ).trim()
            
            // Détection Windows (recherche de patterns Windows)
            if (serverInfo.toLowerCase().contains('windows') || 
                serverInfo.toLowerCase().contains('win') ||
                targetServers.toLowerCase().contains('win') ||
                targetServers.toLowerCase().contains('windows')) {
                credentialInfo.hasWindows = true
                echo "Windows Server detected"
            }
            
            // Détection Linux (par défaut ou patterns Linux)
            if (serverInfo.toLowerCase().contains('redhat') || 
                serverInfo.toLowerCase().contains('ubuntu') ||
                serverInfo.toLowerCase().contains('debian') ||
                serverInfo.toLowerCase().contains('centos') ||
                targetServers.toLowerCase().contains('linux') ||
                targetServers.toLowerCase().contains('web') ||
                targetServers.toLowerCase().contains('db') ||
                !credentialInfo.hasWindows) {  // Par défaut = Linux
                credentialInfo.hasLinux = true
                echo "Linux Server detected"
            }
            
            // Environnement mixte
            if (credentialInfo.hasWindows && credentialInfo.hasLinux) {
                credentialInfo.mixedEnvironment = true
                echo "Both detected Windows & Linux"
            }
            
        } catch (Exception e) {
            echo "⚠️  Not possible to define OS, Linux by default: ${e.message}"
            credentialInfo.hasLinux = true
        }
    }
    
    return credentialInfo
}

/**
 * Prépare les variables Ansible pour l'exécution
 */
private def prepareAnsibleVars(Map config) {
    // Conversion String vers Map si nécessaire
    if (config.ansibleVars instanceof String) {
        def userVars = [:]
        
        config.ansibleVars.split('\n').each { line ->
            line = line.trim()
            if (line && line.contains('=')) {
                def parts = line.split('=', 2)
                if (parts.length == 2) {
                    userVars[parts[0].trim()] = parts[1].trim()
                }
            }
        }
        config.ansibleVars = userVars  // ← Fusion des deux Maps
    } else {
        config.ansibleVars = config.ansibleVars
    }
}

/**
 * Exécute le playbook Ansible avec les bons credentials
 */
private def executeAnsiblePlaybookWithCredentials(Map config) {
    def credInfo = config.credentialInfo
    
    if (credInfo.mixedEnvironment) {
        executePlaybookMixedEnvironment(config)
    } else if (credInfo.hasWindows) {
        executePlaybookWindows(config)
    } else {
        executePlaybookLinux(config)
    }
}

/**
 * Exécution pour serveurs Linux
 */
private def executePlaybookLinux(Map config) {
    withCredentials([
        sshUserPrivateKey(
            credentialsId: config.credentialInfo.linuxCredentialId,
            keyFileVariable: 'SSH_KEY_FILE',
            usernameVariable: 'SSH_USER'
        )
    ]) {
        executePlaybook(config, 'linux')
    }
}

/**
 * Exécution pour serveurs Windows
 */
private def executePlaybookWindows(Map config) {
    withCredentials([
        usernamePassword(
            credentialsId: config.credentialInfo.windowsCredentialId,
            usernameVariable: 'WIN_USER',
            passwordVariable: 'WIN_PASSWORD'
        )
    ]) {        
        executePlaybook(config, 'windows')
    }
}

/**
 * Exécution en environnement mixte (Linux + Windows)
 */
private def executePlaybookMixedEnvironment(Map config) {
    withCredentials([
        sshUserPrivateKey(
            credentialsId: config.credentialInfo.linuxCredentialId,
            keyFileVariable: 'SSH_KEY_FILE',
            usernameVariable: 'SSH_USER'
        ),
        usernamePassword(
            credentialsId: config.credentialInfo.windowsCredentialId,
            usernameVariable: 'WIN_USER',
            passwordVariable: 'WIN_PASSWORD'
        )
    ]) {
        executePlaybook(config, 'mixed')
    }
}

/**
 * Exécute le playbook Ansible avec les paramètres appropriés
 */
private def executePlaybook(Map config, String serverType) {
    // Construction de la commande ansible-playbook
    def ansibleCommand = "ansible-playbook"
    
    // Ajout du playbook
    ansibleCommand += " ${config.playbook_dir}/${config.playbook}"
    
    // Ajout de l'inventaire
    ansibleCommand += " -i ${config.inventory}"
    
    // Limitation aux serveurs cibles
    ansibleCommand += " -l ${config.targetServers}"
    
    // Gestion des privilèges (become)
    if (config.become && serverType != 'windows') {
        ansibleCommand += " --become"
        if (config.becomeUser) {
            ansibleCommand += " --become-user=${config.becomeUser}"
        }
    }
    
    // Ajout des tags si spécifiés
    if (config.tags) {
        ansibleCommand += " --tags '${config.tags}'"
        echo "🏷️  Tags appliqués: ${config.tags}"
    }
    
    // Mode check si demandé
    if (config.checkMode) {
        ansibleCommand += " --check"
        echo "🔍 Mode check activé - Aucune modification ne sera appliquée"
    }
    
    // Verbosité
    if (config.verbose) {
        ansibleCommand += " -vvv"
        echo "📢 Mode verbose activé"
    }
    
    // Construction des variables extra
    def allVars = config.ansibleVars ?: [:]
    
    // Ajout automatique de la variable HOST depuis TARGET_SERVERS
    allVars['HOST'] = config.targetServers
    
    if (allVars) {
        def extraVarsString = allVars.collect { k, v -> 
            "${k}='${v}'"
        }.join(' ')
        ansibleCommand += " --extra-vars \"${extraVarsString}\""
    }
    
    // Configuration spécifique selon le type de serveur
    switch(serverType) {
        case 'linux':
            // Pour Linux, configuration SSH minimale
            ansibleCommand = """
                export ANSIBLE_PRIVATE_KEY_FILE="\${SSH_KEY_FILE}"
                ${ansibleCommand}
            """
            break
            
        case 'windows':
            // Pour Windows, configuration WinRM
            ansibleCommand = """
                export ansible_user="\${WIN_USER}"
                export ansible_password="\${WIN_PASSWORD}"
                ${ansibleCommand}
            """
            break
            
        case 'mixed':
            // Pour environnement mixte, configuration pour les deux
            ansibleCommand = """
                export ANSIBLE_PRIVATE_KEY_FILE="\${SSH_KEY_FILE}"
                export ansible_user="\${WIN_USER}"
                export ansible_password="\${WIN_PASSWORD}"
                ${ansibleCommand}
            """
            break
    }
    
    // Timeout avec gestion d'erreur
    timeout(time: config.timeout, unit: 'SECONDS') {
        try {
            // Exécution de la commande shell
            def result = sh(
                script: ansibleCommand,
                returnStatus: true
            )
            
            if (result != 0) {
                error("❌ Échec de l'exécution du playbook Ansible (code retour: ${result})")
            }
            
            echo "✅ Playbook exécuté avec succès"
        } catch (Exception e) {
            error("❌ Échec de l'exécution du playbook: ${e.message}")
        }
    }
}