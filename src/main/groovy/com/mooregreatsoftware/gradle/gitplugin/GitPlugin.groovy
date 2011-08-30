/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mooregreatsoftware.gradle.gitplugin

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Rule
import org.gradle.api.Task
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GitPlugin implements Plugin<Project> {
    protected Logger logger = LoggerFactory.getLogger(GitPlugin)
    protected static GitState _gitState
    def String _userName
    final ExecutionHelper executionHelper = ExecutionHelper.instance


    void apply(Project project) {
        logger.debug 'Setting up GitPlugin'

        GitConvention.apply(project, gitState)

        if (gitState.remoteName) {
            if (gitState.trackedBranch) {
                project.task('update', type: GitRebaseTask, description: "Pull remote changes from ${gitState.trackedBranch} to current branch.") {
                    rebaseAgainstServer = true
                    sourceBranch = gitState.trackedBranch
                }
            }

            project.task('refresh-remote-branches', type: GitRemotePruneTask, description: "Removes references to remote branches that no longer exist at \"${gitState.remoteName}.\"") {
                remoteMachine = gitState.remoteName
            }

            addRemovalTasks(project)

            addPushTasks(project)
            addTrackingTasks(project)
        }

        addStartRule(project)
        addReviewRule(project)
    }


    private def addRemovalTasks(Project project) {
        addRemovePrivate(project)
        addRemoveWork(project)
        addRemoveReview(project)
    }


    private Task addRemovePrivate(Project project) {
        String privateRemoteBranch = "work/${userName}/${gitState.currentBranch}"
        project.task('remove-private', type: GitRemoveBranchTask, description: "Remove remote ${privateRemoteBranch}") {
            remoteMachine = gitState.remoteName
            branch = privateRemoteBranch
            remote = true
        }
    }


    private Task addRemoveWork(Project project) {
        project.task('remove-work', type: GitRemoveBranchTask, description: "Remove remote ${project.workBranch}") {
            remoteMachine = gitState.remoteName
            branch = project.workBranch
            remote = true
        }
    }


    private Task addRemoveReview(Project project) {
        project.task('remove-review', type: GitRemoveBranchTask, description: "Remove remote ${project.reviewBranch}") {
            remoteMachine = gitState.remoteName
            branch = project.reviewBranch
            remote = true
        }
    }


    private def addPushTasks(Project project) {
        addPushPrivate(project)
        addPushWork(project)
        addPushReview(project)
        addPushIntegration(project)
    }


    private Task addPushIntegration(Project project) {
        project.task('push-integration', type: GitPushTask, description: "Push local changes to ${project.integrationBranch}.", dependsOn: 'update') {
            remoteMachine = gitState.remoteName
            localBranch = gitState.currentBranch
            remoteBranch = project.integrationBranch
            force = false
        }.doLast {
            executeTask(project, 'track-integration')
        }
    }


    private def addPushReview(Project project) {
        project.task('push-review', type: GitPushTask, description: "Push local changes to ${project.reviewBranch}.", dependsOn: 'update') {
            remoteMachine = gitState.remoteName
            localBranch = gitState.currentBranch
            remoteBranch = project.reviewBranch
            force = false
        }.doLast {
            executeTask(project, 'track-review')
        }
    }


    private def addPushWork(Project project) {
        project.task('push-work', type: GitPushTask, description: "Push local changes to ${project.workBranch}.", dependsOn: 'update') {
            remoteMachine = gitState.remoteName
            localBranch = gitState.currentBranch
            remoteBranch = project.workBranch
            force = false
        }.doLast {
            executeTask(project, 'track-work')
        }
    }


    private def addPushPrivate(Project project) {
        String privateRemoteBranch = "work/${userName}/${gitState.currentBranch}"
        project.task('push-private', type: GitPushTask, description: "Push local changes to ${privateRemoteBranch}.") {
            remoteMachine = gitState.remoteName
            localBranch = gitState.currentBranch
            remoteBranch = privateRemoteBranch
            force = true
        }
    }


    private void addTrackingTasks(Project project) {
        addTrackWork(project)
        addTrackReview(project)
        addTrackIntegration(project)
    }


    private Task addTrackIntegration(Project project) {
        return project.task('track-integration', type: GitChangeTrackedBranchTask, description: "Change local ${gitState.currentBranch} branch to track ${gitState.remoteName}/${project.integrationBranch}.") {
            branch = gitState.currentBranch
            trackedBranch = project.integrationBranch
            remoteMachine = gitState.remoteName
        }
    }


    private Task addTrackReview(Project project) {
        return project.task('track-review', type: GitChangeTrackedBranchTask, description: "Change local ${gitState.currentBranch} branch to track ${gitState.remoteName}/${project.reviewBranch}.") {
            branch = gitState.currentBranch
            trackedBranch = project.reviewBranch
            remoteMachine = gitState.remoteName
        }
    }


    private Task addTrackWork(Project project) {
        return project.task('track-work', type: GitChangeTrackedBranchTask, description: "Change local ${gitState.currentBranch} branch to track ${gitState.remoteName}/${project.workBranch}.") {
            branch = gitState.currentBranch
            trackedBranch = project.workBranch
            remoteMachine = gitState.remoteName
        }
    }


    private Rule addStartRule(Project project) {
        project.tasks.addRule 'Pattern: start<ID> to create a branch based on the ticket ID', {String taskName ->
            if (taskName.startsWith("start")) {
                String sourceBranch = gitState.remoteName + '/review/'
                if (project.integrationBranch) {
                    sourceBranch = (gitState.remoteName) ? gitState.remoteName + '/' + project.integrationBranch : project.integrationBranch
                }
                else {
                    throw new GradleException('Do not know what to base the new branch off of.')
                }
                project.task(taskName, type: GitCheckoutTask) {
                    trackedBranch = sourceBranch
                    branch = branchPrefix + taskName[5..-1]
                } as GitCheckoutTask
            }
        }
    }


    private Rule addReviewRule(Project project) {
        project.tasks.addRule 'Pattern: review<ID> to create a local branch to track remote ${project.reviewBranch}', {String taskName ->
            if (taskName.startsWith("review")) {
                project.task(taskName, type: GitCheckoutTask) {
                    def reviewBranchName = branchPrefix + taskName[6..-1]
                    trackedBranch = "${gitState.remoteName}/${project.baseReviewBranchName}/${project.integrationBranch}/${reviewBranchName}"
                    branch = reviewBranchName
                } as GitCheckoutTask
            }
        }
    }


    static synchronized GitState getGitState() {
        if (!_gitState) {
            _gitState = new GitState()
        }
        _gitState
    }


    static void setGitState(GitState state) {
        _gitState = state
    }


    String getUserName() {
        if (!_userName) {
            _userName = System.getenv('USER')
        }
        _userName
    }


    void setUserName(String un) {
        _userName = un
    }


    List executeTask(Project project, String taskName) {
        Task task = project.tasks.getByName(taskName)
        executeTask(task)
    }


    static List executeTask(Task task) {
        Set dependencies = task.taskDependencies.getDependencies(task)
        dependencies.each {Task depTask -> executeTask(depTask)}
        List actions = task.actions
        actions.each {Action action -> action.execute(task) }
    }
}
