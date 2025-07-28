package com.company.ansible

/**
 * Classe helper pour les opérations Ansible avancées
 * Utilisable dans les shared libraries Jenkins
 */
class AnsibleHelper implements Serializable {
    
    def script
    
    AnsibleHelper(script) {
        this.script = script
    }
    
    /**
     * Valide la connectivité vers les serveurs cibles
     */
    def validateConnectivity(String targetServers, String inventory) {
        script.echo "🔗 Test de connectivité vers: ${targetServers}"
        
        try {
            def result = script.sh(
                script: "ansible ${targetServers} -i ${inventory} -m ping --one-line",
                returnStdout: true
            ).trim()
            
            script.echo "✅ Connectivité OK"
            return [success: true, output: result]
        } catch (Exception e) {
            script.echo "❌ Problème de connectivité: ${e.message}"
            return [success: false, error: e.message]
        }
    }
    
    /**
     * Récupère les informations système des serveurs cibles
     */
    def gatherServerInfo(String targetServers, String inventory) {
        script.echo "📋 Collecte d'informations sur: ${targetServers}"
        
        try {
            def result = script.sh(
                script: """
                    ansible ${targetServers} -i ${inventory} -m setup -a "filter=ansible_os_family,ansible_distribution,ansible_hostname" --one-line
                """,
                returnStdout: true
            ).trim()
            
            return parseServerInfo(result)
        } catch (Exception e) {
            script.echo "⚠️  Impossible de collecter les informations: ${e.message}"
            return [:]
        }
    }
    
    /**
     * Parse les informations serveurs depuis la sortie Ansible
     */
    private def parseServerInfo(String output) {
        def serverInfo = [:]
        
        output.split('\n').each { line ->
            if (line.contains('SUCCESS')) {
                def parts = line.split('\\|')
                if (parts.length >= 2) {
                    def hostname = parts[0].trim()
                    def info = parts[1].trim()
                    
                    // Extraction des informations OS
                    def osFamily = extractValue(info, 'ansible_os_family')
                    def distribution = extractValue(info, 'ansible_distribution')
                    
                    serverInfo[hostname] = [
                        os_family: osFamily,
                        distribution: distribution,
                        is_windows: osFamily?.toLowerCase()?.contains('windows') ?: false,
                        is_linux: !osFamily?.toLowerCase()?.contains('windows') ?: true
                    ]
                }
            }
        }
        
        return serverInfo
    }
    
