package edu.eckerd.scripts.google

import edu.eckerd.google.api.services.directory.Directory
import edu.eckerd.scripts.google.temp.GoogleTables
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import edu.eckerd.scripts.google.methods.GroupsTable._
import edu.eckerd.scripts.google.methods.GroupToUserTable._
import edu.eckerd.scripts.google.methods.UsersTable._


/**
  * Created by davenpcm on 6/24/16.
  * This is a simple script for the maintenance of a set of Google Tables that correspond to your existing Google
  * Users and Groups. The commented lines are for first run to generate th necessary tables.
  *
  * If you are having errors they are likely related to your application.conf or the presence of the Oracle JDBC jar.
  * On Compile Make Sure You have a .lib folder that contains ojdbc_.jar in this case I am using 7.
  *
  */
object UpdateGoogleDatabaseTables extends App with GoogleTables {
  implicit val dbConfig: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig("oracle")
  implicit val profile = dbConfig.driver
  implicit val directory = Directory()
  val domain = "eckerd.edu"

  val doIt = for {
    createUsers <- createGoogleUsersTable
    usersUpdate <- updateGoogleUsersTable(domain)
    createGroups <- createGoogleGroupsTable
    groupsUpdate <- UpdateGoogleGroupsTableComplete(domain)
    createGroupToUser <- createGoogleGroupToUserTable
    groupToUserUpdate <- UpdateGoogleGroupToUserTable(domain)
  } yield (usersUpdate, groupsUpdate, groupToUserUpdate)

  val result = Await.result(doIt, Duration.Inf)

}
