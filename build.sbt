enablePlugins(JavaAppPackaging)
name := "GoogleTablesUpdate"

version := "1.0"

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
// Change this to another test framework if you prefer

mainClass in Compile := Some("edu.eckerd.scripts.google.UpdateGoogleDatabaseTables")

mappings in Universal += {
  sourceDirectory.value / "main" / "resources" / "application.conf" -> "conf/application.conf"
}