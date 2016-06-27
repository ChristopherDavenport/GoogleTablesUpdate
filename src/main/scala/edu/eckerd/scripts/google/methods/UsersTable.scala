package edu.eckerd.scripts.google.methods

import java.sql.SQLSyntaxErrorException

import edu.eckerd.google.api.services.directory.Directory
import edu.eckerd.google.api.services.directory.models.User
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import edu.eckerd.scripts.google.temp.GoogleTables.googleUsers

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * Created by davenpcm on 6/24/16.
  */
object UsersTable {

  def updateGoogleUsersTable(domain: String)(implicit dbConfig: DatabaseConfig[JdbcProfile], directory: Directory, ec : ExecutionContext):
  Future[List[Int]] = {

    import dbConfig.driver.api._
    val db = dbConfig.db

    val UpdateUsersTable = for {
      user <- directory.users.list(domain)
    } yield for {
      result <- db.run(googleUsers.insertOrUpdate(user))
    } yield result

    Future.sequence(UpdateUsersTable)

  }

  def createGoogleUsersTable(implicit dbConfig: DatabaseConfig[JdbcProfile], ec: ExecutionContext): Future[Unit] = {
    import dbConfig.driver.api._
    val db = dbConfig.db
    val tableQuery = googleUsers

    val action = tableQuery.schema.create
    db.run(action)
  }

  def dropGoogleUsersTable(implicit dbConfig: DatabaseConfig[JdbcProfile], ec: ExecutionContext): Future[Unit] = {
    import dbConfig.driver.api._
    val db = dbConfig.db
    val tableQuery = googleUsers

    val action = tableQuery.schema.drop
    db.run(action)
  }

}
