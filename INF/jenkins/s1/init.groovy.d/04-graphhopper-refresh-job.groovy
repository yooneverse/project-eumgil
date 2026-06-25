import jenkins.model.Jenkins
import hudson.plugins.git.BranchSpec
import hudson.plugins.git.GitSCM
import hudson.plugins.git.UserRemoteConfig
import hudson.triggers.TimerTrigger
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition

String jobName = 'e102-graphhopper-refresh'
String repoUrl = System.getenv('E102_REPO_URL') ?: 'https://git.example.com/group/project.git'
String credentialsId = 'gitlab-pat'
String branchSpec = '*/master'
String scriptPath = 'INF/jenkins/pipelines/e102-graphhopper-refresh.Jenkinsfile'

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
job.setDescription('3시간마다 S2 prod GraphHopper inactive blue/green slot을 갱신하고 smoke 통과 후 Redis active slot을 전환합니다.')
job.setDefinition(flowDefinition)
job.removeProperty(PipelineTriggersJobProperty.class)
job.addProperty(new PipelineTriggersJobProperty([new TimerTrigger('H H/3 * * *')]))
job.save()
println("${jobName} configured")
