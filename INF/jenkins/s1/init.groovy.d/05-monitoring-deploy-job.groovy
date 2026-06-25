import jenkins.model.Jenkins
import hudson.plugins.git.BranchSpec
import hudson.plugins.git.GitSCM
import hudson.plugins.git.UserRemoteConfig
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition

String jobName = 'e102-monitoring-deploy'
String repoUrl = System.getenv('E102_REPO_URL') ?: 'https://git.example.com/group/project.git'
String credentialsId = 'gitlab-pat'
String branchSpec = '*/master'
String scriptPath = 'INF/jenkins/pipelines/e102-monitoring-deploy.Jenkinsfile'

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
job.setDescription('master 브랜치의 S1 monitoring/Grafana/Prometheus/nginx 변경을 S1 운영 경로에 동기화하고 monitoring stack을 재적용합니다.')
job.setDefinition(flowDefinition)
job.save()
println("${jobName} configured")
