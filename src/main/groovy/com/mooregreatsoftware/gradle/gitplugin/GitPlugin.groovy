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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GitPlugin implements Plugin<Project> {
    protected Logger logger = LoggerFactory.getLogger(GitPlugin)
    private GitState _gitState
    def String _userName
    @Delegate final ExecutionHelper executionHelper = ExecutionHelper.instance


    void apply(Project project) {
        logger.debug 'Setting up GitPlugin'

        String integrationBranch = determineIntegrationBranch()
        String privateRemoteBranch = "work/${userName}/${gitState.currentBranch}"

        if (gitState.trackedBranch) {
            project.task('update', type: GitRebaseTask, description: "Pull remote changes from ${gitState.trackedBranch} to current branch.") {
                rebaseAgainstServer = true
                sourceBranch = gitState.trackedBranch
            }
        }

        project.task('push-private', type: GitPushTask, description: "Push local changes to ${privateRemoteBranch}.") {
            remoteMachine = gitState.remoteName
            localBranch = gitState.currentBranch
            remoteBranch = privateRemoteBranch
            force = true
        }

        if (integrationBranch) {
            String workBranch = "work/${integrationBranch}/${gitState.currentBranch}"
            String reviewBranch = "review/${integrationBranch}/${gitState.currentBranch}"

            project.task('push-work', description: "Push local changes to ${workBranch}.", dependsOn: ['-push-work', 'track-work'])
            project.task('-push-work', type: GitPushTask, dependsOn: 'update') {
                remoteMachine = gitState.remoteName
                localBranch = gitState.currentBranch
                remoteBranch = workBranch
                force = false
            }

            project.task('push-review', type: GitPushTask, description: "Push local changes to ${reviewBranch}.", dependsOn: ['-push-review', 'track-review'])
            project.task('-push-review', type: GitPushTask, dependsOn: 'update') {
                remoteMachine = gitState.remoteName
                localBranch = gitState.currentBranch
                remoteBranch = reviewBranch
                force = false
            }

            project.task('push-integration', type: GitPushTask, description: "Push local changes to ${integrationBranch}.", dependsOn: ['-push-integration', 'track-integration'])
            project.task('-push-integration', type: GitPushTask, dependsOn: 'update') {
                remoteMachine = gitState.remoteName
                localBranch = gitState.currentBranch
                remoteBranch = integrationBranch
                force = false
            }

            project.task('track-work', type: GitChangeTrackedBrachTask, description: "Change local ${gitState.currentBranch} branch to track ${gitState.remoteName}/${workBranch}.") {
                branch = gitState.currentBranch
                trackedBranch = workBranch
                remoteMachine = gitState.remoteName
            }

            project.task('track-review', type: GitChangeTrackedBrachTask, description: "Change local ${gitState.currentBranch} branch to track ${gitState.remoteName}/${reviewBranch}.") {
                branch = gitState.currentBranch
                trackedBranch = reviewBranch
                remoteMachine = gitState.remoteName
            }

            project.task('track-integration', type: GitChangeTrackedBrachTask, description: "Change local ${gitState.currentBranch} branch to track ${gitState.remoteName}/${integrationBranch}.") {
                branch = gitState.currentBranch
                trackedBranch = integrationBranch
                remoteMachine = gitState.remoteName
            }
        }

        project.task('refresh-remote-branches', description: "Removes references to remote branches that no longer exist at \"${gitState.remoteName}.\"") << {
            executionHelper.runCmd "git remote prune ${gitState.remoteName}"
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


    String determineIntegrationBranch() {
        def tb = gitState.trackedBranch
        def cb = gitState.currentBranch
        def rn = gitState.remoteName

        if (!tb) {
            logger.warn("This branch does not track another branch.\nRun \"git config branch.${cb}.merge refs/heads/****\",\nwhere **** is the name of the integration branch.")
            return null
        }

        def m
        m = (tb =~ "^${rn}/([^/]+)\$")
        if (m) return m[0][1]
        m = (tb =~ "^${rn}/work/([^/]+)/${cb}\$")
        if (m) return m[0][1]
        m = (tb =~ "^${rn}/review/([^/]+)/${cb}\$")
        if (m) return m[0][1]

        logger.warn "Can not parse integration branch out of ${tb} because it does not follow the conventions."
        return null
    }

}
