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
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.StringDescription
import org.junit.After
import org.junit.Before
import org.junit.Test
import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat
import static org.junit.Assert.fail

class GitPluginTests {
    final ExecutionHelper helper = ExecutionHelper.instance
    Project project
    GitPlugin gitPlugin = new GitPlugin()


    @Before
    void initExecutionHelper() {
        helper.debugMode = true
    }


    @Before
    void createProject() {
        project = GradleTestHelper.createRootProject()
    }


    @Before
    void createPlugin() {
        gitPlugin = new GitPlugin()
        GitPlugin.gitState = null
        gitPlugin.userName = 'tuser'
    }


    @After
    void cleanupExecutionHelper() {
        helper.reset()
    }


    @Test
    void applyTasks_no_tracking() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        helper.putCmdOutput 'git config --get branch.test_branch.remote', ''
        helper.putCmdOutput 'git config --get branch.test_branch.merge', ''
        helper.putCmdOutput 'git remote', ''

        gitPlugin.apply project

        TaskContainer tasks = project.getTasks()

        Task task

        assertThat tasks.findByName('update'), nullValue(Task)

        assertThat tasks.findByName('push-private'), nullValue(Task)
        assertThat tasks.findByName('-push-work'), nullValue(Task)
        assertThat tasks.findByName('-push-review'), nullValue(Task)
        assertThat tasks.findByName('-push-integration'), nullValue(Task)
        assertThat tasks.findByName('track-work'), nullValue(Task)
        assertThat tasks.findByName('track-review'), nullValue(Task)
        assertThat tasks.findByName('track-integration'), nullValue(Task)
        assertThat tasks.findByName('refresh-remote-branches'), nullValue(Task)

