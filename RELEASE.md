# Release

When you are ready to release a new version, follow these steps:

1. Bump up the [VERSION](VERSION)

1. Update [CHANGELOG.md](CHANGELOG.md)

  * Replace the \[Unreleased\] with the version number.
  * Add current date to version number line.
  * Add a new \[Unreleased\] version.
  * Add a git diff link to the end of the file, and update the `unreleased` diff link.

1. Commit the changes

1. Add a new tag

  ```bash
  git tag -a v1.2.3 -m v1.2.3
  ```

1. Update `latest` tag

  ```bash
  git push origin :refs/tags/latest
  git tag -f -a latest -m latest
  ```

1. Push the tag

  ```bash
  git push --tags
  ```

1. Go to [Releases](https://github.com/sharetribe/harmony/releases) page in Github and draft a new release

  Use the following content:

  **Tag version:** \<the newly created tag\>

  **Release title:** \<version number\>

  **Describe this release:**

  ```markdown
  <copy the content from the [CHANGELOG.md](CHANGELOG.md)>
  ```

  Here's a full example:

  **Tag version:** v5.0.0

  **Release title:** v5.0.0

  **Describe this release:**

  ```markdown
  ### Changed

  - Upgraded Clojure from 1.8.0 to 1.9.0
  ```

1. Announce the new version at the [Open Source Community Forum](https://www.sharetribe.com/community/c/os-announcements)
