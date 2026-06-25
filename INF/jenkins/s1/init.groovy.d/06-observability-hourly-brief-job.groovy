import jenkins.model.Jenkins
import hudson.triggers.TimerTrigger
import hudson.plugins.git.BranchSpec
import hudson.plugins.git.GitSCM
import hudson.plugins.git.UserRemoteConfig
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition
import org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty

String jobName = 'e102-observability-hourly-brief'
String repoUrl = System.getenv('E102_REPO_URL') ?: 'https://git.example.com/group/project.git'
String credentialsId = 'gitlab-pat'
String branchSpec = '*/master'
String scriptPath = 'INF/jenkins/pipelines/e102-observability-hourly-brief.Jenkinsfile'

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
job.setDescription('매시 dev/prod warning-error 로그와 health 상태를 요약해 Mattermost로 보고합니다.')
job.setDefinition(flowDefinition)
job.removeProperty(PipelineTriggersJobProperty.class)
job.addProperty(new PipelineTriggersJobProperty([new TimerTrigger('H * * * *')]))
job.save()
println("${jobName} configured")
