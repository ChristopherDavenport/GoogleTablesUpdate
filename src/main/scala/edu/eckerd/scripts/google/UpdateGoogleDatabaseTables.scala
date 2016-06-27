package edu.eckerd.scripts.google

import edu.eckerd.google.api.services.directory.Directory
import edu.eckerd.scripts.google.methods.{GroupsTable, UsersTable, GroupToUserTable}
import edu.eckerd.scripts.google.temp.GoogleTables
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by davenpcm on 6/24/16.
  * This is a simple script for the maintenance of a set of Google Tables that correspond to your existing Google
  * Users and Groups. The commented lines are for first run to generate th necessary tables.
  *
  * If you are having errors they are likely related to your application.conf or the presence of the Oracle JDBC jar.
  * On Compile Make Sure You have a .lib folder that contains ojdbc_.jar in this case I am using 7.
  *
  */
object UpdateGoogleDatabaseTables extends App with GoogleTables with  GroupsTable with UsersTable with GroupToUserTable{
  implicit val dbConfig: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig("oracle")
  implicit val profile = dbConfig.driver
  implicit val directory = Directory()

  val doIt = for {
//    createUsers <- createGoogleUsersTable
    usersUpdate <- updateGoogleUsersTable
//    createGroups <- createGoogleGroupsTable
    groupsUpdate <- UpdateGoogleGroupsTableComplete("eckerd.edu")
//    createGroupToUser <- createGoogleGroupToUserTable
    groupToUserUpdate <- UpdateGoogleGroupToUserTable("eckerd.edu")
  } yield (usersUpdate, groupsUpdate, groupToUserUpdate)

  val result = Await.result(doIt, Duration.Inf)

}