    /**
     * Extrait une valeur spécifique depuis une chaîne JSON-like
     */
    private def extractValue(String text, String key) {
        def pattern = /"${key}":\s*"([^"]+)"/
        def matcher = text =~ pattern
        return matcher ? matcher[0][1] : null
    }
    
    /**
     * Détermine les credentials appropriés selon le type de serveurs
     */
    def determineCredentials(String targetServers, String inventory) {
        script.echo "🔑 Détermination des credentials pour: ${targetServers}"
        
        def serverInfo = gatherServerInfo(targetServers, inventory)
        def credentialInfo = [
            hasWindows: false,
            hasLinux: false,
            windowsCredentialId: 'windows-ansible-creds',
            linuxCredentialId: 'linux-ansible-creds',
            mixedEnvironment: false,
            serverDetails: serverInfo
        ]
        
        // Analyse des serveurs
        serverInfo.each { hostname, info ->
            if (info.is_windows) {
                credentialInfo.hasWindows = true
                script.echo "🪟 Serveur Windows détecté: ${hostname}"
            } else {
                credentialInfo.hasLinux = true
                script.echo "🐧 Serveur Linux détecté: ${hostname}"
            }
        }
        
        // Détection par nom si pas d'informations système
        if (!credentialInfo.hasWindows && !credentialInfo.hasLinux) {
            if (targetServers.toLowerCase().contains('win') || 
                targetServers.toLowerCase().contains('windows')) {
                credentialInfo.hasWindows = true
                script.echo "🪟 Windows détecté par nom: ${targetServers}"
            } else {
                credentialInfo.hasLinux = true
                script.echo "🐧 Linux par défaut: ${targetServers}"
            }
        }
        
        // Environnement mixte
        if (credentialInfo.hasWindows && credentialInfo.hasLinux) {
            credentialInfo.mixedEnvironment = true
            script.echo "🔄 Environnement mixte détecté"
        }
        
        return credentialInfo
    }
    
    /**
     * Construit les paramètres extra-vars pour Ansible
     */
    def buildExtraVars(Map ansibleVars) {
        if (!ansibleVars) {
            return ""
        }
        
        def extraVars = []
        
        ansibleVars.each { key, value ->
            // Échappement des valeurs avec espaces ou caractères spéciaux
            def escapedValue = value.toString()
            if (escapedValue.contains(' ') || escapedValue.contains('"')) {
                escapedValue = "\"${escapedValue.replace('"', '\\"')}\""
            }
            extraVars.add("${key}=${escapedValue}")
        }
        
        return extraVars.join(' ')
    }
    
    /**
     * Valide les noms de variables Ansible
     */
    def validateVariableNames(Map ansibleVars) {
        def invalidVars = []
        
        ansibleVars.each { key, value ->
            if (!key.matches('^[a-zA-Z_][a-zA-Z0-9_]*$')) {
                invalidVars.add(key)
            }
        }
        
        if (invalidVars) {
            script.error("❌ Variables Ansible invalides: ${invalidVars.join(', ')}")
        }
        
        script.echo "✅ Toutes les variables Ansible sont valides"
    }
    
    /**
     * Génère un rapport de déploiement
     */
    def generateDeploymentReport(Map config, def startTime, def endTime, def status) {
        def duration = (endTime - startTime) / 1000  // en secondes
        
        def report = [
            playbook: config.playbook,
            targetServers: config.targetServers,
            ansibleVars: config.ansibleVars,
            tags: config.tags ?: 'all',
            checkMode: config.checkMode,
            status: status,
            duration: "${duration}s",
            timestamp: new Date().format('yyyy-MM-dd HH:mm:ss'),
            jenkins: [
                buildNumber: script.env.BUILD_NUMBER,
                buildUrl: script.env.BUILD_URL,
                jobName: script.env.JOB_NAME
            ]
        ]
        
        script.echo "📊 Rapport de déploiement généré"
        return report
    }
    
    /**
     * Sauvegarde le rapport de déploiement
     */
    def saveDeploymentReport(Map report) {
        try {
            def reportJson = groovy.json.JsonBuilder(report).toPrettyString()
            script.writeFile file: 'deployment-report.json', text: reportJson
            script.echo "💾 Rapport sauvegardé: deployment-report.json"
        } catch (Exception e) {
            script.echo "⚠️  Impossible de sauvegarder le rapport: ${e.message}"
        }
    }
    
    /**
     * Formate les logs Ansible pour une meilleure lisibilité
     */
    def formatAnsibleOutput(String output) {
        def lines = output.split('\n')
        def formattedLines = []
        
        lines.each { line ->
            if (line.contains('TASK [')) {
                formattedLines.add("🔧 ${line}")
            } else if (line.contains('PLAY [')) {
                formattedLines.add("📋 ${line}")
            } else if (line.contains('changed:')) {
                formattedLines.add("✅ ${line}")
            } else if (line.contains('failed:')) {
                formattedLines.add("❌ ${line}")
            } else if (line.contains('ok:')) {
                formattedLines.add("✓ ${line}")
            } else {
                formattedLines.add(line)
            }
        }
        
        return formattedLines.join('\n')
    }
    
    /**
     * Vérifie les prérequis Ansible
     */
    def checkPrerequisites() {
        script.echo "🔍 Vérification des prérequis Ansible"
        
        try {
            // Vérification de la version Ansible
            def version = script.sh(
                script: "ansible --version | head -n1",
                returnStdout: true
            ).trim()
            script.echo "✅ ${version}"
            
            // Vérification de ansible-playbook
            script.sh "which ansible-playbook > /dev/null"
            script.echo "✅ ansible-playbook disponible"
            
            return true
        } catch (Exception e) {
            script.error("❌ Prérequis Ansible manquants: ${e.message}")
            return false
        }
    }
}