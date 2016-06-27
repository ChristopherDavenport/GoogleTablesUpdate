package edu.eckerd.scripts.google.methods

import edu.eckerd.scripts.google.temp.GoogleTables.googleGroupToUser
import edu.eckerd.scripts.google.temp.GoogleTables.GoogleGroupToUserRow
import edu.eckerd.google.api.services.directory.Directory
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import concurrent.{ExecutionContext, Future}
import language.postfixOps
import language.implicitConversions

/**
  * Created by davenpcm on 6/26/16.
  */
trait GroupToUserTable {

  /**
    * This updates the Google Group To User Table for a given domain
    * @param domain The domain to retreive from Google
    * @param dbConfig The database configuration to Update the Table In
    * @param service The Google Directory to Retreive Users and Members From
    * @param ec The Execution Context To Split Into For Futures
    * @return A Sequence of Each Row and the Number of Rows Effected, which Should always be 1
    */
  def UpdateGoogleGroupToUserTable(domain: String)(
    implicit dbConfig: DatabaseConfig[JdbcProfile],
    service: Directory,
    ec: ExecutionContext
  ): Future[Seq[(GoogleGroupToUserRow, Int)]] = {

    Future.sequence{
      for {
        group <- service.groups.list(domain)
        member <- service.members.list(group.email)
      } yield {
        val GeneratedRow = GoogleGroupToUserRow(group.id.get, member.id.get, "N", member.role, member.memberType)
        for {
          rowFromDB <- getMemberFromDB(GeneratedRow) if rowFromDB.isEmpty
          result <- addMemberToDB(GeneratedRow)
        } yield (GeneratedRow, result)
      }
    }
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

    val r = for {
      rec <- googleGroupToUser if rec.groupId === member.groupId && rec.userID === member.userID
    } yield rec

    db.run(r.update(member))
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

    db.run(googleGroupToUser += member)
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
      googleGroupToUser.withFilter(rec =>
        rec.groupId === groupId && rec.userID === memberId
      ).result.headOption
    )
  }

  /**
    * This is the function the can create the google GroupToUser Table.
    *
    * @param dbConfig The Database to Create The Table In
    * @param ec The Execution Context to split into the Future
    * @return A Future of Unit
    */
  def createGoogleGroupsTable(implicit dbConfig: DatabaseConfig[JdbcProfile], ec: ExecutionContext): Future[Unit] = {
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
  def dropGoogleGroupsTable(implicit dbConfig: DatabaseConfig[JdbcProfile], ec: ExecutionContext): Future[Unit] = {
    import dbConfig.driver.api._
    val db = dbConfig.db

    val action = googleGroupToUser.schema.drop
    db.run(action)
  }

}
