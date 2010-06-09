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

import org.gradle.api.Project
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GitConvention {
    protected static final Logger logger = LoggerFactory.getLogger(GitConvention)
    private static final String DEFAULT_INTEGRATION_BRANCH = 'master'
    private static final String DEFAULT_BRANCH_PREFIX = 'ISSUE-'
    private static final String DEFAULT_BASE_WORK_BRANCH_NAME = 'work'
    private static final String DEFAULT_BASE_REVIEW_BRANCH_NAME = 'review'

    private GitState gitState
    private Project project
    private String _integrationBranch


    static apply(Project project, GitState gitState) {
        GitConvention conv = new GitConvention()
        conv.project = project
        conv.gitState = gitState

        project.convention.plugins.put('git', conv)

        if (!project.hasProperty('branchPrefix')) {
            project.branchPrefix = DEFAULT_BRANCH_PREFIX
        }

        if (!project.hasProperty('baseWorkBranchName')) {
            project.baseWorkBranchName = 'work'
        }
        if (!project.hasProperty('baseReviewBranchName')) {
            project.baseReviewBranchName = 'review'
        }

        if (!project.hasProperty('workBranch')) {
            project.workBranch = "${project.baseWorkBranchName}/${project.integrationBranch}/${project.gitState.currentBranch}"
        }
        if (!project.hasProperty('reviewBranch')) {
            project.reviewBranch = "${project.baseReviewBranchName}/${project.integrationBranch}/${project.gitState.currentBranch}"
        }

        project.tasks.withType(GitCheckoutTask).allTasks {GitCheckoutTask task ->
            task.conventionMapping.map 'branchPrefix', { project.branchPrefix }
        }
    }


    String getIntegrationBranch() {
        if (!_integrationBranch) {
            _integrationBranch = determineIntegrationBranch()
        }
        _integrationBranch
    }


    void setIntegrationBranch(String integrationBranch) {
        _integrationBranch = integrationBranch
    }


    private String determineIntegrationBranch() {
        String tb = gitState.trackedBranch

        if (!tb) {
            return DEFAULT_INTEGRATION_BRANCH
        }

        String rn = gitState.remoteName

        def m
        m = (tb =~ "^${rn}/([^/]+)\$")
        if (m) return m[0][1]

        def i

        i = determineIntBranchFromSharedWorkingBranch(gitState)
        if (!i) {
            i = determineIntBranchFromReviewBranch(gitState)
            if (!i) {
                logger.warn "Can not parse integration branch out of ${tb} because it does not follow the conventions."
                i = DEFAULT_INTEGRATION_BRANCH
            }
        }
        i
    }


    private String determineIntBranchFromSharedWorkingBranch(GitState gs) {
        String tb = gs.trackedBranch
        String rn = gs.remoteName
        String cb = gs.currentBranch
        String pattern = "^${rn}/${project.baseWorkBranchName}/([^/]+)/${cb}\$"

        def m = (tb =~ pattern)
        (m) ? m[0][1] : null
    }


    private String determineIntBranchFromReviewBranch(GitState gs) {
        String tb = gs.trackedBranch
        String rn = gs.remoteName
        String cb = gs.currentBranch
        String pattern = "^${rn}/${project.baseReviewBranchName}/([^/]+)/${cb}\$"

        def m = (tb =~ pattern)
        (m) ? m[0][1] : null
    }

}
