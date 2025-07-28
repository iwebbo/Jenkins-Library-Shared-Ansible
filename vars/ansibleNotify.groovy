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
        ansibleVars: [:],
        error: '',
        duration: '',
        sendEmail: true,
        sendSlack: false
    ]
    
    config = defaultConfig + config
    
    echo "📧 Envoi de notification - Status: ${config.status}"
    
    try {
        // Préparation des données pour la notification
        def notificationData = prepareNotificationData(config)
        
        // Envoi des notifications selon la configuration
        if (config.sendEmail) {
            sendEmailNotification(notificationData)
        }
        
        if (config.sendSlack) {
            sendSlackNotification(notificationData)
        }
        
    } catch (Exception e) {
        echo "⚠️  Erreur lors de l'envoi de notification: ${e.message}"
    }
}

/**
 * Prépare les données pour la notification
 */
private def prepareNotificationData(Map config) {
    def statusEmoji = getStatusEmoji(config.status)
    def statusColor = getStatusColor(config.status)
    def statusText = getStatusText(config.status)
    
    return [
        status: config.status,
        statusEmoji: statusEmoji,
        statusColor: statusColor,
        statusText: statusText,
        playbook: config.playbook,
        targetServers: config.targetServers,
        ansibleVars: config.ansibleVars,
        error: config.error,
        duration: config.duration,
        timestamp: new Date().format('yyyy-MM-dd HH:mm:ss'),
        jenkins: [
            buildNumber: env.BUILD_NUMBER,
            buildUrl: env.BUILD_URL,
            jobName: env.JOB_NAME,
            buildUser: env.BUILD_USER ?: 'jenkins'
        ]
    ]
}

/**
 * Envoi de notification par email
 */
private def sendEmailNotification(Map data) {
    try {
        def subject = "${data.statusEmoji} Ansible Deploy ${data.statusText} - ${data.playbook}"
        def body = buildEmailBody(data)
        def recipients = getEmailRecipients(data.status)
        
        emailext(
            subject: subject,
            body: body,
            to: recipients,
            mimeType: 'text/html'
        )
        
        echo "✅ Email envoyé à: ${recipients}"
    } catch (Exception e) {
        echo "❌ Erreur envoi email: ${e.message}"
    }
}

/**
 * Construction du corps de l'email
 */
private def buildEmailBody(Map data) {
    def template = """
    <html>
    <head>
        <style>
            body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
            .header { background: ${data.statusColor}; color: white; padding: 20px; text-align: center; }
            .content { padding: 20px; }
            .info-table { width: 100%; border-collapse: collapse; margin: 20px 0; }
            .info-table th, .info-table td { border: 1px solid #ddd; padding: 12px; text-align: left; }
            .info-table th { background-color: #f2f2f2; }
            .error-box { background-color: #f8d7da; border: 1px solid #f5c6cb; padding: 15px; margin: 10px 0; border-radius: 5px; }
            .vars-box { background-color: #d1ecf1; border: 1px solid #bee5eb; padding: 15px; margin: 10px 0; border-radius: 5px; }
        </style>
    </head>
    <body>
        <div class="header">
            <h1>${data.statusEmoji} Déploiement Ansible ${data.statusText}</h1>
        </div>
        
        <div class="content">
            <h2>Détails du Déploiement</h2>
            
            <table class="info-table">
                <tr><th>Playbook</th><td>${data.playbook}</td></tr>
                <tr><th>Serveurs Cibles</th><td>${data.targetServers}</td></tr>
                <tr><th>Status</th><td>${data.statusText}</td></tr>
                <tr><th>Durée</th><td>${data.duration}</td></tr>
                <tr><th>Timestamp</th><td>${data.timestamp}</td></tr>
                <tr><th>Déclenché par</th><td>${data.jenkins.buildUser}</td></tr>
                <tr><th>Build Jenkins</th><td><a href="${data.jenkins.buildUrl}">#${data.jenkins.buildNumber}</a></td></tr>
            </table>
            
            ${data.ansibleVars ? buildVarsSection(data.ansibleVars) : ''}
            ${data.error ? buildErrorSection(data.error) : ''}
        </div>
    </body>
    </html>
    """
    
    return template
}

/**
 * Construction de la section variables
 */
private def buildVarsSection(Map vars) {
    def varsHtml = '<div class="vars-box"><h3>Variables Ansible</h3><ul>'
    vars.each { key, value ->
        if (key.toLowerCase().contains('password') || key.toLowerCase().contains('secret')) {
            varsHtml += "<li><strong>${key}:</strong> *** (masqué)</li>"
        } else {
            varsHtml += "<li><strong>${key}:</strong> ${value}</li>"
        }
    }
    varsHtml += '</ul></div>'
    return varsHtml
}

/**
 * Construction de la section erreur
 */
private def buildErrorSection(String error) {
    return """
    <div class="error-box">
        <h3>❌ Erreur</h3>
        <pre>${error}</pre>
    </div>
    """
}

/**
 * Envoi de notification Slack
 */
private def sendSlackNotification(Map data) {
    try {
        def message = buildSlackMessage(data)
        
        slackSend(
            channel: '#deployments',
            color: data.statusColor,
            message: message
        )
        
        echo "✅ Notification Slack envoyée"
    } catch (Exception e) {
        echo "❌ Erreur Slack: ${e.message}"
    }
}

/**
 * Construction du message Slack
 */
private def buildSlackMessage(Map data) {
    def message = """
${data.statusEmoji} *Ansible Deploy ${data.statusText}*

*Playbook:* ${data.playbook}
*Serveurs:* ${data.targetServers}
*Durée:* ${data.duration}
*Build:* <${data.jenkins.buildUrl}|#${data.jenkins.buildNumber}>
*Par:* ${data.jenkins.buildUser}
    """
    
    if (data.error) {
        message += "\n*Erreur:* ```${data.error}```"
    }
    
    return message
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
    switch (status.toLowerCase()) {
        case 'failure':
            return 'devops-team@company.com,dev-team@company.com'
        case 'success':
            return 'devops-team@company.com'
        default:
            return 'devops-team@company.com'
    }
}