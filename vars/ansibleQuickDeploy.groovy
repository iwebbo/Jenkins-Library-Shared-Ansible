def call(String role, String target = null, String playbook = 'site.yml', String extraVars = '') {
    // Version simplifiée pour usage rapide
    ansibleDeploy([
        defaultRole: role,
        defaultTarget: target ?: role,
        defaultPlaybook: playbook,
        defaultExtraVars: extraVars
    ])
}