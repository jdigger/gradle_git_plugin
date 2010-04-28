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

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GitState {
    def statOut
    protected Logger logger = LoggerFactory.getLogger(GitState)
    @Delegate final ExecutionHelper executionHelper = ExecutionHelper.instance


    GitState() {
        statOut = cmdOutput('git status')
    }


    String getStatOut() {
        if (!statOut) {
            statOut = cmdOutput('git status')
        }
        statOut
    }


    boolean isInvalidBranch() {
        statOut.contains('# Not currently on any branch.')
    }


    boolean hasChanges() {
        statOut.contains('# Changed but not updated:') || statOut.contains('# Changes to be committed:')
    }


    String getCurrentBranch() {
        isInvalidBranch() ? null : (statOut =~ /^# On branch (\S+)/)[0][1]
    }


    String getTrackedBranch() {
        def remote = getRemoteName()
        if (remote) {
            def branch = cmdOutput("git config --get branch.${currentBranch}.merge") - 'refs/heads/'
            if (branch)
                return "${remote}/${branch}"
        }
        return ''
    }


    String getRemoteName() {
        String rn = cmdOutput("git config --get branch.${currentBranch}.remote")
        if (rn) return rn
        String remotes = cmdOutput('git remote')
        String[] remotesArray = remotes.split()
        if (remotesArray.length == 0) {
            logger.warn 'There are no remotes set up for this repository'
            return ''
        }
        else if (remotesArray.length == 1) {
            return remotesArray[0]
        }
        else {
            logger.warn "Can not determine which remote name to use ${Arrays.asList(remotesArray)}"
            return ''
        }
    }

}
