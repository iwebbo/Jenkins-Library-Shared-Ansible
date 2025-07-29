#!/usr/bin/env groovy

/**
 * Gestion des notifications pour les déploiements Ansible
 * Usage: ansibleNotify([status: 'success', playbook: 'site.yml', targetServers: 'web01,web02'])
 */
def call(Map config = [:]) {
    // Configuration par défaut
    def defaultConfig = [
        status: 'unknown',
        playbook: '',
        targetServers: '',
        inventory: '',
        tags: '',
        version: '',
        duration: '',
        ansibleVars: [:],
        error: '',
        sendEmail: true,
        sendSlack: false,
        extraVars: '',
        packageName: '',
        environment: ''
    ]
    
    config = defaultConfig + config
    
    echo "📧 Envoi de notification Ansible - Status: ${config.status}"
    
    try {
        // Génération du rapport détaillé
        generateAnsibleReport(config)
        
        // Envoi des notifications selon la configuration
        if (config.sendEmail) {
            sendAnsibleEmail(config)
        }
        
        if (config.sendSlack) {
            sendSlackNotification(config)
        }
        
        // Mise à jour de la description du build
        updateBuildDescription(config)
        
    } catch (Exception e) {
        echo "⚠️  Erreur lors de l'envoi de notification: ${e.message}"
    }
}

/**
 * Génère un rapport détaillé du déploiement Ansible
 */
private def generateAnsibleReport(Map config) {
    try {
        def statusEmoji = getStatusEmoji(config.status)
        def timestamp = new Date().format('yyyy-MM-dd HH:mm:ss')
        
        def report = """
=== RAPPORT DÉPLOIEMENT ANSIBLE ===
${statusEmoji} Statut: ${config.status.toUpperCase()}
Playbook: ${config.playbook ?: 'Non spécifié'}
Inventaire: ${config.inventory ?: 'Non spécifié'}
Serveurs Cibles: ${config.targetServers ?: 'Non spécifiés'}
Tags: ${config.tags ?: 'Aucun'}
Version: ${config.version ?: 'Non spécifiée'}
Environnement: ${config.environment ?: 'Non spécifié'}
Durée: ${config.duration ?: 'Non calculée'}
Package (si applicable): ${config.packageName ?: 'N/A'}

Détails Jenkins:
- Build: #${env.BUILD_NUMBER}
- Job: ${env.JOB_NAME}
- URL Build: ${env.BUILD_URL}
- Console: ${env.BUILD_URL}console
- Déclenché par: ${env.BUILD_USER ?: 'jenkins'}
- Date: ${timestamp}

Variables Ansible Extra:
${config.extraVars ? config.extraVars : 'Aucune variable extra'}

Variables de configuration:
${formatVarsForReport(config.ansibleVars)}

${config.error ? "❌ ERREUR:\n${config.error}" : '✅ Aucune erreur signalée'}
==========================================
        """
        
        writeFile file: 'ansible_deployment_report.txt', text: report
        archiveArtifacts artifacts: 'ansible_deployment_report.txt', allowEmptyArchive: true
        
        echo "✅ Rapport Ansible généré: ansible_deployment_report.txt"
    } catch (Exception e) {
        echo "⚠️ Erreur lors de la génération du rapport: ${e.message}"
    }
}

/**
 * Formate les variables pour le rapport
 */
private def formatVarsForReport(Map vars) {
    if (!vars || vars.isEmpty()) {
        return "Aucune variable de configuration"
    }
    
    def formatted = ""
    vars.each { key, value ->
        if (key.toLowerCase().contains('password') || 
            key.toLowerCase().contains('secret') || 
            key.toLowerCase().contains('token')) {
            formatted += "\n  - ${key}: *** (masqué pour sécurité)"
        } else {
            formatted += "\n  - ${key}: ${value}"
        }
    }
    return formatted
}

/**
 * Envoi de notification par email (version simplifiée)
 */
private def sendAnsibleEmail(Map config) {
    try {
        def statusEmoji = getStatusEmoji(config.status)
        def statusText = getStatusText(config.status)
        def timestamp = new Date().format('yyyy-MM-dd HH:mm:ss')
        
        def subject = "[Jenkins] Ansible ${statusText} - ${config.playbook ?: 'Déploiement'}"
        if (config.status == 'failure') {
            subject = "[Jenkins] ❌ Ansible ÉCHEC - ${config.playbook ?: 'Déploiement'}"
        }
        
        def emailBody = buildEmailBody(config, statusEmoji, statusText, timestamp)
        def recipients = getEmailRecipients(config.status)
        
        mail to: recipients,
             subject: subject,
             body: emailBody
        
        echo "✅ Email Ansible envoyé à: ${recipients}"
    } catch (Exception e) {
        echo "❌ Erreur envoi email Ansible: ${e.message}"
    }
}

/**
 * Construction du corps de l'email (texte formaté)
 */
private def buildEmailBody(Map config, String statusEmoji, String statusText, String timestamp) {
    if (config.status == 'success') {
        return buildSuccessEmailBody(config, statusEmoji, statusText, timestamp)
    } else {
        return buildFailureEmailBody(config, statusEmoji, statusText, timestamp)
    }
}

/**
 * Email de succès
 */
