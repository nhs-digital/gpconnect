<img src="logo.png" height=72>

# GP Connect Demonstrator

This README contains information related to developing, building and running the GP Connect Demonstrator.

### Requirements
To develop and run the application locally you must have the following installed:

| Component | Description |
|---|---|
| Git | A revision control system. |
| NodeJS | An open source, cross-platform runtime environment for developing JavaScript applications. |
| Java JDK 11 | The Java Development Kit, which includes the Java Runtime Environment (JRE). |
| Maven 3 | A popular Java build tool. |
| MySQL | An open source RDBMS. |

***

### Getting started
First, download the code from GitHub. This can be done using the desktop git tool, an IDE which supports git or by downloading the code as a zip file which you can then extract.

Next, install the dev tools and dependencies....

### Installation of Development Tools and Dependencies
#### Install Git for Windows:
Install official git release:
https://git-scm.com/download/win

Or install GitHub Desktop which also includes a GUI interface for git management:
https://desktop.github.com/

#### Install NodeJS:
https://nodejs.org

#### Install Java Development Kit 11:
Given the change in Oracle licensing conditions at JDK11 users may wish to choose an alternative jdk11 implementation.
https://www.oracle.com/java/technologies/javase-jdk11-downloads.html

#### Install Maven 3:
https://maven.apache.org/download.cgi

#### Environment Variables
Ensure that the system environment variables for Java and Maven are set correctly, as described below...

M2_HOME should point to the install directory of your local Maven install folder, e.g.
```
M2_HOME C:\Maven\apache-maven-3.3.9
```

JAVA_HOME should point to the install directory of your local Java JDK install folder, e.g.
```
JAVA_HOME C:\Program Files\Java\jdk-11.0.8
```

PATH should contain the bin directory of both M2_HOME and JAVA_HOME, e.g.
```
...;%JAVA_HOME%\bin;%M2_HOME%\bin;...
```

### Installation of the MySQL database
Use the MySQL Installer (http://dev.mysql.com/downloads/installer) to install:
* MySQL Server
* MySQL Workbench
* MySQL Notifier

When using the Windows installer above, the PATH variables are automatically set.

Create the database schema and user locally by executing the following script:
```
{projectRoot}/config/sql/create_database.sql
```

Note: *All other scripts in this directory will be run (order defined in sql_script_run_order.info) each time the application starts. This means you'll get a fresh instance with default content each time. If you want the database state to persist between boots, change the 'database.reset' property to false (described later) however be aware that if the database schema is modified you may run into problems, so only do this if you know what you're doing!*

### Environment configuration
Some settings are specific to the environment the application is running on e.g. database username/password/location.

The environment specific files can be found in:
```
{projectRoot}/config
```

You are welcome to use these configuration files in situ, however we ask that any changes made are *not* committed back to GitHub, unless you're adding a new property. Instead you may wish to copy this entire directory to another location on your machine and point your application to the copied files (how to do this is covered later).

Environment properties are defined in 'gpconnect-demonstrator-api.properties'. If you need to modify any of these properties for your environment, it is recommended you create a new file called 'gpconnect-demonstrator-api.environment.properties' and set the overriding properties in here, as the contents of this file will take precedence over 'gpconnect-demonstrator-api.properties'.

### Installing front end packages

NOTE: There is a problem with the latest version of 'npm' so you may need to revert you npm version to '4.5.0'!
```sh
npm install -g npm@4.5.0
```

Install Grunt, the JavaScript task runner (you may need to be root user):
```sh
npm install -g grunt-cli bower
```

Navigate to the webapp folder of the GPConnect project and install dependencies:
```sh
cd {projectRoot}/webapp
bower install
bower update
npm update
```

### Building the front end
Run the *build* task within the webapp directory of the project.
```sh
cd {projectRoot}/webapp
grunt build
```

The *build* task minifies and uglifies the front end code in the webapp directory of the project, and packages it up in the gpconnect-demonstrator-api module under the following directory:
```
{projectRoot}/gpconnect-demonstrator-api/src/main/webapp
```

### Running the application
Note: *If you have not performed the 'Building the front end' task, there will be no UI in the project!*

Use maven to build the project:
```sh
cd {projectRoot}
mvn clean package
```

If working in an IDE you could run the following mvn task
```
spring-boot:run -Dserver.port=19191 -Dconfig.path=<path_to_config>\
```

To start with non-SSL on http://localhost:19191 and SSL on https://localhost:19192:
```sh
java -jar gpconnect-demonstrator-api\target\gpconnect-demonstrator-api.war --server.port=19192 --server.port.http=19191 --config.path=config\ --server.ssl.key-store=config\server.jks --server.ssl.key-store-password=password --server.ssl.trust-store=config\server.jks --server.ssl.trust-store-password=password --server.ssl.client-auth=want
```

If you're experiencing build errors, execute the following commands:
```sh
java -version
mvn --version
```

If they do not return a suitable response, ensure that the system environment variables described above are pointing to the correct install directory, and that the *\bin* directories within them are on your PATH system environment variable.

### Developing the front end
When developing the UI, it's best to run it separately to the back end. To do this, use the following commands:
```sh
cd {projectRoot}/webapp
grunt serve
```

This will run the UI on http://localhost:9000

Any changes to the front end code will be watched and re-served immediately for quick development.

### Slots
The "config/slots.txt" file determins the default appointment slots available when using the appointment booking functionality.

The row format of “slots.txt” is:
* Number of days from current date.
* Start Hour (0-23)
* Start Minute (0-59)
* Start Seconds (0-59)
* End Hour (0-23)
* End Minute (0-59)
* End Seconds (0-59)
* Slot Type Code
* Slot Type Description
* Practitioner internal ID
* Slot status (FREE/BUSY)
* Bookable by Gp Connect
* Bookable organization id
* Bookable organization type
* Delivery Channel (0..1 T|V|P) [ T : Telephone, V : Video, P : In-person ] ) 


