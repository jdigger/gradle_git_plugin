# Purpose

This is a plugin intended to work with other plugins to help establish a Git workflow for a project.

# Creating the Plugin JAR

    gradlew assemble

# Usage

    usePlugin(com.mooregreatsoftware.gradle.gitplugin.GitPlugin)

    buildscript {
      dependencies {
        classpath 'com.mooregreatsoftware.gradle:gitplugin:1.0.0'
      }
    }

The plugin jar is not currently published in a public repo, so you will have to add your own `repository` section.

# Predefined Workflow

## Normal Development

Look at bug system/cards and start developing against ISSUE-1234

    git checkout -b ISSUE-1234 origin/master

Work the issue.  Paranoid that my machine will eat the files, but want to make sure no-one else on my team will base any work off of my checkins.

    gradle push-private

Want to make sure that I have the latest from the integration branch and rebase my work against it.

    gradle update

I'm ready for others to review my changes before putting into into the "golden integration branch".

    gradle push-review

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

### refresh-remote-branches
Removes references to remote branches that no longer exist.

# Author and Origin

These plugins were written by Jim Moore.  They are published at [GitHub:jdigger/gradle\_git\_plugin](http://github.com/jdigger/gradle_git_plugin).

# License

All these plugins are licensed under the [Apache Software License 2.0](http://www.apache.org/licenses/LICENSE-2.0.html) license with no warranty (expressed or implied) for any purpose.
