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
  */
object UpdateGoogleDatabaseTables extends App with GoogleTables with  GroupsTable with UsersTable with GroupToUserTable{
  implicit val dbConfig: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig("oracle")
  implicit val profile = dbConfig.driver
  implicit val directory = Directory()

  val doIt = for {
    createUsers <- createGoogleUsersTable
    usersUpdate <- updateGoogleUsersTable
    createGroups <- createGoogleGroupsTable
    groupsUpdate <- UpdateGoogleGroupsTableComplete("eckerd.edu")
    createGroupToUser <- createGoogleGroupToUserTable
    groupToUserUpdate <- UpdateGoogleGroupToUserTable("eckerd.edu")
  } yield (usersUpdate, groupsUpdate, groupToUserUpdate)

  val result = Await.result(doIt, Duration.Inf)

}
