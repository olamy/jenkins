# This is a CloudBees managed LTS versions of Jenkins core
The presence of this file indicates that this Jenkins is turned into Nectar.

When we take OSS Jenkins and start our sustaining branches (aka Nectarize), a few changes need
to be made to POM. The `nectarize` branch keeps track of the changes we need. The idea
is whenever we need to nectarize, we do so by merging this branch into it.

## How to nectarize
When the community is done with its LTS releases on a `stable-1.xxx` branch and CloudBees is
ready to take over, or private builds are used the the current LTS line, we do the following:

* Add as remote the OSS reporsitory `git remote add oss https://github.com/jenkinsci/jenkins.git` and `git fetch oss`. Your remote `origin` is supossed to be `https://github.com/cloudbees-oss/private-jenkins.git`

* First thing is update `nectarize` branch with the changes on the __previous LTS__ merging the `cb.X.Y` into
  `nectarize` branch: `git fetch origin; git branch -D nectarize; git checkout -t origin/nectarize; git merge --no-ff origin/cb-X.Y` and push your changes.

* `git merge 'jenkins-x.zzz^'` to merge the parent commit of the release tag into this workspace (you will need the community repository tags for this).

* Resolve merge conflicts carefully. If necessary, check the difference between the `nectarize`
  branch and its previous base. Tip using *the previous LTS* `git diff origin/cb-X.Y..origin/community-X.Y --name-only`. If you have changes in files that has conflicts, please take care.
  
* Commit the change and push that into the `nectarize` branch

* Create a branch `community-x.yyy` from branch `stable-x.yyy` in OSS. If the branch is not existing yet, use tag `jenkins-x.yyy`. Push `community-x.yyy` branch to `origin` reposotory

* Create a `cb-x.yyy` branch from the `community-x.yyy` repo (which should be in sync with the community repo).

* Run `git merge nectarize` to merge the tip of the `nectarize` over the branch `cb-x.yyy`
  
* Run `mvn release:update-versions -DdevelopmentVersion=x.yyy.zz-cb-w-SNAPSHOT` on branch `cb-x.yyy` after `nectarized` branch is merged on it. 

* Run `mvn -V -B io.jenkins.tools.incrementals:incrementals-maven-plugin:reincrementalify` [Only if incremental is enabled (from 2.138 LTS onwards)]

* Commit the changes into `cb-x.yyy` branch

* Push the resulting `cb-x.yyy` into the `private-jenkins` repo.

__Congratulations,
  a new LTS release line is properly nectarized.__
