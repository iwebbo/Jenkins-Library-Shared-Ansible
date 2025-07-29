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
        error("Le paramètre 'playbook' est obligatoire")
    }
    if (!config.targetServers) {
        error("Le paramètre 'targetServers' est obligatoire")
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

    echo "🚀 Début du déploiement Ansible"
    echo "Playbook: ${config.playbook}"
    echo "Target Servers: ${config.targetServers}"
    if (config.ansibleVars) {
        echo "🔧 Variables Ansible: ${config.ansibleVars}"
    }
    
    try {
        // Étape 1: Détection du type de serveurs et credentials
        stage('Détection Credentials') {
            config.credentialInfo = detectServerCredentials(config.targetServers, config.inventory)
            echo "🔑 Credentials détectés: ${config.credentialInfo}"
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
            
            echo "ℹ️  Informations serveurs: ${serverInfo}"
            
            // Détection Windows (recherche de patterns Windows)
            if (serverInfo.toLowerCase().contains('windows') || 
                serverInfo.toLowerCase().contains('win') ||
                targetServers.toLowerCase().contains('win') ||
                targetServers.toLowerCase().contains('windows')) {
                credentialInfo.hasWindows = true
                echo "🪟 Serveurs Windows détectés"
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
                echo "🐧 Serveurs Linux détectés"
            }
            
            // Environnement mixte
            if (credentialInfo.hasWindows && credentialInfo.hasLinux) {
                credentialInfo.mixedEnvironment = true
                echo "🔄 Environnement mixte détecté (Windows + Linux)"
            }
            
        } catch (Exception e) {
            echo "⚠️  Impossible de détecter le type de serveurs, utilisation Linux par défaut: ${e.message}"
            credentialInfo.hasLinux = true
        }
    }
    
    return credentialInfo
}

/**
 * Prépare les variables Ansible pour l'exécution
 */
private def prepareAnsibleVars(Map config) {
    // Variables système automatiques
    def systemVars = [
        'jenkins_build_number': env.BUILD_NUMBER,
        'jenkins_build_url': env.BUILD_URL,
        'jenkins_job_name': env.JOB_NAME,
        'deployment_timestamp': new Date().format('yyyy-MM-dd_HH-mm-ss'),
        'deployed_by': env.BUILD_USER ?: 'jenkins'
    ]
    
    // Conversion String vers Map si nécessaire
    if (config.ansibleVars instanceof String) {
        echo "🔄 Conversion des variables String vers Map"
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
        config.ansibleVars = systemVars + userVars  // ← Fusion des deux Maps
    } else {
        config.ansibleVars = systemVars + config.ansibleVars
    }
}

/**
 * Exécute le playbook Ansible avec les bons credentials
 */
private def executeAnsiblePlaybookWithCredentials(Map config) {
    def credInfo = config.credentialInfo
    
    if (credInfo.mixedEnvironment) {
        echo "🔄 Exécution en environnement mixte"
        executePlaybookMixedEnvironment(config)
    } else if (credInfo.hasWindows) {
        echo "🪟 Exécution pour serveurs Windows"
        executePlaybookWindows(config)
    } else {
        echo "🐧 Exécution pour serveurs Linux"
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
        // Configuration des variables d'environnement pour Windows
        env.ANSIBLE_CONNECTION = 'winrm'
        env.ANSIBLE_WINRM_TRANSPORT = 'ntlm'
        env.ANSIBLE_WINRM_SERVER_CERT_VALIDATION = 'ignore'
        
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
        echo "🔄 Configuration pour environnement mixte"
        executePlaybook(config, 'mixed')
    }
}

/**
 * Exécute le playbook Ansible avec les paramètres appropriés
 */
private def executePlaybook(Map config, String serverType) {
    // Construction des paramètres de base
    def playbookParams = [
        playbook: config.playbook,
        inventory: config.inventory,
        limit: config.targetServers,
        disableHostKeyChecking: true,
        colorized: true,
        become: config.become,
        becomeUser: config.becomeUser,
        forks: config.forks
    ]
    
    // Configuration spécifique selon le type de serveur
    switch(serverType) {
        case 'linux':
            playbookParams.credentialsId = config.credentialInfo.linuxCredentialId
            break
        case 'windows':
            // Pour Windows, utilisation des variables d'environnement
            playbookParams.become = false  // Pas de sudo sur Windows
            break
        case 'mixed':
            // En environnement mixte, utiliser le credential Linux par défaut
            // Les credentials Windows sont gérés via les variables d'environnement
            playbookParams.credentialsId = config.credentialInfo.linuxCredentialId
            break
    }
    
    // Ajout des tags si spécifiés
    if (config.tags) {
        playbookParams.tags = config.tags
        echo "🏷️  Tags appliqués: ${config.tags}"
    }
    
    // Construction des variables extra avec HOST automatique
    def allVars = config.ansibleVars ?: [:]
    
    // Ajout automatique de la variable HOST depuis TARGET_SERVERS
    allVars['HOST'] = config.targetServers
    echo "🎯 Variable HOST ajoutée: ${config.targetServers}"
    
    if (allVars) {
        def extraVarsString = allVars.collect { k, v -> "${k}=${v}" }.join(' ')
        playbookParams.extraVars = [
            extraVars: extraVarsString
        ]
        echo "🔧 Variables extra: ${extraVarsString}"
    }
    
    // Mode check si demandé
    if (config.checkMode) {
        playbookParams.check = true
        echo "🔍 Mode check activé - Aucune modification ne sera appliquée"
    }
    
    // Verbosité
    if (config.verbose) {
        playbookParams.verbose = true
        echo "📢 Mode verbose activé"
    }
    
    echo "🎯 Exécution sur les serveurs: ${config.targetServers}"
    echo "📋 Playbook: ${config.playbook}"
    echo "🖥️  Type de serveurs: ${serverType}"
    
    // Timeout avec gestion d'erreur
    timeout(time: config.timeout, unit: 'SECONDS') {
        try {
            // Utilisation du plugin Ansible Jenkins
            ansiblePlaybook(playbookParams)
            echo "✅ Playbook exécuté avec succès"
        } catch (Exception e) {
            error("❌ Échec de l'exécution du playbook: ${e.message}")
        }
    }
}