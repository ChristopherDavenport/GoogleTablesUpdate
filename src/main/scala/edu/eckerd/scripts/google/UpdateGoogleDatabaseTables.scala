package edu.eckerd.scripts.google

import edu.eckerd.google.api.services.directory.Directory
import edu.eckerd.scripts.google.methods.{GroupsTable, UsersTable}
import edu.eckerd.scripts.google.temp.GoogleTables
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by davenpcm on 6/24/16.
  */
object UpdateGoogleDatabaseTables extends App with GoogleTables with  GroupsTable with UsersTable {
  implicit val dbConfig: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig("oracle")
  import dbConfig.driver.api._
  implicit val profile = dbConfig.driver
  implicit val directory = Directory()

//  val doIt = for {
////    createUsers <- createGoogleUsersTable
//    usersUpdate <- updateGoogleUsersTable
//    createGroups <- createGoogleGroupsTable
//    groupsUpdate <- UpdateGoogleGroupsTable
//  } yield (usersUpdate, groupsUpdate)
//
//  val result = Await.result(doIt, Duration.Inf)
//  result._1.foreach(t => println(t))
//  result._2.foreach(g => println(g._1.name, g._2))


  Await.result(dbConfig.db.run(googleGroups.result), Duration.Inf).foreach(println)


}
