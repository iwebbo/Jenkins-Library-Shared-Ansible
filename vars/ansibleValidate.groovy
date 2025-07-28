def call(Map config) {
    echo "🔍 Validation Ansible..."
    
    sh """
        echo "Validation des fichiers Ansible..."
        
        # Vérification inventaire
        if [ ! -f "${config.ansibleDir}/inventory/hosts.ini" ]; then
            echo "❌ Inventaire non trouvé: ${config.ansibleDir}/inventory/hosts.ini"
            exit 1
        fi
        
        # Vérification playbook
        if [ ! -f "${config.ansibleDir}/playbooks/${config.playbook}" ]; then
            echo "❌ Playbook non trouvé: ${config.playbook}"
            echo "Playbooks disponibles:"
            ls -la ${config.ansibleDir}/playbooks/
            exit 1
        fi
        
        # Vérification rôle (optionnel)
        if [ -d "${config.ansibleDir}/roles" ] && [ ! -d "${config.ansibleDir}/roles/${config.role}" ]; then
            echo "⚠️ Rôle ${config.role} non trouvé dans roles/"
            echo "Rôles disponibles:"
            ls -la ${config.ansibleDir}/roles/
        fi
        
        echo "✅ Fichiers Ansible validés"
        
        # Test syntaxe
        cd ${config.ansibleDir}
        ansible-playbook --syntax-check \\
            -i inventory/hosts.ini \\
            playbooks/${config.playbook}
    """
    
    echo "✅ Validation réussie"
}