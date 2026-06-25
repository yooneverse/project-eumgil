import jenkins.model.Jenkins
import com.cloudbees.plugins.credentials.Credentials
import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.domains.Domain
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey
import hudson.util.Secret
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl

def jenkins = Jenkins.get()
def store = SystemCredentialsProvider.getInstance().getStore()

def removeIfExists = { String id ->
    def existing = CredentialsProvider.lookupCredentials(
        Credentials.class,
        jenkins,
        null,
        null
    ).find { it.id == id }

    if (existing != null) {
        store.removeCredentials(Domain.global(), existing)
    }
}

def upsertStringCredential = { String id, String description, String value ->
    if (value == null || value.trim().isEmpty()) {
        println("[e102] skip credential ${id}: blank value")
        return
    }

    removeIfExists(id)
    def credential = new StringCredentialsImpl(
        CredentialsScope.GLOBAL,
        id,
        description,
        Secret.fromString(value.trim())
    )
    store.addCredentials(Domain.global(), credential)
    println("[e102] upserted string credential ${id}")
}

def readFirstTextFile = { List<String> paths ->
    for (String path : paths.findAll { it != null && !it.trim().isEmpty() }) {
        File file = new File(path.trim())
        if (!file.isFile()) {
            continue
        }

        String value = file.text.trim()
        if (!value.isEmpty()) {
            println("[e102] loaded credential value from ${file.path}")
            return value
        }
    }
    return null
}

def envOrFile = { String envName, List<String> paths, String fallback ->
    String value = System.getenv(envName)
    if (value != null && !value.trim().isEmpty()) {
        return value.trim()
    }

    String fileValue = readFirstTextFile(paths)
    if (fileValue != null && !fileValue.trim().isEmpty()) {
        return fileValue.trim()
    }

    return fallback
}

def upsertSshKeyCredential = { String id, String description, String username, String keyPath ->
    File key = new File(keyPath)
    if (!key.isFile()) {
        println("[e102] skip credential ${id}: missing key ${keyPath}")
        return
    }

    removeIfExists(id)
    def credential = new BasicSSHUserPrivateKey(
        CredentialsScope.GLOBAL,
        id,
        username,
        new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(key.text),
        '',
        description
    )
    store.addCredentials(Domain.global(), credential)
    println("[e102] upserted SSH key credential ${id}")
}

println('[e102] env file credentials are managed in Jenkins Credentials and are not synced from host files')

upsertStringCredential(
    'e102-s2-host',
    'E102 S2 production SSH host',
    envOrFile(
        'E102_S2_HOST',
        [
            System.getenv('E102_S2_HOST_FILE'),
            '/var/jenkins_home/prod-secrets/e102-s2-host',
            '/var/jenkins_home/prod-secrets/s2-host',
            '/var/jenkins_home/prod-secrets/E102_S2_HOST'
        ],
        '43.201.198.214'
    )
)

upsertSshKeyCredential(
    'e102-s2-ssh-key',
    'E102 S2 production SSH private key',
    envOrFile(
        'E102_S2_USER',
        [
            System.getenv('E102_S2_USER_FILE'),
            '/var/jenkins_home/prod-secrets/e102-s2-user',
            '/var/jenkins_home/prod-secrets/s2-user',
            '/var/jenkins_home/prod-secrets/E102_S2_USER'
        ],
        'ubuntu'
    ),
    '/var/jenkins_home/prod-secrets/busan-eumgil-S2.pem'
)

upsertStringCredential(
    'e102-mattermost-webhook-url',
    'E102 Mattermost incoming webhook URL',
    System.getenv('MATTERMOST_WEBHOOK_URL')
)

upsertStringCredential(
    'e102-log-analysis-webhook-url',
    'E102 shared log analysis Mattermost incoming webhook URL',
    System.getenv('LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL')
)

upsertStringCredential(
    'e102-dev-log-analysis-webhook-url',
    'E102 DEV log analysis Mattermost incoming webhook URL',
    System.getenv('DEV_LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL') ?: System.getenv('LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL')
)

upsertStringCredential(
    'e102-prod-log-analysis-webhook-url',
    'E102 PROD log analysis Mattermost incoming webhook URL',
    System.getenv('PROD_LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL') ?: System.getenv('LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL')
)

SystemCredentialsProvider.getInstance().save()
