package edu.eckerd.scripts.google.methods

import java.sql.{SQLIntegrityConstraintViolationException, SQLSyntaxErrorException}

import com.typesafe.scalalogging.LazyLogging
import edu.eckerd.scripts.google.temp.GoogleTables.googleGroupToUser
import edu.eckerd.scripts.google.temp.GoogleTables.GoogleGroupToUserRow
import edu.eckerd.scripts.google.temp.GoogleTables.googleGroupToUserByPK
import edu.eckerd.google.api.services.directory.Directory
import edu.eckerd.google.api.services.directory.models.Member
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import edu.eckerd.scripts.google.temp.GoogleTables.recoverFromTableCreateFail
import edu.eckerd.google.api.services.directory.models.Group
import concurrent.{ExecutionContext, Future}
import language.postfixOps
import language.implicitConversions

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
  def UpdateGoogleGroupToUserTable(domains: List[String])(
    implicit dbConfig: DatabaseConfig[JdbcProfile],
    service: Directory,
    ec: ExecutionContext
  ): Future[Seq[(Member, Int)]] = {

    logger.info("Starting Google Members Update")

    val f = Future.sequence {
      for {
        domain <- domains
        group <- service.groups.list(domain)
      } yield {
        val members = groupMembers(group)
        for {
          _ <- deleteNonExistentMembers(group, members)
          result <- Future.sequence {
            for {
              member <- members
            } yield {
              val GeneratedRow = GoogleGroupToUserRow(
                group.id.get, member.id.get, member.email, "N", member.role, member.memberType
              )
              for {
                rowFromDB <- getMemberFromDB(group.id.get, member.id.get)
                result <- insertOrUpdateMemberInDB(rowFromDB, GeneratedRow)
              } yield {
                logger.debug(s"Row From DB For $member - $rowFromDB")
                (member, result)
              }
            }
          }
        } yield result
      }
    }

    f.map(_.flatten)

  }

  /**
    * Get all Members of a Group
    * @param group The Group
    * @param service The service to get members from
    * @return A List of all Members of that Group
    */
  def groupMembers(group: Group)(implicit service: Directory): List[Member] =
    for {member <- service.members.list(group.email)} yield member

  /**
    * Takes a Group and a List of All Its Members and then checks that against what is currently in the database.
    * It then deletes any records that are in the database but do not exist in Google.
    * @param group The group to look for
    * @param members The members of the group
    * @param dbConfig The database coinfiguration
    * @param ec The execution context to pull futures from
    * @return An Integer Of the Number Of Rows Deleted
    */
  def deleteNonExistentMembers(group: Group, members: List[Member])
                             (implicit dbConfig: DatabaseConfig[JdbcProfile],
                               ec: ExecutionContext
                             ): Future[Int] = {
    import dbConfig.driver.api._
    val db = dbConfig.db

    val memberIdsOpt = members.map(_.id)
    val memberIds = memberIdsOpt.map(_.get)

    val q = for {
      result <- googleGroupToUser
      if result.groupId === group.id.get && ! result.userID.inSetBind(memberIds)
    } yield result

    db.run(q.delete)
  }

  /**
    * This is the functionality that extends that if it exists it is returned as valid or if it is not it adds the
    * new member to the database
    * @param existingRow The row returned
    * @param generatedRow The row created
    * @param dbConfig The database configuration
    * @param ec The execution context
    * @return An Int corresponding to the rows created should be 0 or 1
    */
  private def insertOrUpdateMemberInDB(existingRow: Option[GoogleGroupToUserRow], generatedRow: GoogleGroupToUserRow)
                                      (implicit dbConfig: DatabaseConfig[JdbcProfile],
                                       ec: ExecutionContext): Future[Int] = existingRow match {
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
      case sqlIntegrityConstraint : SQLIntegrityConstraintViolationException =>
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
    logger.info("Attempting to Create Google Members Table")
    val action = googleGroupToUser.schema.create
    db.run(action) recoverWith recoverFromTableCreateFail
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
