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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

abstract class GitTask extends DefaultTask {
    @Delegate final ExecutionHelper executionHelper = ExecutionHelper.instance
    GitState gitState


    GitState getGitState() {
        if (!gitState) gitState = new GitState()
        gitState
    }

}

abstract class AbstractGitTask extends GitTask {
    @TaskAction
    void runCommand() {
        runCmd commandToExecute()
    }


    abstract String commandToExecute()

}


class GitFetchTask extends AbstractGitTask {

    String commandToExecute() {
        'git fetch'
    }

}


class GitRebaseTask extends GitTask {
    String remoteMachine = 'origin'
    String sourceBranch
    boolean rebaseAgainstServer


    GitRebaseTask() {
        doFirst {
            if (!sourceBranch) throw new GradleException('Need a sourceBranch')

            if (gitState.isInvalidBranch()) throw new GradleException("Not working on a local branch.")
            if (gitState.hasChanges()) throw new GradleException("You still have uncommitted changes.")

            if (rebaseAgainstServer) {
                runCmd "git fetch"
                runCmd "git rebase ${remoteMachine}/${sourceBranch}"
            }
            else {
                runCmd "git rebase ${sourceBranch}"
            }
        }
    }
}


class GitPushTask extends AbstractGitTask {
    String remoteMachine = 'origin'
    String localBranch
    String remoteBranch
    boolean force = false


    String commandToExecute() {
        if (!localBranch) throw new GradleException('Need a localBranch')
        if (!remoteBranch) throw new GradleException('Need a remoteBranch')
        "git push ${force ? '-f ' : ''}${remoteMachine} ${localBranch}:${remoteBranch}"
    }

}


class GitRemoveBranchTask extends AbstractGitTask {
    String remoteMachine = 'origin'
    String branch
    boolean remote = false
    boolean force = false


    String commandToExecute() {
        if (!branch) throw new GradleException('Need a branch')
        if (remote) {
            "git push ${remoteMachine} :${branch}"
        }
        else {
            "git branch -${force ? 'D' : 'd'} ${branch}"
        }
    }

}


class GitChangeTrackedBrachTask extends GitTask {
    String remoteMachine = 'origin'
    String branch
    String trackedBranch


    GitChangeTrackedBrachTask() {
        doFirst {
            if (!branch) throw new GradleException('Need a branch')
            if (!trackedBranch) throw new GradleException('Need a trackedBranch')

            def remoteName = cmdOutput("git config branch.${branch}.remote")
            if (!remoteName) runCmd("git config branch.${branch}.remote ${remoteMachine}")
            if (trackedBranch != "${remoteMachine}/${branch}") runCmd("git config branch.${branch}.merge refs/heads/${trackedBranch}")
            else logger.info("Already on ${trackedBranch}")
        }
    }

}
