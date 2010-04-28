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
    protected Logger logger = LoggerFactory.getLogger(GitState)
    @Delegate final ExecutionHelper executionHelper = ExecutionHelper.instance
    private String _statOut = null
    private String _trackedBranch = null
    private String _remoteName = null
    private String _currentBranch = null


    String getStatOut() {
        if (!_statOut) {
            _statOut = cmdOutput('git status')
        }
        _statOut
    }


    boolean isInvalidBranch() {
        statOut.contains('# Not currently on any branch.')
    }


    boolean hasChanges() {
        statOut.contains('# Changed but not updated:') || statOut.contains('# Changes to be committed:')
    }


    String getCurrentBranch() {
        if (!_currentBranch) {
            _currentBranch = isInvalidBranch() ? null : (statOut =~ /^# On branch (\S+)/)[0][1]
        }
        _currentBranch
    }


    String getTrackedBranch() {
        if (_trackedBranch == null) {
            def remote = getRemoteName()
            String tb = ''
            if (remote) {
                def branch = cmdOutput("git config --get branch.${currentBranch}.merge") - 'refs/heads/'
                if (branch)
                    tb = "${remote}/${branch}"
            }
            _trackedBranch = tb
        }
        _trackedBranch
    }


    String getRemoteName() {
        if (_remoteName == null) {
            String rn = cmdOutput("git config --get branch.${currentBranch}.remote")
            if (!rn) {
                String remotes = cmdOutput('git remote')
                String[] remotesArray = remotes.split()
                if (remotesArray.length == 1) {
                    rn = remotesArray[0]
                }
                else if (remotesArray.length == 0) {
                    logger.warn 'There are no remotes set up for this repository'
                }
                else {
                    logger.warn "Can not determine which remote name to use ${Arrays.asList(remotesArray)}"
                }
            }
            _remoteName = rn
        }
        _remoteName
    }

}