private def buildSuccessEmailBody(Map config, String statusEmoji, String statusText, String timestamp) {
    return """
${statusEmoji} ANSIBLE DÉPLOIEMENT - SUCCÈS

Playbook: ${config.playbook ?: 'Non spécifié'}
Inventaire: ${config.inventory ?: 'Non spécifié'}
Serveurs: ${config.targetServers ?: 'Non spécifiés'}
Version: ${config.version ?: 'Non spécifiée'}
Environnement: ${config.environment ?: 'Non spécifié'}
Durée: ${config.duration ?: 'Non calculée'}
Build: #${env.BUILD_NUMBER}
Date: ${timestamp}

📊 Détails du build: ${env.BUILD_URL}

Configuration utilisée:
- Tags Ansible: ${config.tags ?: 'Aucun'}
- Variables Extra: ${config.extraVars ?: 'Aucune'}
- Package (si applicable): ${config.packageName ?: 'N/A'}

Variables Ansible:
${config.ansibleVars ? formatVarsForEmail(config.ansibleVars) : '- Aucune variable configurée'}

✅ Le déploiement s'est terminé avec succès.
    """
}

/**
 * Email d'échec
 */
private def buildFailureEmailBody(Map config, String statusEmoji, String statusText, String timestamp) {
    return """
❌ ANSIBLE DÉPLOIEMENT - ÉCHEC

Playbook: ${config.playbook ?: 'Non spécifié'}
Inventaire: ${config.inventory ?: 'Non spécifié'}
Serveurs: ${config.targetServers ?: 'Non spécifiés'}
Version: ${config.version ?: 'Non spécifiée'}
Environnement: ${config.environment ?: 'Non spécifié'}
Build: #${env.BUILD_NUMBER}
Date: ${timestamp}

🔍 Logs d'erreur: ${env.BUILD_URL}console

Configuration utilisée:
- Tags Ansible: ${config.tags ?: 'Aucun'}
- Variables Extra: ${config.extraVars ?: 'Aucune'}
- Package (si applicable): ${config.packageName ?: 'N/A'}

Variables Ansible configurées:
${config.ansibleVars ? formatVarsForEmail(config.ansibleVars) : '- Aucune variable configurée'}

❌ ERREUR DÉTECTÉE:
${config.error ?: 'Erreur non spécifiée - Consultez les logs Jenkins'}

⚠️ Veuillez vérifier les logs pour plus de détails.
    """
}

/**
 * Formate les variables pour l'email
 */
private def formatVarsForEmail(Map vars) {
    def formatted = ""
    vars.each { key, value ->
        if (key.toLowerCase().contains('password') || 
            key.toLowerCase().contains('secret') || 
            key.toLowerCase().contains('token')) {
            formatted += "- ${key}: *** (masqué)\n"
        } else {
            formatted += "- ${key}: ${value}\n"
        }
    }
    return formatted
}

/**
 * Met à jour la description du build
 */
private def updateBuildDescription(Map config) {
    try {
        def statusEmoji = getStatusEmoji(config.status)
        def description = "${statusEmoji} ${config.playbook ?: 'Ansible'}"
        
        if (config.targetServers) {
            description += " → ${config.targetServers}"
        }
        
        if (config.environment) {
            description += " (${config.environment})"
        }
        
        if (config.status == 'failure') {
            description += " - ÉCHEC"
        }
        
        currentBuild.description = description
        echo "✅ Description du build mise à jour: ${description}"
    } catch (Exception e) {
        echo "⚠️ Erreur mise à jour description: ${e.message}"
    }
}

/**
 * Envoi de notification Slack
 */
private def sendSlackNotification(Map config) {
    try {
        def statusEmoji = getStatusEmoji(config.status)
        def statusText = getStatusText(config.status)
        def statusColor = getStatusColor(config.status)
        
        def message = """
${statusEmoji} *Ansible Deploy ${statusText}*

*Playbook:* ${config.playbook ?: 'Non spécifié'}
*Serveurs:* ${config.targetServers ?: 'Non spécifiés'}
*Environnement:* ${config.environment ?: 'Non spécifié'}
*Durée:* ${config.duration ?: 'Non calculée'}
*Build:* <${env.BUILD_URL}|#${env.BUILD_NUMBER}>
*Par:* ${env.BUILD_USER ?: 'jenkins'}
        """
        
        if (config.error) {
            message += "\n*Erreur:* ```${config.error}```"
        }
        
        slackSend(
            channel: '#deployments',
            color: statusColor,
            message: message
        )
        
        echo "✅ Notification Slack envoyée"
    } catch (Exception e) {
        echo "❌ Erreur Slack: ${e.message}"
    }
}

/**
 * Obtient l'emoji selon le status
 */
private def getStatusEmoji(String status) {
    switch (status.toLowerCase()) {
        case 'success': return '✅'
        case 'failure': return '❌'
        case 'warning': return '⚠️'
        case 'info': return 'ℹ️'
        default: return '❓'
    }
}

/**
 * Obtient la couleur selon le status
 */
private def getStatusColor(String status) {
    switch (status.toLowerCase()) {
        case 'success': return '#28a745'
        case 'failure': return '#dc3545'
        case 'warning': return '#ffc107'
        case 'info': return '#17a2b8'
        default: return '#6c757d'
    }
}

/**
 * Obtient le texte selon le status
 */
private def getStatusText(String status) {
    switch (status.toLowerCase()) {
        case 'success': return 'Réussi'
        case 'failure': return 'Échec'
        case 'warning': return 'Avertissement'
        case 'info': return 'Information'
        default: return 'Inconnu'
    }
}

/**
 * Obtient les destinataires email selon le status
 */
private def getEmailRecipients(String status) {
    // Vous pouvez personnaliser selon vos besoins
    switch (status.toLowerCase()) {
        case 'failure':
            return 'l.kieran95@gmail.com'
        case 'success':
            return 'l.kieran95@gmail.com'
        default:
            return 'l.kieran95@gmail.com'
    }
}