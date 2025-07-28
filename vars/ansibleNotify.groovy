def call(Map config) {
    def status = config.result == 'SUCCESS' ? '✅ SUCCÈS' : '❌ ÉCHEC'
    def emoji = config.result == 'SUCCESS' ? '🚀' : '❌'
    
    // Rapport
    def report = """
    ═══════════════════════════════════════
    📋 ANSIBLE SHARED LIBRARY REPORT
    ═══════════════════════════════════════
    Rôle: ${config.role}
    Target: ${config.target}
    Playbook: ${config.playbook}
    Build: #${config.buildNumber}
    Statut: ${config.result}
    Date: ${new Date().format('yyyy-MM-dd HH:mm:ss')}
    ═══════════════════════════════════════
    """
    
    writeFile file: "ansible_report_${config.buildNumber}.txt", text: report
    archiveArtifacts artifacts: "ansible_report_${config.buildNumber}.txt", allowEmptyArchive: true
    
    // Email
    try {
        mail to: config.emailTo,
             subject: "[Jenkins] Ansible ${config.role} - ${status}",
             body: """
             ${emoji} ANSIBLE DEPLOYMENT - ${status}
             
             Rôle: ${config.role}
             Target: ${config.target}
             Playbook: ${config.playbook}
             Build: #${config.buildNumber}
             
             📊 Détails: ${config.buildUrl}
             
             ${config.result == 'SUCCESS' ? '✅ Déploiement réussi' : '⚠️ Vérifiez les logs'}
             """
    } catch (Exception e) {
        echo "⚠️ Erreur envoi email: ${e.getMessage()}"
    }
    
    currentBuild.description = "${config.role} → ${config.target}"
}