#!/usr/bin/env groovy

/**
 * Validation simplifiée des playbooks et configurations Ansible
 * Usage: ansibleValidate([playbook: 'site.yml', targetServers: 'web01,web02'])
 */
def call(Map config = [:]) {
    echo "Starting validation of Ansible"
    echo "Target server: ${config.targetServers}"
     
    // Validation de l'inventaire
    validateInventory(config.inventory)
    
    // Validation des serveurs cibles dans l'inventaire
    validateTargetServers(config)
    
    echo "Validation of Ansible successfully"
}

/**
 * Valide l'inventaire
 */
private def validateInventory(String inventory) {
    script {
        try {
            sh """
                if [ -f "${inventory}" ] || [ -d "${inventory}" ]; then
                    ansible-inventory -i "${inventory}" --list > /dev/null
                else
                    exit 1
                fi
            """
        } catch (Exception e) {
            error("Error with inventory fodler ${inventory}: ${e.message}")
        }
    }
}

/**
 * Valide que les serveurs cibles existent dans l'inventaire
 */
private def validateTargetServers(Map config) {
    script {
        try {
            sh """
                # Match target_server to inventory 
                ansible ${config.targetServers} -i ${config.inventory} --list-hosts || echo "⚠️  Vérification manuelle requise"
            """
        } catch (Exception e) {
            echo "⚠️  Warning with servers check-in in inventory: ${e.message}"
        }
    }
}
