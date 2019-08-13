# This is a CloudBees managed LTS versions of Jenkins core
The presence of this file indicates that this Jenkins is turned into Nectar.

When we take OSS Jenkins and start our sustaining branches (aka Nectarize), a few changes need
to be made to POM. The `nectarize` branch keeps track of the changes we need. The idea
is whenever we need to nectarize, we do so by merging this branch into it.

## How to nectarize
When the community is done with its LTS releases on a `stable-1.xxx` branch and CloudBees is
ready to take over, or private builds are used the the current LTS line, we do the following:

* Determine the correct 3rd digit for the nectarized version, such as `x.yyy.18`.
  This 3rd digit is kept the same across all the sustaining LTS branches that are
  actively maintained, so the easiest way is to find this is to check out the previous
  nectarized LTS line from the cloudbees repo and look at its version number.
  We will refer to this number in `1.xxx.yy` as `yy`. If we are nectarizing the current LTS line,
  the version number is the same than the community one but `-cb-x` is added at the end, with `x`
  starting at 1.

* Created a `cb-x.yyy` branch from the `community-x.yyy` repo (which should be in sync with the community repo).

* Run `mvn release:update-versions -DdevelopmentVersion=x.yyy.zz-cb-w-SNAPSHOT` on
  the workspace from the previous step. If unsure, see commit d08cf489bce4ab8f109c59b1ce3aaec7a87d3298
  for an actual example of how this was done. This step reduces the merge conflict in the next step.
* Only if incremental is enabled (from 2.138 LTS onwards), then run also `mvn -V -B io.jenkins.tools.incrementals:incrementals-maven-plugin:reincrementalify`. Further details: https://github.com/jenkinsci/incrementals-tools#running-maven-releases


* Run `git merge nectarize` to merge the tip of the `nectarize`.
  If this step results in merge conflicts, *DO NOT RESOLVE merge conflicts here*. Instead,
  abandon the merge, follow the "Update nectarize branch" process below, then retry this
  step from scratch.

* Push the resulting `cb-x.yyy` into the `private-jenkins` repo. Congratulations,
  a new LTS release line is properly nectarized.

## How to update nectarize branch
The `nectarize` branch contains POM changes that are necessary to internalize LTS release lines,
and normally it should merge cleanly to any `stable-x.yyy` branches. However, on rare occasions,
the OSS Jenkins project modifies POMs in ways that cause merge conflicts.

When this happens, it is important to update the `nectarize` branch to reflect this changes,
instead of resolving merge conflicts at the point of `stable-x.yyy` merges. Otherwise there
won't be any guarantee that such merge conflicts are resolved consistently.

Say you discovered that you need to update the `nectarize` branch while trying to nectarize
`stable-x.zzz`. The following process updates `nectarize` branch:

* Checkout the `nectarize` branch from the cloudbees repo
* `git merge 'jenkins-x.zzz^'` to merge the parent commit of the release tag into this workspace (you will need the community repository tags for this).
* Resolve merge conflicts carefully. If necessary, check the difference between the `nectarize`
  branch and its previous base.
* Commit the change and push that into the `nectarize` branch