        task = tasks.getByName('start2459')
        assertThat task.actions.size(), equalTo(1)
        assertThat task, dependsOn()
    }


    @Test
    void applyTasks_tracking_integration() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        helper.putCmdOutput 'git config --get branch.test_branch.remote', 'remote_machine'
        helper.putCmdOutput 'git config --get branch.test_branch.merge', 'refs/heads/source_branch'

        gitPlugin.apply project

        final TaskContainer tasks = project.getTasks()

        Task task

        task = tasks.findByName('update')
        assertThat task, instanceOf(GitRebaseTask)
        assertThat task.rebaseAgainstServer, equalTo(true)
        assertThat task.sourceBranch, equalTo('remote_machine/source_branch')
        assertThat task, dependsOn()

        task = tasks.getByName('push-private')
        assertThat task, instanceOf(GitPushTask)
        assertThat task.remoteMachine, equalTo('remote_machine')
        assertThat task.localBranch, equalTo('test_branch')
        assertThat task.remoteBranch, equalTo('work/tuser/test_branch')
        assertThat task.force, equalTo(true)
        assertThat task, dependsOn()

        task = tasks.getByName('push-work')
        assertThat task, instanceOf(GitPushTask)
        assertThat task.remoteMachine, equalTo('remote_machine')
        assertThat task.localBranch, equalTo('test_branch')
        assertThat task.remoteBranch, equalTo('work/source_branch/test_branch')
        assertThat task.force, equalTo(false)
        assertThat task, dependsOn('update')

        task = tasks.getByName('push-review')
        assertThat task, instanceOf(GitPushTask)
        assertThat task.remoteMachine, equalTo('remote_machine')
        assertThat task.localBranch, equalTo('test_branch')
        assertThat task.remoteBranch, equalTo('review/source_branch/test_branch')
        assertThat task.force, equalTo(false)
        assertThat task, dependsOn('update')

        task = tasks.getByName('push-integration')
        assertThat task, instanceOf(GitPushTask)
        assertThat task.remoteMachine, equalTo('remote_machine')
        assertThat task.localBranch, equalTo('test_branch')
        assertThat task.remoteBranch, equalTo('source_branch')
        assertThat task.force, equalTo(false)
        assertThat task, dependsOn('update')

        task = tasks.getByName('track-work')
        assertThat task, instanceOf(GitChangeTrackedBranchTask)
        assertThat task.branch, equalTo('test_branch')
        assertThat task.trackedBranch, equalTo('work/source_branch/test_branch')
        assertThat task.remoteMachine, equalTo('remote_machine')
        assertThat task, dependsOn()

        task = tasks.getByName('track-review')
        assertThat task, instanceOf(GitChangeTrackedBranchTask)
        assertThat task.branch, equalTo('test_branch')
        assertThat task.trackedBranch, equalTo('review/source_branch/test_branch')
        assertThat task.remoteMachine, equalTo('remote_machine')
        assertThat task, dependsOn()

        task = tasks.getByName('track-integration')
        assertThat task, instanceOf(GitChangeTrackedBranchTask)
        assertThat task.branch, equalTo('test_branch')
        assertThat task.trackedBranch, equalTo('source_branch')
        assertThat task.remoteMachine, equalTo('remote_machine')
        assertThat task, dependsOn()

        task = tasks.getByName('refresh-remote-branches')
        assertThat task.actions.size(), equalTo(1)
        assertThat task, dependsOn()
    }


    @Test
    void runRefreshRemoteBranches() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        helper.putCmdOutput 'git config --get branch.test_branch.remote', 'remote_machine'
        helper.putCmdOutput 'git config --get branch.test_branch.merge', 'refs/heads/source_branch'

        gitPlugin.apply project

        executeTask('refresh-remote-branches')
        assertThat helper.cmds, hasItem('git remote prune remote_machine')
    }


    @Test
    void runTrackReview_with_remote() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        helper.putCmdOutput 'git config --get branch.test_branch.remote', 'remote_machine'
        helper.putCmdOutput 'git config --get branch.test_branch.merge', 'refs/heads/source_branch'

        gitPlugin.apply project

        executeTask('track-review')
        assertThat helper.cmds, hasItem('git config branch.test_branch.merge refs/heads/review/source_branch/test_branch')
    }


    @Test
    void runTrackReview_with_remote_and_custom_review_basename() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        helper.putCmdOutput 'git config --get branch.test_branch.remote', 'remote_machine'
        helper.putCmdOutput 'git config --get branch.test_branch.merge', 'refs/heads/test_source_branch'

        project.baseReviewBranchName = 'test_reviewing'

        gitPlugin.apply project

        executeTask('track-review')
        assertThat helper.cmds, hasItem('git config branch.test_branch.merge refs/heads/test_reviewing/test_source_branch/test_branch')
    }


    @Test
    void runTrackReview_with_specified_name() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        helper.putCmdOutput 'git config --get branch.test_branch.remote', 'remote_machine'
        helper.putCmdOutput 'git config --get branch.test_branch.merge', 'refs/heads/test_source_branch'

        project.reviewBranch = 'my/custom/review/name'

        gitPlugin.apply project

        executeTask('track-review')
        assertThat helper.cmds, hasItem('git config branch.test_branch.merge refs/heads/my/custom/review/name')
    }


    @Test
    void runPushReview_with_remote_and_new_custom_review_basename() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        helper.putCmdOutput 'git config --get branch.test_branch.remote', 'remote_machine'
        helper.putCmdOutput 'git config --get branch.test_branch.merge', 'refs/heads/test_source_branch'

        project.baseReviewBranchName = 'test_reviewing'

        gitPlugin.apply project

        executeTask('push-review')
        assertThat helper.cmds, hasItem('git push remote_machine test_branch:test_reviewing/test_source_branch/test_branch')
        assertThat helper.cmds, hasItem('git config branch.test_branch.merge refs/heads/test_reviewing/test_source_branch/test_branch')
    }


    @Test
    void runTrackWork_with_remote_and_new_pattern() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        helper.putCmdOutput 'git config --get branch.test_branch.remote', 'remote_machine'
        helper.putCmdOutput 'git config --get branch.test_branch.merge', 'refs/heads/test_source_branch'

        project.baseWorkBranchName = 'test_working'

        gitPlugin.apply project

        executeTask('track-work')
        assertThat helper.cmds, hasItem('git config branch.test_branch.merge refs/heads/test_working/test_source_branch/test_branch')
    }


    @Test
    void runTrackWork_with_specified_name() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        helper.putCmdOutput 'git config --get branch.test_branch.remote', 'remote_machine'
        helper.putCmdOutput 'git config --get branch.test_branch.merge', 'refs/heads/test_source_branch'

        project.workBranch = 'my/custom/name'

        gitPlugin.apply project

        executeTask('track-work')
        assertThat helper.cmds, hasItem('git config branch.test_branch.merge refs/heads/my/custom/name')
    }


    @Test
    void runPushWork_with_remote_and_new_pattern() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        helper.putCmdOutput 'git config --get branch.test_branch.remote', 'remote_machine'
        helper.putCmdOutput 'git config --get branch.test_branch.merge', 'refs/heads/test_source_branch'

        project.baseWorkBranchName = 'test_working'

        gitPlugin.apply project

        executeTask('push-work')
        assertThat helper.cmds, hasItem('git push remote_machine test_branch:test_working/test_source_branch/test_branch')
        assertThat helper.cmds, hasItem('git config branch.test_branch.merge refs/heads/test_working/test_source_branch/test_branch')
    }


    @Test
    void runTrackIntegration_with_remote_and_custom_review_basename() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        helper.putCmdOutput 'git config --get branch.test_branch.remote', 'remote_machine'
        helper.putCmdOutput 'git config --get branch.test_branch.merge', 'refs/heads/test_reviewing/test_source_branch/test_branch'

        project.baseReviewBranchName = "test_reviewing"

        gitPlugin.apply project

        executeTask('track-integration')
        assertThat helper.cmds, hasItem('git config branch.test_branch.merge refs/heads/test_source_branch')
    }


    @Test
    void runPushIntegration_with_remote_and_custom_review_basename() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        helper.putCmdOutput 'git config --get branch.test_branch.remote', 'remote_machine'
        helper.putCmdOutput 'git config --get branch.test_branch.merge', 'refs/heads/test_reviewing/test_source_branch/test_branch'

        project.baseReviewBranchName = "test_reviewing"

        gitPlugin.apply project

        executeTask('push-integration')
        assertThat helper.cmds, hasItem('git push remote_machine test_branch:test_source_branch')
        assertThat helper.cmds, hasItem('git config branch.test_branch.merge refs/heads/test_source_branch')
    }


    @Test
    void runPushIntegration_with_remote_and_custom_work_basename() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        helper.putCmdOutput 'git config --get branch.test_branch.remote', 'remote_machine'
        helper.putCmdOutput 'git config --get branch.test_branch.merge', 'refs/heads/test_working/test_source_branch/test_branch'

        project.baseWorkBranchName = "test_working"

        gitPlugin.apply project

        executeTask('push-integration')
        assertThat helper.cmds, hasItem('git push remote_machine test_branch:test_source_branch')
        assertThat helper.cmds, hasItem('git config branch.test_branch.merge refs/heads/test_source_branch')
    }


    @Test
    void runRemovePrivate_simple() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        helper.putCmdOutput 'git config --get branch.test_branch.remote', ''
        helper.putCmdOutput 'git remote', 'origin'
        helper.putCmdOutput 'git config --get branch.test_branch.merge', ''
        helper.putCmdOutput 'git branch -r', "  origin/master\n  origin/work/tuser/test_branch\n"

        gitPlugin.apply project

        executeTask('remove-private')
        assertThat helper.cmds, hasItem('git push origin :work/tuser/test_branch')
    }


    @Test
    void runRemovePrivate_no_existing_branch() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        helper.putCmdOutput 'git config --get branch.test_branch.remote', 'remote_machine'
        helper.putCmdOutput 'git config --get branch.test_branch.merge', ''
        helper.putCmdOutput 'git branch -r', "  origin/master\n"

        gitPlugin.apply project

        try {
            executeTask('remove-private')
            fail "Should have thrown a GradleException because branch does not exist"
        }
        catch (GradleException exp) {
            // expected
        }
    }


    @Test
    void runRemoveWork_simple() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        helper.putCmdOutput 'git config --get branch.test_branch.remote', 'origin'
        helper.putCmdOutput 'git config --get branch.test_branch.merge', 'work/int_branch/test_branch'
        helper.putCmdOutput 'git branch -r', "  origin/master\n  origin/work/int_branch/test_branch\n"

        gitPlugin.apply project

        executeTask('remove-work')
        assertThat helper.cmds, hasItem('git push origin :work/int_branch/test_branch')
    }


    @Test
    void runRemoveWork_no_existing_branch() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        helper.putCmdOutput 'git config --get branch.test_branch.remote', 'remote_machine'
        helper.putCmdOutput 'git config --get branch.test_branch.merge', ''
        helper.putCmdOutput 'git branch -r', "  origin/master\n"

        gitPlugin.apply project

        try {
            executeTask('remove-work')
            fail "Should have thrown a GradleException because branch does not exist"
        }
        catch (GradleException exp) {
            // expected
        }
    }


    @Test
    void runRemoveReview_simple() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        helper.putCmdOutput 'git config --get branch.test_branch.remote', 'origin'
        helper.putCmdOutput 'git config --get branch.test_branch.merge', 'work/int_branch/test_branch'
        helper.putCmdOutput 'git branch -r', "  origin/master\n  origin/review/int_branch/test_branch\n"

        gitPlugin.apply project

        executeTask('remove-review')
        assertThat helper.cmds, hasItem('git push origin :review/int_branch/test_branch')
    }


    @Test
    void runRemoveReview_customer_review_name() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        helper.putCmdOutput 'git config --get branch.test_branch.remote', 'origin'
        helper.putCmdOutput 'git config --get branch.test_branch.merge', 'work/int_branch/test_branch'
        helper.putCmdOutput 'git branch -r', "  origin/master\n  origin/test_reviewing/int_branch/test_branch\n"

        project.baseReviewBranchName = "test_reviewing"

        gitPlugin.apply project

        executeTask('remove-review')
        assertThat helper.cmds, hasItem('git push origin :test_reviewing/int_branch/test_branch')
    }


    @Test
    void runRemoveReview_no_existing_branch() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        helper.putCmdOutput 'git config --get branch.test_branch.remote', 'remote_machine'
        helper.putCmdOutput 'git config --get branch.test_branch.merge', ''
        helper.putCmdOutput 'git branch -r', "  origin/master\n"

        gitPlugin.apply project

        try {
            executeTask('remove-review')
            fail "Should have thrown a GradleException because branch does not exist"
        }
        catch (GradleException exp) {
            // expected
        }
    }


    @Test
    void runStart1() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        helper.putCmdOutput 'git config --get branch.test_branch.remote', 'remote_machine'
        helper.putCmdOutput 'git config --get branch.test_branch.merge', 'refs/heads/source_branch'

        gitPlugin.apply project

        project.branchPrefix = 'TESTISSUE-'

        executeTask('start3568')
        assertThat helper.cmds, hasItem('git checkout -b TESTISSUE-3568 remote_machine/source_branch')
    }


    @Test
    void runStart2() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        helper.putCmdOutput 'git config --get branch.test_branch.remote', 'remote_machine'
        helper.putCmdOutput 'git config --get branch.test_branch.merge', ''

        gitPlugin.apply project

        project.branchPrefix = 'TESTISSUE-'
        project.integrationBranch = 'source_branch'

        executeTask('start3568')
        assertThat helper.cmds, hasItem('git checkout -b TESTISSUE-3568 remote_machine/source_branch')
    }


    @Test
    void runStart3() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        helper.putCmdOutput 'git config --get branch.test_branch.remote', 'remote_machine'
        helper.putCmdOutput 'git config --get branch.test_branch.merge', ''

        gitPlugin.apply project

        project.branchPrefix = 'TESTISSUE-'

        executeTask('start3568')
        assertThat helper.cmds, hasItem('git checkout -b TESTISSUE-3568 remote_machine/master')
    }


    @Test
    void runStart4() {
        helper.putCmdOutput 'git status', '# On branch test_branch\n'
        helper.putCmdOutput 'git config --get branch.test_branch.remote', ''
        helper.putCmdOutput 'git config --get branch.test_branch.merge', ''
        helper.putCmdOutput 'git remote', ''

        gitPlugin.apply project

        project.branchPrefix = 'TESTISSUE-'

        executeTask('start3568')
        assertThat helper.cmds, hasItem('git checkout -b TESTISSUE-3568 master')
    }


    List executeTask(String taskName) {
        gitPlugin.executeTask(project, taskName)
    }


    public static Matcher<Task> dependsOn(final String... tasks) {
        return dependsOn(equalTo(new HashSet<String>(Arrays.asList(tasks))))
    }


    public static Matcher<Task> dependsOn(final Matcher<? extends Iterable<String>> matcher) {
        return new BaseMatcher<Task>() {
            public boolean matches(obj) {
                Task task = Task.cast(obj)
                Set<? extends Task> depTasks = task.taskDependencies.getDependencies(task)
                Set<String> names = new HashSet<String>(depTasks.collect {Task depTask -> depTask.name})
                boolean matches = matcher.matches(names)
                if (!matches) {
                    StringDescription description = new StringDescription()
                    matcher.describeTo(description)
                    println(String.format("expected %s, got %s.", description.toString(), names))
                }
                matches
            }


            public void describeTo(Description description) {
                description.appendText("a Task that depends on ").appendDescriptionOf(matcher)
            }
        }
    }
}
