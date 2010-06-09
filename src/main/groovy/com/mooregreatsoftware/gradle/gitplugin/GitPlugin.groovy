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

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Rule
import org.gradle.api.Task
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GitPlugin implements Plugin<Project> {
    protected Logger logger = LoggerFactory.getLogger(GitPlugin)
    private GitState _gitState
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

            addPushTasks(project)
            addTrackingTasks(project)

            project.task('refresh-remote-branches', description: "Removes references to remote branches that no longer exist at \"${gitState.remoteName}.\"") << {
                executionHelper.runCmd "git remote prune ${gitState.remoteName}"
            }
        }

        addStartRule(project)
    }


    private def addPushTasks(Project project) {
        addPushPrivate(project)
        addPushWork(project)
        addPushReview(project)
        addPushIntegration(project)
    }


    private Task addPushIntegration(Project project) {
        project.task('push-integration', type: GitPushTask, description: "Push local changes to ${project.integrationBranch}.", dependsOn: ['-push-integration', 'track-integration'])
        project.task('-push-integration', type: GitPushTask, dependsOn: 'update') {
            remoteMachine = gitState.remoteName
            localBranch = gitState.currentBranch
            remoteBranch = project.integrationBranch
            force = false
        }
    }


    private def addPushReview(Project project) {
        project.task('push-review', type: GitPushTask, description: "Push local changes to ${project.reviewBranch}.", dependsOn: ['-push-review', 'track-review'])
        project.task('-push-review', type: GitPushTask, dependsOn: 'update') {
            remoteMachine = gitState.remoteName
            localBranch = gitState.currentBranch
            remoteBranch = project.reviewBranch
            force = false
        }
    }


    private def addPushWork(Project project) {
        project.task('push-work', description: "Push local changes to ${project.workBranch}.", dependsOn: ['-push-work', 'track-work'])
        project.task('-push-work', type: GitPushTask, dependsOn: 'update') {
            remoteMachine = gitState.remoteName
            localBranch = gitState.currentBranch
            remoteBranch = project.workBranch
            force = false
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
                String sourceBranch = ''
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


    GitState getGitState() {
        if (!_gitState) {
            _gitState = new GitState()
        }
        _gitState
    }


    void setGitState(GitState state) {
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

}