e.g.:
```
0,9,0,0,9,30,0,408443003,General medical practice,2,FREE,false,1,,T
0,9,30,0,10,0,0,408443003,General medical practice,2,FREE,true,1,,V
0,10,0,0,10,30,0,408443003,General medical practice,2,FREE,true,1,urgent-care,P
```

Currently slots from 09:00 to 17:00 are available to any organisation or type. Slots from 17:00 to 17:50 on day + 1 are either restricted to organisation A20047 (logical id 7) or to Urgent Care/GP Practice or both organisation and type. See the slots.txt for specific details.

### Data clear down
For Appointments and Tasks there is a clear down process which is scheduled using the "datasource.cleardown.cron" property. When the task runs it will delete all GP Connect Demonstrator Tasks previously added. It will also delete all Appointments and remove the currently available slots. It will then refresh the available slots using the "slots.txt" sample data file.

This clear down process will also run each time the application starts.

### Local endpoint lookup configuration
There is the concept of federated practices within the GP Connect Demonstrator, controlled by the “config/providerRouting.json” file. This file contains configuration for the local system ASID, the path the demonstrator will use to call the spine proxy service as well as information for the federated practices if you wish to override the response from the LDAP server or run the demonstrator without connection to an LDAP server.

In the WAR file there is a “defaultPracticeOdsCode.html” which contains the default practice ODS code for the instance of the GP Connect Demonstrator. This ODS code will be used to lookup the practice details within the “providerRouting.json” file.

### Conformance to the GP Connect specification
In some areas the demonstrator does not conform to the GP Connect specification in that it does not pass all of the tests in the specification conformance test suite (https://github.com/nhsconnect/gpconnect-provider-testing/wiki).

Each failing test is documented below by feature area

##### Feature: Search for free slots
| Scenario | Reason for non-conformance |
|---|---|
|I perform a getSchedule with invalid end date and or start date parameters|The Hapi FHIR Java library interprets blank/empty String params as null as opposed to passing the data direct to the demonstrator. Since the values are null the code falls into a different block of logic, one designed to return a 400 instead of a 422 as would be the case if the blank values were passed through uninterpreted .|

##### ENJOY!
