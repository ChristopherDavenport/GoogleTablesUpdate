
name := "GoogleTablesUpdate"
version := "0.1.0"
maintainer := "Christopher Davenport <ChristopherDavenport@outlook.com>"
packageSummary := "This creates tables and maintains a database consistent with your organization in google"
packageDescription := "Scala project that reads from a file location with your google credential to create" +
"a table structure to represent your google organizations Users, Groups and Group Members"

scalaVersion := "2.11.8"

resolvers += Resolver.sonatypeRepo("snapshots")
resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/maven-releases/"

val slickV = "3.1.0"

libraryDependencies ++= List(
  "org.scalatest" %% "scalatest" % "2.2.6" % "test",
  "edu.eckerd" %% "google-api-scala" % "0.0.1-SNAPSHOT",
  "com.typesafe.slick" %% "slick" % slickV,
  "com.typesafe.slick" %% "slick-extensions" % slickV ,
  "com.typesafe.slick" %% "slick-hikaricp" % slickV,
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0"
)

unmanagedBase := baseDirectory.value / ".lib"

mainClass in Compile := Some("edu.eckerd.scripts.google.UpdateGoogleDatabaseTables")

mappings in Universal ++= Seq(
  sourceDirectory.value / "main" / "resources" / "application.conf" -> "conf/application.conf",
  sourceDirectory.value / "main" / "resources" / "logback.xml" -> "conf/logback.xml"
)

rpmVendor := "Eckerd College"
rpmLicense := Some("Apache 2.0")


enablePlugins(JavaAppPackaging)