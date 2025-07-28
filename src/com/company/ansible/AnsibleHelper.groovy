// ========================================
// src/com/company/ansible/AnsibleHelper.groovy
// ========================================

package com.company.ansible

class AnsibleHelper implements Serializable {
    def script
    
    AnsibleHelper(script) {
        this.script = script
    }
    
    // Configuration par défaut par rôle
    def getDefaultConfig(String role) {
        def configs = [
            'homeassistant': [
                defaultTarget: 'homeassistant',
                defaultPlaybook: 'site.yml',
                defaultExtraVars: '',
                emailTo: 'homeassistant-admin@company.com'
            ],
            'nextcloud': [
                defaultTarget: 'nextcloud',
                defaultPlaybook: 'site.yml',
                defaultExtraVars: '',
                emailTo: 'nextcloud-admin@company.com'
            ],
            'plex': [
                defaultTarget: 'plex',
                defaultPlaybook: 'site.yml',
                defaultExtraVars: '',
                emailTo: 'media-admin@company.com'
            ]
        ]
        
        return configs[role] ?: [
            defaultTarget: role,
            defaultPlaybook: 'site.yml',
            defaultExtraVars: '',
            emailTo: 'admin@company.com'
        ]
    }
    
    // Validation des paramètres
    def validateConfig(Map config) {
        def errors = []
        
        if (!config.role) {
            errors << "role est requis"
        }
        
        if (!config.target) {
            errors << "target est requis"
        }
        
        if (!config.playbook) {
            errors << "playbook est requis"
        }
        
        if (errors) {
            script.error("❌ Erreurs de configuration: ${errors.join(', ')}")
        }
        
        return true
    }
    
    // Génération de commande Ansible
    def buildAnsibleCommand(Map config) {
        def cmd = """
            cd ${config.ansibleDir ?: '/tmp/ansibleJenkins/ansible'}
            
            ansible-playbook \\
                -i inventory/hosts.ini \\
                playbooks/${config.playbook} \\
                --limit ${config.target}
        """
        
        if (config.extraVars?.trim()) {
            cmd += " -e \"${config.extraVars}\""
        }
        
        if (config.options?.trim()) {
            cmd += " ${config.options}"
        }
        
        return cmd
    }
}