package com.company.ansible

/**
 * Helper class for advanced Ansible operations
 * Usable in Jenkins shared libraries
 */
class AnsibleHelper implements Serializable {
    
    def script
    
    AnsibleHelper(script) {
        this.script = script
    }
    
    /**
     * Validates connectivity to target servers
     */
    def validateConnectivity(String targetServers, String inventory) {
        script.echo "🔗 Testing connectivity to: ${targetServers}"
        
        try {
            def result = script.sh(
                script: "ansible ${targetServers} -i ${inventory} -m ping --one-line",
                returnStdout: true
            ).trim()
            
            script.echo "✅ Connectivity OK"
            return [success: true, output: result]
        } catch (Exception e) {
            script.echo "❌ Connectivity issue: ${e.message}"
            return [success: false, error: e.message]
        }
    }
    
    /**
     * Gathers system information from target servers
     */
    def gatherServerInfo(String targetServers, String inventory) {
        script.echo "📋 Collecting information from: ${targetServers}"
        
        try {
            def result = script.sh(
                script: """
                    ansible ${targetServers} -i ${inventory} -m setup -a "filter=ansible_os_family,ansible_distribution,ansible_hostname" --one-line
                """,
                returnStdout: true
            ).trim()
            
            return parseServerInfo(result)
        } catch (Exception e) {
            script.echo "⚠️  Unable to collect information: ${e.message}"
            return [:]
        }
    }
    
    /**
     * Parses server information from Ansible output
     */
    private def parseServerInfo(String output) {
        def serverInfo = [:]
        
        output.split('\n').each { line ->
            if (line.contains('SUCCESS')) {
                def parts = line.split('\\|')
                if (parts.length >= 2) {
                    def hostname = parts[0].trim()
                    def info = parts[1].trim()
                    
                    // Extract OS information
                    def osFamily = extractValue(info, 'ansible_os_family')
                    def distribution = extractValue(info, 'ansible_distribution')
                    
                    serverInfo[hostname] = [
                        os_family: osFamily,
                        distribution: distribution,
                        is_windows: osFamily?.toLowerCase()?.contains('windows') ?: false,
                        is_linux: !osFamily?.toLowerCase()?.contains('windows') ?: true
                    ]
                }
            }
        }
        
        return serverInfo
    }
    
