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
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Singleton
class ExecutionHelper {
    protected Logger logger = LoggerFactory.getLogger(ExecutionHelper)
    protected boolean debugMode = false
    protected List<String> cmds = []
    protected Map<String, Object> cmdOutputs = [:]


    void setDebugMode(boolean debug) {
        debugMode = debug
    }


    void setCmds(List<String> c) {
        debugMode = true
        cmds = c
    }


    List<String> getCmds() {
        cmds
    }


    void reset() {
        cmdOutputs = [:]
        cmds = []
        debugMode = false
    }


    void putCmdOutput(String cmd, output) {
        cmdOutputs.put(cmd, output)
    }


    void runCmd(String cmd) {
        logger.warn cmd

        if (debugMode) {
            cmds.add cmd
        }
        else {
            def outProc = Runtime.runtime.exec(cmd, null as String[], null)
            outProc.consumeProcessOutput(System.out, System.err)

            outProc.waitFor()

            if (outProc.exitValue()) {
                logger.error "Error code: ${outProc.exitValue()}"
                throw new GradleException(cmd)
            }
        }
    }


    String cmdOutput(String cmd) {
        cmdOutput(cmd, true)
    }


    String cmdOutput(String cmd, boolean showLog) {
        if (showLog) logger.warn cmd

        if (debugMode) {
            cmds.add cmd
            def output = cmdOutputs.get(cmd)
            if (output == null)
                throw new GradleException("Unknown command: $cmd")
            if (output instanceof Closure) {
                return output.call()
            }
            return output
        }
        else {
            cmd.execute().text
        }
    }
}
