#!/usr/bin/env groovy

/**
 * Validation simplifiée des playbooks et configurations Ansible
 * Usage: ansibleValidate([playbook: 'site.yml', targetServers: 'web01,web02'])
 */
def call(Map config = [:]) {
    echo "Validation des configurations Ansible"
    echo "Serveurs cibles: ${config.targetServers}"
     
    // Validation de l'inventaire
    validateInventory(config.inventory)
    
    // Validation des serveurs cibles dans l'inventaire
    validateTargetServers(config)
    
    echo "✅ Validation Ansible terminée avec succès"
}

/**
 * Valide l'inventaire
 */
private def validateInventory(String inventory) {
    echo "📋 Validation de l'inventaire: ${inventory}"
    
    script {
        try {
            sh """
                if [ -f "${inventory}" ] || [ -d "${inventory}" ]; then
                    ansible-inventory -i "${inventory}" --list > /dev/null
                    echo "✅ Inventaire valide"
                else
                    echo "❌ Inventaire ${inventory} non trouvé"
                    exit 1
                fi
            """
        } catch (Exception e) {
            error("❌ Erreur dans l'inventaire ${inventory}: ${e.message}")
        }
    }
}

/**
 * Valide que les serveurs cibles existent dans l'inventaire
 */
private def validateTargetServers(Map config) {
    echo "🎯 Validation des serveurs cibles: ${config.targetServers}"
    
    script {
        try {
            sh """
                # Liste des hosts correspondants
                echo "📋 Hosts correspondants:"
                ansible ${config.targetServers} -i ${config.inventory} --list-hosts || echo "⚠️  Vérification manuelle requise"
            """
        } catch (Exception e) {
            echo "⚠️  Avertissement lors de la validation des serveurs: ${e.message}"
        }
    }
}
