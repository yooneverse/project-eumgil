import jenkins.model.Jenkins
import hudson.triggers.SCMTrigger
import hudson.plugins.git.BranchSpec
import hudson.plugins.git.GitSCM
import hudson.plugins.git.UserRemoteConfig
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition
import org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty

String jobName = 'e102-prod-deploy'
String repoUrl = System.getenv('E102_REPO_URL') ?: 'https://git.example.com/group/project.git'
String credentialsId = 'gitlab-pat'
String branchSpec = '*/master'
String scriptPath = 'INF/jenkins/pipelines/e102-prod-deploy.Jenkinsfile'

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
job.setDescription('master 브랜치를 30분마다 Poll SCM으로 확인해 S2 prod 서버에 backend, AI, 선택적 GraphHopper graph-cache/runtime을 배포합니다.')
job.setDefinition(flowDefinition)
if (job.getProperty(PipelineTriggersJobProperty.class) == null) {
  job.addProperty(new PipelineTriggersJobProperty([new SCMTrigger('H/30 * * * *')]))
}
job.save()
println("${jobName} configured")