    /**
     * Extracts a specific value from a JSON-like string
     */
    private def extractValue(String text, String key) {
        def pattern = /"${key}":\s*"([^"]+)"/
        def matcher = text =~ pattern
        return matcher ? matcher[0][1] : null
    }
    
    /**
     * Determines appropriate credentials based on server types
     */
    def determineCredentials(String targetServers, String inventory) {
        script.echo "🔑 Determining credentials for: ${targetServers}"
        
        def serverInfo = gatherServerInfo(targetServers, inventory)
        def credentialInfo = [
            hasWindows: false,
            hasLinux: false,
            windowsCredentialId: 'windows-ansible-creds',
            linuxCredentialId: 'linux-ansible-creds',
            mixedEnvironment: false,
            serverDetails: serverInfo
        ]
        
        // Analyze servers
        serverInfo.each { hostname, info ->
            if (info.is_windows) {
                credentialInfo.hasWindows = true
                script.echo "🪟 Windows server detected: ${hostname}"
            } else {
                credentialInfo.hasLinux = true
                script.echo "🐧 Linux server detected: ${hostname}"
            }
        }
        
        // Detection by name if no system information available
        if (!credentialInfo.hasWindows && !credentialInfo.hasLinux) {
            if (targetServers.toLowerCase().contains('win') || 
                targetServers.toLowerCase().contains('windows')) {
                credentialInfo.hasWindows = true
                script.echo "🪟 Windows detected by name: ${targetServers}"
            } else {
                credentialInfo.hasLinux = true
                script.echo "🐧 Linux by default: ${targetServers}"
            }
        }
        
        // Mixed environment
        if (credentialInfo.hasWindows && credentialInfo.hasLinux) {
            credentialInfo.mixedEnvironment = true
            script.echo "🔄 Mixed environment detected"
        }
        
        return credentialInfo
    }
    
    /**
     * Builds extra-vars parameters for Ansible
     */
    def buildExtraVars(Map ansibleVars) {
        if (!ansibleVars) {
            return ""
        }
        
        def extraVars = []
        
        ansibleVars.each { key, value ->
            // Escape values with spaces or special characters
            def escapedValue = value.toString()
            if (escapedValue.contains(' ') || escapedValue.contains('"')) {
                escapedValue = "\"${escapedValue.replace('"', '\\"')}\""
            }
            extraVars.add("${key}=${escapedValue}")
        }
        
        return extraVars.join(' ')
    }
    
    /**
     * Validates Ansible variable names
     */
    def validateVariableNames(Map ansibleVars) {
        def invalidVars = []
        
        ansibleVars.each { key, value ->
            if (!key.matches('^[a-zA-Z_][a-zA-Z0-9_]*$')) {
                invalidVars.add(key)
            }
        }
        
        if (invalidVars) {
            script.error("❌ Invalid Ansible variables: ${invalidVars.join(', ')}")
        }
        
        script.echo "✅ All Ansible variables are valid"
    }
    
    /**
     * Generates a deployment report
     */
    def generateDeploymentReport(Map config, def startTime, def endTime, def status) {
        def duration = (endTime - startTime) / 1000  // in seconds
        
        def report = [
            playbook: config.playbook,
            targetServers: config.targetServers,
            ansibleVars: config.ansibleVars,
            tags: config.tags ?: 'all',
            checkMode: config.checkMode,
            status: status,
            duration: "${duration}s",
            timestamp: new Date().format('yyyy-MM-dd HH:mm:ss'),
            jenkins: [
                buildNumber: script.env.BUILD_NUMBER,
                buildUrl: script.env.BUILD_URL,
                jobName: script.env.JOB_NAME
            ]
        ]
        
        script.echo "📊 Deployment report generated"
        return report
    }
    
    /**
     * Saves the deployment report
     */
    def saveDeploymentReport(Map report) {
        try {
            def reportJson = groovy.json.JsonBuilder(report).toPrettyString()
            script.writeFile file: 'deployment-report.json', text: reportJson
            script.echo "💾 Report saved: deployment-report.json"
        } catch (Exception e) {
            script.echo "⚠️  Unable to save report: ${e.message}"
        }
    }
    
    /**
     * Formats Ansible output for better readability
     */
    def formatAnsibleOutput(String output) {
        def lines = output.split('\n')
        def formattedLines = []
        
        lines.each { line ->
            if (line.contains('TASK [')) {
                formattedLines.add("🔧 ${line}")
            } else if (line.contains('PLAY [')) {
                formattedLines.add("📋 ${line}")
            } else if (line.contains('changed:')) {
                formattedLines.add("✅ ${line}")
            } else if (line.contains('failed:')) {
                formattedLines.add("❌ ${line}")
            } else if (line.contains('ok:')) {
                formattedLines.add("✓ ${line}")
            } else {
                formattedLines.add(line)
            }
        }
        
        return formattedLines.join('\n')
    }
    
    /**
     * Checks Ansible prerequisites
     */
    def checkPrerequisites() {
        script.echo "🔍 Checking Ansible prerequisites"
        
        try {
            // Check Ansible version
            def version = script.sh(
                script: "ansible --version | head -n1",
                returnStdout: true
            ).trim()
            script.echo "✅ ${version}"
            
            // Check ansible-playbook availability
            script.sh "which ansible-playbook > /dev/null"
            script.echo "✅ ansible-playbook available"
            
            return true
        } catch (Exception e) {
            script.error("❌ Missing Ansible prerequisites: ${e.message}")
            return false
        }
    }
    
    /**
     * Performs pre-deployment checks
     */
    def preDeploymentChecks(String targetServers, String inventory, String playbook) {
        script.echo "🔍 Running pre-deployment checks"
        
        def checks = [:]
        
        // Check prerequisites
        checks.prerequisites = checkPrerequisites()
        
        // Check connectivity
        def connectivity = validateConnectivity(targetServers, inventory)
        checks.connectivity = connectivity.success
        
        // Check playbook existence
        try {
            script.sh "test -f ${playbook}"
            checks.playbookExists = true
            script.echo "✅ Playbook found: ${playbook}"
        } catch (Exception e) {
            checks.playbookExists = false
            script.echo "❌ Playbook not found: ${playbook}"
        }
        
        // Check inventory
        try {
            script.sh "test -f ${inventory}"
            checks.inventoryExists = true
            script.echo "✅ Inventory found: ${inventory}"
        } catch (Exception e) {
            checks.inventoryExists = false
            script.echo "❌ Inventory not found: ${inventory}"
        }
        
        // Overall result
        checks.allPassed = checks.prerequisites && checks.connectivity && 
                          checks.playbookExists && checks.inventoryExists
        
        if (checks.allPassed) {
            script.echo "✅ All pre-deployment checks passed"
        } else {
            script.echo "❌ Some pre-deployment checks failed"
        }
        
        return checks
    }
    
    /**
     * Executes Ansible playbook with enhanced error handling
     */
    def executePlaybook(Map config) {
        def startTime = System.currentTimeMillis()
        
        script.echo "🚀 Starting Ansible playbook execution"
        script.echo "📋 Playbook: ${config.playbook}"
        script.echo "🎯 Target: ${config.targetServers}"
        
        try {
            // Pre-deployment checks
            def checks = preDeploymentChecks(config.targetServers, config.inventory, config.playbook)
            if (!checks.allPassed) {
                throw new Exception("Pre-deployment checks failed")
            }
            
            // Build command
            def cmd = buildAnsibleCommand(config)
            script.echo "🔧 Command: ${cmd}"
            
            // Execute playbook
            def output = script.sh(
                script: cmd,
                returnStdout: true
            )
            
            def endTime = System.currentTimeMillis()
            def duration = (endTime - startTime) / 1000
            
            script.echo "✅ Playbook executed successfully in ${duration}s"
            
            // Format and save output
            def formattedOutput = formatAnsibleOutput(output)
            script.writeFile file: 'ansible-output.log', text: formattedOutput
            
            // Generate report
            def report = generateDeploymentReport(config, startTime, endTime, 'success')
            saveDeploymentReport(report)
            
            return [
                success: true,
                duration: duration,
                output: formattedOutput,
                report: report
            ]
            
        } catch (Exception e) {
            def endTime = System.currentTimeMillis()
            def duration = (endTime - startTime) / 1000
            
            script.echo "❌ Playbook execution failed after ${duration}s: ${e.message}"
            
            // Generate failure report
            def report = generateDeploymentReport(config, startTime, endTime, 'failure')
            report.error = e.message
            saveDeploymentReport(report)
            
            return [
                success: false,
                duration: duration,
                error: e.message,
                report: report
            ]
        }
    }
    
    /**
     * Builds the Ansible command with all parameters
     */
    private def buildAnsibleCommand(Map config) {
        def cmd = "ansible-playbook ${config.playbook} -i ${config.inventory}"
        
        if (config.targetServers && config.targetServers != 'all') {
            cmd += " -l ${config.targetServers}"
        }
        
        if (config.tags) {
            cmd += " --tags ${config.tags}"
        }
        
        if (config.skipTags) {
            cmd += " --skip-tags ${config.skipTags}"
        }
        
        if (config.checkMode) {
            cmd += " --check"
        }
        
        if (config.verbose) {
            cmd += " -${config.verbose}"
        }
        
        if (config.ansibleVars) {
            def extraVars = buildExtraVars(config.ansibleVars)
            if (extraVars) {
                cmd += " --extra-vars '${extraVars}'"
            }
        }
        
        return cmd
    }
}