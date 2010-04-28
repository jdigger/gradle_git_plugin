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
import org.junit.After
import org.junit.Before
import org.junit.Test
import static com.mooregreatsoftware.gradle.gitplugin.GradleTestHelper.createTask
import static org.hamcrest.Matchers.equalTo
import static org.junit.Assert.assertThat
import static org.junit.Assert.fail

class GitTaskTests {
    final ExecutionHelper helper = ExecutionHelper.instance


    @Before
    void initExecutionHelper() {
        helper.debugMode = true
    }


    @After
    void cleanupExecutionHelper() {
        helper.reset()
    }


    @Test
    void gitPush_force() {
        GitPushTask task = createTask(GitPushTask) as GitPushTask
        task.force = true
        task.localBranch = 'master'
        task.remoteBranch = 'remote_master'
        assertThat task.commandToExecute(), equalTo('git push -f origin master:remote_master')
    }


    @Test
    void gitPush_no_force() {
        GitPushTask task = createTask(GitPushTask) as GitPushTask
        task.force = false
        task.localBranch = 'master'
        task.remoteBranch = 'remote_master'
        assertThat task.commandToExecute(), equalTo('git push origin master:remote_master')
    }


    @Test
    void gitPush_missing_localBranch() {
        GitPushTask task = createTask(GitPushTask) as GitPushTask
        task.remoteBranch = 'remote_master'
        try {
            task.commandToExecute()
            fail "Should have thrown assertion error"
        }
        catch (GradleException exp) {
            assertThat exp.getMessage(), equalTo('Need a localBranch')
        }
    }


    @Test
    void gitRemoveBranch_remote() {
        GitRemoveBranchTask task = createTask(GitRemoveBranchTask) as GitRemoveBranchTask
        task.remote = true
        task.branch = 'master'
        assertThat task.commandToExecute(), equalTo('git push origin :master')
    }


    @Test
    void gitRemoveBranch_force() {
        GitRemoveBranchTask task = createTask(GitRemoveBranchTask) as GitRemoveBranchTask
        task.force = true
        task.branch = 'master'
        assertThat task.commandToExecute(), equalTo('git branch -D master')
    }


    @Test
    void gitRemoveBranch_no_force() {
        GitRemoveBranchTask task = createTask(GitRemoveBranchTask) as GitRemoveBranchTask
        task.force = false
        task.branch = 'master'
        assertThat task.commandToExecute(), equalTo('git branch -d master')
    }


    @Test
    void gitRemoveBranch_missing_branch() {
        GitRemoveBranchTask task = createTask(GitRemoveBranchTask) as GitRemoveBranchTask
        try {
            task.commandToExecute()
            fail "Should have thrown assertion error"
        }
        catch (GradleException exp) {
            assertThat exp.getMessage(), equalTo('Need a branch')
        }
    }


    @Test
    void gitPush_missing_remoteBranch() {
        GitPushTask task = createTask(GitPushTask) as GitPushTask
        task.localBranch = 'master'
        try {
            task.commandToExecute()
            fail "Should have thrown assertion error"
        }
        catch (GradleException exp) {
            assertThat exp.getMessage(), equalTo('Need a remoteBranch')
        }
    }


    @Test
    void gitRebase_simple() {
        helper.putCmdOutput 'git status', ' '

        GitRebaseTask task = createTask(GitRebaseTask) as GitRebaseTask
        task.sourceBranch = 'master'
        task.execute()
        assertThat helper.cmds.size(), equalTo(2)
        assertThat helper.cmds[0], equalTo('git status')
        assertThat helper.cmds[1], equalTo('git rebase master')
    }


    @Test
    void gitRebase_simple_server() {
        helper.putCmdOutput 'git status', ' '

        GitRebaseTask task = createTask(GitRebaseTask) as GitRebaseTask
        task.sourceBranch = "master"
        task.rebaseAgainstServer = true
        task.execute()
        assertThat helper.cmds.size(), equalTo(3)
        assertThat helper.cmds[0], equalTo('git status')
        assertThat helper.cmds[1], equalTo('git fetch')
        assertThat helper.cmds[2], equalTo('git rebase origin/master')
    }


    @Test
    void gitCheckout_no_tracking() {
        GitCheckoutTask task = createTask(GitCheckoutTask) as GitCheckoutTask
        task.branch = 'new_branch'
        task.trackedBranch = ''
        task.execute()
        assertThat helper.cmds.size(), equalTo(1)
        assertThat helper.cmds[0], equalTo('git checkout new_branch')
    }


    @Test
    void gitCheckout_with_tracking() {
        GitCheckoutTask task = createTask(GitCheckoutTask) as GitCheckoutTask
        task.branch = 'new_branch'
        task.trackedBranch = 'integration_branch'
        task.execute()
        assertThat helper.cmds.size(), equalTo(1)
        assertThat helper.cmds[0], equalTo('git checkout -b new_branch integration_branch')
    }

}
