# Purpose

This is a plugin intended to work with other plugins to help establish a Git workflow for a project.

# Creating the Plugin JAR

    gradlew assemble

# Usage

    apply plugin: com.mooregreatsoftware.gradle.gitplugin.GitPlugin

    buildscript {
      repositories {
        add(new org.apache.ivy.plugins.resolver.URLResolver()) {
                name = 'gitplugin_repo'
                addArtifactPattern "http://github.com/downloads/jdigger/gradle_git_plugin/[module]-[revision](-[classifier]).[ext]"
                descriptor = 'optional'
        }
      }
      dependencies {
        classpath 'com.mooregreatsoftware.gradle:gitplugin:1.1.0'
      }
    }

# Predefined Workflow

## Normal Development

Look at bug system/cards and start developing against ISSUE-1234

    gradle start1234

You can specify the prefix used in creating the branch name (e.g., 'ISSUE-') by setting the `branchPrefix` property in the project.  It will try to figure out the current integration branch (e.g., 'origin/master') based on the branch you are currently on, but if you want to explicitly specify it, set the `integrationBranch` property in the project.

Work the issue.  I'm paranoid that my machine will eat the files, but I want to make sure no-one else on my team will base any work off of my checkins.

    gradle push-private

That will create the branch "work/jmoore/ISSUE-1234" on the server. You can customize the behavior of how the work branch is created by setting the `baseWorkBranchName` property on the project.  The default name is `work`.  Or, if you don't want to use the convention, specify `workBranch` on the project to be whatever you want.

When I want to make sure that I have the latest from the integration branch and rebase my work against it.

    gradle update

I'm ready for others to review my changes before putting into into the "golden integration branch".

    gradle push-review

That will create the branch "review/master/ISSUE-1234" on the server.  You can customize the behavior of how the review branch is created by setting the `baseReviewBranchName` property on the project.  The default name is `review`. Or, if you don't want to use the convention, specify `reviewBranch` on the project to be whatever you want.

Rinse and repeat.


## Code Review

Checkout another developer's code.

    git checkout -b ISSUE-3456 origin/review/master/ISSUE-3456

Everything looks great.

    gradle push-integration


# Available Targets

### update
Pull remote changes from the currently tracked branch into the current branch.

### push-private
Push changes to a private branch on the server.

### push-work
Push changes to a branch that can be shared with other developers.

### push-review
Push changes to a branch used for doing code-reviews.

### push-integration
Push changes to the integration branch others base their work from.

### track-work
Change the local branch to track the "working" (i.e., shared) branch.

### track-review
Change the local branch to track the code-review branch.

### track-integration
Change the local branch to track the integration branch.

### remove-private
Remove the remote private development branch for the current issue.

### remove-work
Remove the remote shared development branch for the current issue.

### remove-review
Remove the remote code-review branch for the current issue.

### refresh-remote-branches
Removes references to remote branches that no longer exist.

# Author and Origin

These plugins were written by Jim Moore.  They are published at [GitHub:jdigger/gradle\_git\_plugin](http://github.com/jdigger/gradle_git_plugin).

# License

All these plugins are licensed under the [Apache Software License 2.0](http://www.apache.org/licenses/LICENSE-2.0.html) license with no warranty (expressed or implied) for any purpose.
