Query Parser
=========

This Plugin is still a work in progress.

The intention is to consume /DBCC/regression/target/jbehave/logs/container.log which is produced each build and contains all the jdbc sql requests made in the
build and output a report of all the sql requests made.

The plugin consists of two logical parts, the core plugin which consumes the logfile and outputs a two json files and the webapp which
consumes the two json files and outputs an html report.  One json file is the list of every query request made during the build,
the URL connection string, time of query execution, # of results and the actual results themselves.  The second output json file
is the summary of all the Schemas, Tables, and Columns necessary to create a mock DB for the build to point at instead of the
actual production or pre-production database.

Publishing the WebApp: When the jenkins build completes and the json files have been published the plugin will also publish the
webapp itself, with the containing json files.

TODO:
Consuming and writing large text currently causes issues.  The SQL logs are typically above 35 MB in size and the mechanism
    with which files are written need to be redesigned.<br>
TODO:
Completion of WebApp.  The work left for this is minimal, some parsing of the strings needs to be done and simply thrown into a table.<br>
TODO:
Fixing a bug where plugin configs do not persist on reopening the job config<br>
TODO:
> Implementing the reading of the webapp and publishing of the webapp through the sqlparser class<br>
TODO:
> Integration of SQLParser class within the Query Parser plugin<br>

