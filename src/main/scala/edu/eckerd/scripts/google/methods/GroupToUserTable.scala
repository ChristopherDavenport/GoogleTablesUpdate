package edu.eckerd.scripts.google.methods

import java.sql.SQLSyntaxErrorException

import com.sun.net.httpserver.Authenticator.Success
import com.typesafe.scalalogging.LazyLogging
import edu.eckerd.scripts.google.temp.GoogleTables.googleGroupToUser
import edu.eckerd.scripts.google.temp.GoogleTables.GoogleGroupToUserRow
import edu.eckerd.scripts.google.temp.GoogleTables.googleGroupToUserByPK
import edu.eckerd.google.api.services.directory.Directory
import edu.eckerd.google.api.services.directory.models.Member
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile

import concurrent.{ExecutionContext, Future}
import language.postfixOps
import language.implicitConversions
import scala.util.Try
import scala.util.control.NonFatal

/**
  * Created by davenpcm on 6/26/16.
  */
object GroupToUserTable extends LazyLogging{



  /**
    * This updates the Google Group To User Table for a given domain
    * @param domains The domains to retreive from Google
    * @param dbConfig The database configuration to Update the Table In
    * @param service The Google Directory to Retreive Users and Members From
    * @param ec The Execution Context To Split Into For Futures
    * @return A Sequence of Each Row and the Number of Rows Effected, which Should always be 1
    */
  def UpdateGoogleGroupToUserTable(domains: Seq[String])(
    implicit dbConfig: DatabaseConfig[JdbcProfile],
    service: Directory,
    ec: ExecutionContext
  ): Future[Seq[(Member, Int)]] = {

    Future.sequence{
      val perform = for {
        domain <- domains.par
        group <- service.groups.list(domain)
        member <- service.members.list(group.email).par
      } yield {
        val GeneratedRow = GoogleGroupToUserRow(group.id.get, member.id.get, "N", member.role, member.memberType)
        for {
          rowFromDB <- getMemberFromDB(group.id.get, member.id.get)
          result <- insertOrUpdateMemberInDB(rowFromDB, GeneratedRow)
        } yield {
          logger.debug(s"Row From DB For $member - $rowFromDB")
          (member, result)
        }
      }
      perform.seq
    }
  }

  private def insertOrUpdateMemberInDB(existingRow: Option[GoogleGroupToUserRow], generatedRow: GoogleGroupToUserRow)
                                      (implicit dbConfig: DatabaseConfig[JdbcProfile],
                                       ec: ExecutionContext): Future[Int] = existingRow match {
//    case Some(row) =>
//      val updatedRow = GoogleGroupToUserRow(
//        generatedRow.groupId,
//        generatedRow.userID,
//        row.autoIndicator,
//        generatedRow.memberRole,
//        generatedRow.memberType,
//        row.processIndicator
//      )
//      if (){
//        Future.successful(0)
//      } else {
//        updateMemberInDB(updatedRow)
//      }
    case None =>
      addMemberToDB(generatedRow)
    case _ => Future.successful(0)
  }

  /**
    * This updates a member in the Database
    * @param member A Member as the GoogleGroupToUserRow
    * @param dbConfig The Database to Update
    * @param ec The execution context to split into the futures
    * @return An Int Representing The Number of Rows Affected
    */
  private def updateMemberInDB(member: GoogleGroupToUserRow)
                              (implicit dbConfig: DatabaseConfig[JdbcProfile],
                               ec: ExecutionContext): Future[Int] = {
    import dbConfig.driver.api._
    val db : JdbcProfile#Backend#Database = dbConfig.db

    logger.debug(s"Updated Member In DB - $member")
    db.run(
        googleGroupToUserByPK(member.groupId, member.userID).update(member)
    ) recoverWith {
      case e: Exception => Future.successful(0)
    }
  }



  /**
    * This adds a new member to the database.
    * @param member A Member as the GoogleGroupToUserRow
    * @param dbConfig The Database to Update
    * @param ec The execution context to split into the futures
    * @return An Int Representing The Number of Rows Affected
    */
  private def addMemberToDB(member: GoogleGroupToUserRow)
                           (implicit dbConfig: DatabaseConfig[JdbcProfile], ec: ExecutionContext): Future[Int] = {
    import dbConfig.driver.api._
    val db : JdbcProfile#Backend#Database = dbConfig.db

    logger.debug(s"Adding Member To DB - $member")

    db.run(googleGroupToUser += member) recoverWith {
      case sqlErr: SQLSyntaxErrorException =>
        logger.error(s"SQL Syntax Error - ${sqlErr.getLocalizedMessage}")
        Future.successful(0)
      case sqlIntegrityConstraint =>
        logger.error(s"$member - ${sqlIntegrityConstraint.getMessage.takeWhile(_ != '\n')}")
        Future.successful(0)
      case e: Exception =>
        logger.error(s"Error Encountered With - $member", e)
        Future.successful(0)
    }
  }

  /**
    * This gets a member from the database
    * @param member A Member as the GoogleGroupToUserRow
    * @param dbConfig The Database to Update
    * @param ec The execution context to split into the futures
    * @return An Option of the Row Returned if it Exists
    */
  private def getMemberFromDB(member: GoogleGroupToUserRow)
                             (implicit dbConfig: DatabaseConfig[JdbcProfile], ec: ExecutionContext)
  : Future[Option[GoogleGroupToUserRow]] = {

    getMemberFromDB(member.groupId, member.userID)
  }

  /**
    *
    * @param groupId The Group ID
    * @param memberId The Member ID
    * @param dbConfig The Database to Update
    * @param ec The execution context to split into the futures
    * @return An Option of the Row Returned if it Exists
    */
  private def getMemberFromDB(groupId: String, memberId: String)
                             (implicit dbConfig: DatabaseConfig[JdbcProfile], ec: ExecutionContext)
  : Future[Option[GoogleGroupToUserRow]] = {
    import dbConfig.driver.api._
    val db : JdbcProfile#Backend#Database = dbConfig.db

    db.run(
      googleGroupToUserByPK(groupId, memberId).result.headOption
    )
  }

  /**
    * This is the function the can create the google GroupToUser Table.
    *
    * @param dbConfig The Database to Create The Table In
    * @param ec The Execution Context to split into the Future
    * @return A Future of Unit
    */
  def createGoogleGroupToUserTable(implicit dbConfig: DatabaseConfig[JdbcProfile], ec: ExecutionContext): Future[Unit] = {
    import dbConfig.driver.api._
    val db = dbConfig.db

    val action = googleGroupToUser.schema.create
    db.run(action)
  }

  /**
    * This Drops the Google GroupToUser Table. Hopefully this won't need to be used.
    * @param dbConfig Where to get rid of this Table from
    * @param ec An Execution Context to Split Into the Future
    * @return A Future of Unit
    */
  def dropGoogleGroupToUserTable(implicit dbConfig: DatabaseConfig[JdbcProfile], ec: ExecutionContext): Future[Unit] = {
    import dbConfig.driver.api._
    val db = dbConfig.db

    val action = googleGroupToUser.schema.drop
    db.run(action)
  }

}
