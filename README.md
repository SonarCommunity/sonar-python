# Code Quality and Security for Python [![Build Status](https://api.cirrus-ci.com/github/SonarSource/sonar-python.svg?branch=master)](https://cirrus-ci.com/github/SonarSource/sonar-python)  [![Quality Gate](https://next.sonarqube.com/sonarqube/api/project_badges/measure?project=org.sonarsource.python%3Apython&metric=alert_status)](https://next.sonarqube.com/sonarqube/dashboard?id=https://next.sonarqube.com/sonarqube/dashboard?id=org.sonarsource.python%3Apython)
#### Python analyzer for SonarQube, SonarCloud and SonarLint

## Useful links

* [Project homepage](https://www.sonarsource.com/products/codeanalyzers/sonarpython.html)
* [Issue tracking](http://jira.sonarsource.com/browse/SONARPY)
* [Available rules](https://rules.sonarsource.com/python)
* [SonarSource Community Forum](https://community.sonarsource.com) for feedback

## Building the project

### Fast/minimal build

**Prerequisites:**
- JDK 11
- Maven 3.0.0 or newer

The easiest way to build the Project is by running:

`mvn clean install -DskipTypeshed`

It builds only Java Maven modules, run tests, and install jar locally.
The Python interpreter is not required in that case.

### Full build

**Prerequisites:**
- JDK 11
- Maven 3.0.0 or newer
- Python 3.9 or newer
- [tox](https://tox.readthedocs.io/en/latest/) - `pip install tox`
- Run `git submodule update --init` to retrieve [Typeshed](https://github.com/python/typeshed) as a Git submodule

All above should be available in PATH.

To execute full build just run:

`mvn clean install`

The full build executes [Typeshed](https://github.com/python/typeshed). 
It generates protobuf messages for Typeshed symbols (for standard Python API) and our customs symbols 
(for Python libraries, e.g. [AWS CDK](https://docs.aws.amazon.com/cdk/v2/guide/work-with-cdk-python.html)).
This helps in types interference and providing better rules.  

## How to contribute

### Configuration

First, please configure your IDE:
https://github.com/SonarSource/sonar-developer-toolset.

### Rule annotation

Each new implemented rule should have `@Rule(key = "S0000")` annotation on the class level.
The number of the rule can be find here: https://sonarsource.github.io/rspec/#/rspec/?lang=python.
The key needs to be unique in the whole project.

### Expectations:
- working on separate branch and creating PR when it's finish
- clean coded, well tested solution 
- fix all issues reported by SonarLint
- 100% code coverage for new changes (if possible). It can be checked on CI build.

### Before push

Please check if all files have a license header.
If not, the `mvn install` will fail with `Some files do not have the expected license header` message.
To fix that please execute: `mvn license:format`.

## License

Copyright 2011-2022 SonarSource.

Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)
