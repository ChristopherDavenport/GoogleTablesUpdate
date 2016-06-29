package edu.eckerd.scripts.google.methods

import com.typesafe.scalalogging.LazyLogging
import edu.eckerd.google.api.services.directory.Directory
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import edu.eckerd.scripts.google.temp.GoogleTables.googleUsers
import edu.eckerd.scripts.google.temp.GoogleTables.recoverFromTableCreateFail
import scala.concurrent.{ExecutionContext, Future}
import edu.eckerd.google.api.services.directory.models.User
/**
  * Created by davenpcm on 6/24/16.
  */
object UsersTable extends LazyLogging {

  def updateGoogleUsersTable(domains: Seq[String])(implicit dbConfig: DatabaseConfig[JdbcProfile], directory: Directory, ec : ExecutionContext):
  Future[Seq[(User,Int)]] = {

    import dbConfig.driver.api._
    val db = dbConfig.db
    logger.info("Starting Users Table Update")

    val UpdateUsersTable = for {
      domain <- domains
      user <- directory.users.list(domain)
    } yield for {
      result <- db.run(googleUsers.insertOrUpdate(user))
    } yield (user, result)

    Future.sequence(UpdateUsersTable)
  }

  def createGoogleUsersTable(implicit dbConfig: DatabaseConfig[JdbcProfile], ec: ExecutionContext): Future[Unit] = {
    import dbConfig.driver.api._
    val db = dbConfig.db
    val tableQuery = googleUsers

    logger.info("Attempting to Create Google Users Table")

    val action = tableQuery.schema.create
    db.run(action) recoverWith recoverFromTableCreateFail
  }



  def dropGoogleUsersTable(implicit dbConfig: DatabaseConfig[JdbcProfile], ec: ExecutionContext): Future[Unit] = {
    import dbConfig.driver.api._
    val db = dbConfig.db
    val tableQuery = googleUsers

    val action = tableQuery.schema.drop
    db.run(action)
  }

}
