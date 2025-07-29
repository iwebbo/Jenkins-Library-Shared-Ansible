#!/usr/bin/env groovy

/**
 * Validation simplifiée des playbooks et configurations Ansible
 * Usage: ansibleValidate([playbook: 'site.yml', targetServers: 'web01,web02'])
 */
def call(Map config = [:]) {
    echo "🔍 Validation des configurations Ansible"
    echo "🎯 Serveurs cibles: ${config.targetServers}"
    
    // Validation de la syntaxe du playbook
    validatePlaybookSyntax(config.playbook)
    
    // Validation de l'inventaire
    validateInventory(config.inventory)
    
    // Validation des serveurs cibles dans l'inventaire
    validateTargetServers(config)
    
    // Validation des variables Ansible
    validateAnsibleVars(config.ansibleVars)
    
    echo "✅ Validation Ansible terminée avec succès"
}

/**
 * Valide la syntaxe du playbook
 */
private def validatePlaybookSyntax(String playbook) {
    echo "📝 Validation de la syntaxe du playbook: ${playbook}"
    
    script {
        try {
            sh """
                pwd
                ls -lath
                ls -lath ansible/
                if [ -f "${playbook}" ]; then
                    ansible-playbook --syntax-check "${playbook}"
                    echo "✅ Syntaxe du playbook valide"
                else
                    echo "❌ Playbook ${playbook} non trouvé"
                    exit 1
                fi
            """
        } catch (Exception e) {
            error("❌ Erreur de syntaxe dans le playbook ${playbook}: ${e.message}")
        }
    }
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

/**
 * Valide les variables Ansible
 */
private def validateAnsibleVars(Map ansibleVars) {
    if (!ansibleVars) {
        echo "ℹ️  Aucune variable Ansible personnalisée définie"
        return
    }
    
    echo "🔧 Validation des variables Ansible:"
    
    ansibleVars.each { key, value ->
        // Validation du nom de variable (doit être valide pour Ansible)
        if (!key.matches('^[a-zA-Z_][a-zA-Z0-9_]*$')) {
            error("❌ Nom de variable invalide: '${key}'. Doit commencer par une lettre ou underscore.")
        }
        
        // Validation des valeurs sensibles
        if (key.toLowerCase().contains('password') || key.toLowerCase().contains('secret')) {
            echo "🔒 Variable sensible détectée: ${key} (valeur masquée)"
        } else {
            echo "   ${key}: ${value}"
        }
    }
    
    echo "✅ Variables Ansible validées"
}