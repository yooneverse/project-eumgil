import jenkins.model.Jenkins
import hudson.triggers.SCMTrigger
import hudson.plugins.git.BranchSpec
import hudson.plugins.git.GitSCM
import hudson.plugins.git.UserRemoteConfig
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition
import org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty

String jobName = 'e102-dev-deploy'
File marker = new File('/var/jenkins_home/.e102-dev-deploy-job-created')
String repoUrl = System.getenv('E102_REPO_URL') ?: 'https://git.example.com/group/project.git'
String credentialsId = 'gitlab-pat'
String branchSpec = '*/develop'
String scriptPath = 'INF/jenkins/pipelines/e102-dev-deploy.Jenkinsfile'

def scm = new GitSCM(
  [new UserRemoteConfig(repoUrl, null, null, credentialsId)],
  [new BranchSpec(branchSpec)],
  false,
  [],
  null,
  null,
  []
)

def flowDefinition = new CpsScmFlowDefinition(scm, scriptPath)
flowDefinition.setLightweight(true)

Jenkins j = Jenkins.get()
def job = j.getItem(jobName)
if (job == null) {
  job = j.createProject(WorkflowJob.class, jobName)
}
job.setDescription('Deploy develop branch to S1 dev Docker Compose stack. GraphHopper cache is built from PostgreSQL LineString when missing.')
job.setDefinition(flowDefinition)
if (job.getProperty(PipelineTriggersJobProperty.class) == null) {
  job.addProperty(new PipelineTriggersJobProperty([new SCMTrigger('H/30 * * * *')]))
}
job.save()

if (!marker.exists()) {
  job.scheduleBuild2(0)
  marker.text = new Date().toString()
  println("${jobName} configured and initial build scheduled")
} else {
  println("${jobName} configured")
}
