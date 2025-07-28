#!/usr/bin/env groovy

/**
 * Déploiement Ansible rapide simplifié
 * Usage: ansibleQuickDeploy('site.yml', 'web01,web02', [app_version: '1.0.0'])
 */
def call(String playbook, String targetServers, Map ansibleVars = [:]) {
    echo "⚡ Déploiement rapide Ansible"
    echo "📋 Playbook: ${playbook}"
    echo "🎯 Target Servers: ${targetServers}"
    if (ansibleVars) {
        echo "🔧 Variables: ${ansibleVars}"
    }
    
    // Configuration simplifiée pour déploiement rapide
    def config = [
        playbook: playbook,
        targetServers: targetServers,
        ansibleVars: ansibleVars,
        notification: false,  // Pas de notification pour les déploiements rapides
        verbose: true,
        checkMode: false,
        timeout: 1800  // 30 minutes pour déploiement rapide
    ]
    
    // Appel de la fonction principale
    ansibleDeploy(config)
}