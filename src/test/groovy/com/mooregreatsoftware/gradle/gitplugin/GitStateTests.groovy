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

import org.junit.After
import org.junit.Before
import org.junit.Test
import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.nullValue
import static org.junit.Assert.assertThat

class GitStateTests {
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
    void getCurrentBranch_good() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        assertThat new GitState().getCurrentBranch(), equalTo('test_branch')
    }


    @Test
    void getCurrentBranch_bad() {
        helper.putCmdOutput 'git status', '# Not currently on any branch.\n'
        assertThat new GitState().getCurrentBranch(), nullValue(String)
    }


    @Test
    void isInvalidBranch_true() {
        helper.putCmdOutput 'git status', '# Not currently on any branch.\n'
        assertThat new GitState().isInvalidBranch(), equalTo(true)
    }


    @Test
    void isInvalidBranch_false() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        assertThat new GitState().isInvalidBranch(), equalTo(false)
    }


    @Test
    void hasChanges_true() {
        helper.putCmdOutput 'git status', '\n# Changed but not updated:\n'
        assertThat new GitState().hasChanges(), equalTo(true)

        helper.putCmdOutput 'git status', '\n# Changes to be committed:\n'
        assertThat new GitState().hasChanges(), equalTo(true)
    }


    @Test
    void hasChanges_false() {
        helper.putCmdOutput 'git status', '#\nnothing to commit (working directory clean)\n'
        assertThat new GitState().hasChanges(), equalTo(false)

        helper.putCmdOutput 'git status', '#\nnothing added to commit but untracked files present (use "git add" to track)\n'
        assertThat new GitState().hasChanges(), equalTo(false)
    }


    @Test
    void getTrackedBranch_simple() {
        helper.putCmdOutput 'git status', '# On branch mybranch'
        helper.putCmdOutput 'git config --get branch.mybranch.remote', 'origin\n'
        helper.putCmdOutput 'git config --get branch.mybranch.merge', 'refs/heads/mybranch'
        assertThat new GitState().getTrackedBranch(), equalTo('origin/mybranch')
    }


    @Test
    void getTrackedBranch_missing_remote() {
        helper.putCmdOutput 'git status', '# On branch mybranch'
        helper.putCmdOutput 'git config --get branch.mybranch.remote', '\n'
        helper.putCmdOutput 'git config --get branch.mybranch.merge', ''
        helper.putCmdOutput 'git remote', 'a_server\n'
        assertThat new GitState().getTrackedBranch(), equalTo('')
    }


    @Test
    void getTrackedBranch_missing_merge() {
        helper.putCmdOutput 'git status', '# On branch mybranch'
        helper.putCmdOutput 'git config --get branch.mybranch.remote', 'origin'
        helper.putCmdOutput 'git config --get branch.mybranch.merge', ''
        assertThat new GitState().getTrackedBranch(), equalTo('')
    }


    @Test
    void getRemoteName_from_remote_branch() {
        helper.putCmdOutput 'git status', '# On branch mybranch'
        helper.putCmdOutput 'git config --get branch.mybranch.remote', 'remote_server'
        assertThat new GitState().remoteName, equalTo('remote_server')
    }


    @Test
    void getRemoteName_from_local_branch() {
        helper.putCmdOutput 'git status', '# On branch mybranch'
        helper.putCmdOutput 'git config --get branch.mybranch.remote', ''
        helper.putCmdOutput 'git remote', 'a_server\n'
        assertThat new GitState().remoteName, equalTo('a_server')
    }


    @Test
    void getRemoteName_from_local_branch_no_remote() {
        helper.putCmdOutput 'git status', '# On branch mybranch'
        helper.putCmdOutput 'git config --get branch.mybranch.remote', ''
        helper.putCmdOutput 'git remote', ''
        assertThat new GitState().remoteName, equalTo('')
    }


    @Test
    void getRemoteName_from_local_branch_multiple_remotes() {
        helper.putCmdOutput 'git status', '# On branch mybranch'
        helper.putCmdOutput 'git config --get branch.mybranch.remote', ''
        helper.putCmdOutput 'git remote', "a_remote\nb_remote"
        assertThat new GitState().remoteName, equalTo('')
    }

}
