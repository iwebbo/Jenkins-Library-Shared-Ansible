# Jenkins Ansible Shared Library - Documentation

## 📋 Table of Contents
1. [Installation and Configuration](#installation-and-configuration)
2. [Ansible Project Structure](#ansible-project-structure)
3. [Pipeline Examples](#pipeline-examples)
4. [Function Reference](#function-reference)
5. [Best Practices](#best-practices)

---

## 🚀 Installation and Configuration

### 1. Jenkins Configuration

#### Method 1: Global Configuration (Recommended)
1. Go to **Jenkins** → **Manage Jenkins** → **Configure System**
2. Section **Global Pipeline Libraries**
3. Add a new library:
   - **Name**: `ansible-shared-lib`
   - **Default version**: `main` (or your branch)
   - **Retrieval method**: Modern SCM
   - **Source Code Management**: Git
   - **Project Repository**: `https://your-git-server/ansible-shared-lib.git`
   - **Credentials**: Select your Git credentials

#### Method 2: Folder Level Configuration
To limit access to specific projects only.

### 2. Library Git Structure

```
ansible-shared-lib/
├── vars/
│   ├── ansibleDeploy.groovy
│   ├── ansibleValidate.groovy
│   ├── ansibleNotify.groovy
│   └── ansibleQuickDeploy.groovy
├── resources/
│   └── templates/
│       └── notification.html
├── src/
│   └── com/
│       └── company/
│           └── ansible/
│               └── Utils.groovy
└── README.md
```

---

## 📁 Ansible Project Structure

### Recommended Structure

```
ansible-project/
├── ansible.cfg
├── inventory/
│   ├── hosts.ini
│   ├── production/
│   │   └── hosts.ini
│   ├── staging/
│   │   └── hosts.ini
│   ├── group_vars/
│   │   ├── all.yml
│   │   ├── webservers.yml
│   │   └── databases.yml
│   └── host_vars/
│       ├── web01.yml
│       └── db01.yml
├── playbook/
│   ├── site.yml
│   ├── deploy-app.yml
│   ├── database.yml
│   └── maintenance.yml
├── roles/
│   ├── common/
│   ├── nginx/
│   ├── postgresql/
│   └── application/
├── collections/
│   └── requirements.yml
├── library/
│   └── custom_modules/
├── templates/
│   └── global/
└── files/
    └── certificates/
```

### ansible.cfg File

```ini
[defaults]
# Security
host_key_checking = False
ansible_managed = Ansible managed: {file} modified on %Y-%m-%d %H:%M:%S

# Paths
inventory = inventory/hosts.ini
roles_path = ./roles
playbook_dir = playbook/
library = library/
collections_paths = collections/
filter_plugins = filter_plugins/
callback_plugins = callback_plugins/

# Performance
gathering = smart
fact_caching = jsonfile
fact_caching_connection = /tmp/ansible_cache
fact_caching_timeout = 3600
pipelining = True

# Display
stdout_callback = yaml
callback_whitelist = profile_tasks

# Logs
log_path = ./ansible.log

# Privileges
[privilege_escalation]
become = True
become_method = sudo
become_user = root
become_ask_pass = False
```

### Example inventory/hosts.ini

```ini
[webservers]
web01 ansible_host=192.168.1.10
web02 ansible_host=192.168.1.11

[databases]
db01 ansible_host=192.168.1.20 ansible_user=dbadmin

[windows]
win01 ansible_host=192.168.1.30 ansible_connection=winrm

[production:children]
webservers
databases

[staging]
staging-web01 ansible_host=192.168.2.10
staging-db01 ansible_host=192.168.2.20
```

---

## 📝 Pipeline Examples

### 1. Simple One-liner Deployment

```groovy
@Library('ansible-shared-lib@main') _
pipeline {
    agent any
    stages {
        stage('Deploy') {
            steps {
                ansibleDeploy([
                    playbook: 'site.yml',
                    targetServers: 'webservers'
                ])
            }
        }
    }
}
```

### 2. Basic Pipeline with Parameters

```groovy
@Library('ansible-shared-lib@main') _
pipeline {
    agent any
    stages {
        stage('Deploy Application') {
            steps {
                ansibleDeploy([
                    playbook: 'deploy-app.yml',
                    targetServers: 'production',
                    ansibleVars: [app_version: '1.2.3', environment: 'prod']
                ])
            }
        }
    }
}
```

### 3. Multi-Environment Pipeline

```groovy
@Library('ansible-shared-lib@main') _
pipeline {
    agent any
    parameters {
        choice(name: 'ENVIRONMENT', choices: ['dev', 'staging', 'production'])
    }
    stages {
        stage('Deploy') {
            steps {
                ansibleDeploy([
                    playbook: 'site.yml',
                    targetServers: params.ENVIRONMENT,
                    inventory: "inventory/${params.ENVIRONMENT}/hosts.ini",
                    ansibleVars: [environment: params.ENVIRONMENT]
                ])
            }
        }
    }
}
```

### 4. Environment-based Inventory Pipeline

```groovy
@Library('ansible-shared-lib@main') _
pipeline {
    agent any
    environment {
        INVENTORY = "${env.BRANCH_NAME == 'main' ? 'production' : 'staging'}"
    }
    stages {
        stage('Validate') {
            steps {
                ansibleValidate([
                    playbook: 'deploy-app.yml',
                    inventory: "inventory/${env.INVENTORY}/hosts.ini",
                    targetServers: 'all'
                ])
            }
        }
        stage('Deploy') {
            steps {
                ansibleDeploy([
                    playbook: 'deploy-app.yml',
                    inventory: "inventory/${env.INVENTORY}/hosts.ini",
                    targetServers: 'webservers',
                    ansibleVars: [
                        environment: env.INVENTORY,
                        branch: env.BRANCH_NAME
                    ]
                ])
            }
        }
    }
}
```

### 5. Quick Ad-hoc Commands

```groovy
@Library('ansible-shared-lib@main') _
pipeline {
    agent any
    stages {
        stage('Health Check') {
            steps {
                ansibleQuickDeploy([
                    adhocCommand: 'systemctl status nginx',
                    targetServers: 'webservers'
                ])
            }
        }
    }
}
```

### 6. Full-Featured Pipeline

```groovy
@Library('ansible-shared-lib@main') _
pipeline {
    agent any
    
    parameters {
        string(name: 'TARGET_SERVERS', defaultValue: 'webservers', description: 'Target servers or groups')
        choice(name: 'PLAYBOOK', choices: ['deploy-app.yml', 'maintenance.yml', 'rollback.yml'])
        text(name: 'ANSIBLE_VARS', defaultValue: 'app_version=1.0.0\nenvironment=production')
        booleanParam(name: 'CHECK_MODE', defaultValue: false, description: 'Run in check mode')
        booleanParam(name: 'VERBOSE', defaultValue: false, description: 'Enable verbose output')
    }
    
    stages {
        stage('Pre-check') {
            steps {
                ansibleValidate([
                    playbook: params.PLAYBOOK,
                    targetServers: params.TARGET_SERVERS
                ])
            }
        }
        
        stage('Deploy') {
            steps {
                ansibleDeploy([
                    playbook: params.PLAYBOOK,
                    targetServers: params.TARGET_SERVERS,
                    ansibleVars: params.ANSIBLE_VARS,
                    checkMode: params.CHECK_MODE,
                    verbose: params.VERBOSE,
                    tags: 'application,configuration'
                ])
            }
        }
        
        stage('Verify') {
            when {
                expression { params.CHECK_MODE == false }
            }
            steps {
                ansibleQuickDeploy([
                    adhocCommand: 'curl -f http://localhost/health || exit 1',
                    targetServers: params.TARGET_SERVERS,
                    module: 'shell'
                ])
            }
        }
    }
    
    post {
        success {
            echo "✅ Deployment successful!"
        }
        failure {
            echo "❌ Deployment failed!"
        }
        always {
            ansibleNotify([
                status: currentBuild.result ?: 'SUCCESS',
                playbook: params.PLAYBOOK,
                targetServers: params.TARGET_SERVERS
            ])
        }
    }
}
```

### 7. Blue-Green Deployment

```groovy
@Library('ansible-shared-lib@main') _
pipeline {
    agent any
    stages {
        stage('Deploy to Green') {
            steps {
                ansibleDeploy([
                    playbook: 'deploy-app.yml',
                    targetServers: 'green',
                    ansibleVars: [version: params.VERSION, color: 'green']
                ])
            }
        }
        stage('Switch Traffic') {
            input { message 'Switch traffic to green?' }
            steps {
                ansibleDeploy([
                    playbook: 'switch-lb.yml',
                    targetServers: 'loadbalancers',
                    ansibleVars: [active: 'green', inactive: 'blue']
                ])
            }
        }
    }
}
```

### 8. Parallel Deployment

```groovy
@Library('ansible-shared-lib@main') _
pipeline {
    agent any
    stages {
        stage('Deploy All') {
            parallel {
                stage('Frontend') {
                    steps {
                        ansibleDeploy([
                            playbook: 'frontend.yml',
                            targetServers: 'webservers'
                        ])
                    }
                }
                stage('Backend') {
                    steps {
                        ansibleDeploy([
                            playbook: 'backend.yml',
                            targetServers: 'appservers'
                        ])
                    }
                }
                stage('Database') {
                    steps {
                        ansibleDeploy([
                            playbook: 'database.yml',
                            targetServers: 'databases'
                        ])
                    }
                }
            }
        }
    }
}
```

### 9. Deployment with Rollback

```groovy
@Library('ansible-shared-lib@main') _
pipeline {
    agent any
    environment {
        BACKUP_ID = "${BUILD_NUMBER}-${currentBuild.startTimeInMillis}"
    }
    stages {
        stage('Backup') {
            steps {
                ansibleDeploy([
                    playbook: 'backup.yml',
                    targetServers: 'production',
                    ansibleVars: [backup_id: env.BACKUP_ID]
                ])
            }
        }
        stage('Deploy') {
            steps {
                script {
                    try {
                        ansibleDeploy([
                            playbook: 'deploy-app.yml',
                            targetServers: 'production',
                            ansibleVars: [version: params.VERSION]
                        ])
                    } catch (Exception e) {
                        currentBuild.result = 'FAILURE'
                        error("Deployment failed: ${e.message}")
                    }
                }
            }
        }
    }
    post {
        failure {
            ansibleDeploy([
                playbook: 'rollback.yml',
                targetServers: 'production',
                ansibleVars: [backup_id: env.BACKUP_ID]
            ])
        }
    }
}
```

### 10. Scheduled Maintenance

```groovy
@Library('ansible-shared-lib@main') _
pipeline {
    agent any
    triggers {
        cron('0 2 * * 6') // Every Saturday at 2 AM
    }
    stages {
        stage('Maintenance') {
            steps {
                ansibleDeploy([
                    playbook: 'maintenance.yml',
                    targetServers: 'all',
                    ansibleVars: [
                        maintenance_type: 'weekly',
                        notify_users: true
                    ]
                ])
            }
        }
    }
}
```

---

## 📚 Function Reference

### ansibleDeploy

**Description**: Main function to execute Ansible playbooks

**Parameters**:
- `playbook` (required): Playbook name to execute
- `targetServers` (required): Target servers or groups
- `inventory`: Path to inventory (default: from ansible.cfg)
- `ansibleVars`: Ansible variables (Map or String)
- `tags`: Tags to execute
- `checkMode`: Simulation mode (default: false)
- `verbose`: Verbose mode (default: false)
- `timeout`: Timeout in seconds (default: 3600)
- `become`: Privilege escalation (default: true)
- `becomeUser`: Target user (default: root)
- `notification`: Enable notifications (default: true)

**Example**:
```groovy
ansibleDeploy([
    playbook: 'deploy-app.yml',
    targetServers: 'webservers',
    ansibleVars: [version: '1.2.3'],
    tags: 'application'
])
```

### ansibleValidate

**Description**: Validates playbook syntax and host accessibility

**Parameters**: Same as ansibleDeploy

**Example**:
```groovy
ansibleValidate([
    playbook: 'site.yml',
    targetServers: 'all'
])
```

### ansibleQuickDeploy

**Description**: Executes ad-hoc commands quickly

**Parameters**:
- `adhocCommand`: Command to execute
- `targetServers`: Target servers
- `module`: Ansible module (default: shell)

**Example**:
```groovy
ansibleQuickDeploy([
    adhocCommand: 'df -h',
    targetServers: 'all'
])
```

### ansibleNotify

**Description**: Sends notifications (email, Slack, etc.)

**Parameters**:
- `status`: Build status
- `playbook`: Executed playbook
- `targetServers`: Targeted servers
- `error`: Error message (if failed)

---

## 🎯 Best Practices

### 1. Variable Organization

**inventory/group_vars/all.yml**
```yaml
# Global variables
ntp_servers:
  - 0.pool.ntp.org
  - 1.pool.ntp.org

timezone: UTC
```

**inventory/group_vars/webservers.yml**
```yaml
# Web server specific variables
nginx_worker_processes: auto
nginx_worker_connections: 1024
php_memory_limit: 256M
```

### 2. Using Tags

```yaml
# playbook/deploy-app.yml
---
- name: Application deployment
  hosts: "{{ HOST | default('webservers') }}"
  tasks:
    - name: Install dependencies
      tags: [dependencies, install]
      # ...
    
    - name: Configure application
      tags: [configuration, config]
      # ...
    
    - name: Start services
      tags: [services, start]
      # ...
```

### 3. Environment Management

Multi-environment structure:
```
inventory/
├── dev/
│   ├── hosts.ini
│   └── group_vars/
├── staging/
│   ├── hosts.ini
│   └── group_vars/
└── production/
    ├── hosts.ini
    └── group_vars/
```

### 4. Security

- Use Ansible Vault for secrets
- Never commit passwords in plain text
- Use Jenkins credentials
- Limit sudo permissions

### 5. Performance

- Use `gathering: smart` for fact caching
- Enable `pipelining` for SSH
- Use `serial` for rolling deployments
- Optimize fork count based on your infrastructure

### 6. Monitoring and Logging

- Enable callbacks for profiling
- Keep Ansible logs
- Implement health checks
- Use notifications for alerts

---

## 🐛 Troubleshooting

### Common Issues

1. **Playbook not found**
   - Check `playbook_dir` in ansible.cfg
   - Use full path if necessary

2. **SSH connection issues**
   - Verify Jenkins credentials
   - Test connection manually

3. **Undefined variables**
   - Check variable precedence order
   - Use `default()` in playbooks

4. **Timeouts**
   - Increase `timeout` parameter
   - Optimize playbooks

---

This documentation should help you effectively use your Jenkins Ansible shared library. Feel free to adapt it to your specific needs!