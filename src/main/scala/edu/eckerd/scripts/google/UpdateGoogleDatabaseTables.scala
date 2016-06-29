package edu.eckerd.scripts.google

import com.typesafe.scalalogging.LazyLogging
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
object UpdateGoogleDatabaseTables extends App with GoogleTables with LazyLogging {
  logger.info("Starting Update of Google Tables")
  implicit val dbConfig: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig("oracle")
  implicit val profile = dbConfig.driver
  implicit val directory = Directory()
  val domains = Seq("eckerd.edu")

  val doIt = for {
    createUsers <- createGoogleUsersTable
    usersUpdate <- updateGoogleUsersTable(domains)
    createGroups <- createGoogleGroupsTable
    groupsUpdate <- UpdateGoogleGroupsTableComplete(domains)
    createGroupToUser <- createGoogleGroupToUserTable
    groupToUserUpdate <- UpdateGoogleGroupToUserTable(domains)
  } yield (usersUpdate, groupsUpdate, groupToUserUpdate)

  val result = Await.result(doIt, Duration.Inf)

  result._1.foreach(results => logger.debug(s"Complete: User - ${results._1} - ${results._2}"))
  result._2.foreach(results => logger.debug(s"Complete: Group - ${results._1} - ${results._2}"))
  result._3.foreach(results => logger.debug(s"Complete: GroupToUser - ${results._1} - ${results._2}"))
  logger.info("Update of Google Tables Complete")
}
