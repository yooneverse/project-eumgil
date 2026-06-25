import hudson.security.FullControlOnceLoggedInAuthorizationStrategy
import hudson.security.HudsonPrivateSecurityRealm
import jenkins.model.Jenkins

if ((System.getenv('JENKINS_FORCE_LOCAL_ADMIN') ?: 'false') != 'true') {
  println('Skipping local admin bootstrap. Set JENKINS_FORCE_LOCAL_ADMIN=true to force recovery.')
  return
}

Jenkins instance = Jenkins.get()
String adminUser = System.getenv('JENKINS_ADMIN_ID') ?: 'e102admin'
String adminPassword = System.getenv('JENKINS_ADMIN_PASSWORD')

if (adminPassword == null || adminPassword.trim().isEmpty()) {
  throw new IllegalStateException('JENKINS_ADMIN_PASSWORD is required')
}

def realm = new HudsonPrivateSecurityRealm(false)
realm.createAccount(adminUser, adminPassword)
instance.setSecurityRealm(realm)
instance.setAuthorizationStrategy(new FullControlOnceLoggedInAuthorizationStrategy())
instance.save()
println('Forced local admin security restored')
