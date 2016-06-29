package edu.eckerd.scripts.google.methods

import com.typesafe.scalalogging.LazyLogging
import edu.eckerd.google.api.services.directory.Directory
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import edu.eckerd.scripts.google.temp.GoogleTables.googleGroups
import edu.eckerd.scripts.google.temp.GoogleTables.GoogleGroupsRow
import edu.eckerd.scripts.google.temp.GoogleTables.gwbAlias
import edu.eckerd.scripts.google.temp.GoogleTables.GwbaliasRow
import edu.eckerd.google.api.services.directory.models.Group
import edu.eckerd.scripts.google.temp.GoogleTables.recoverFromTableCreateFail
import concurrent.{ExecutionContext, Future}
import language.postfixOps
import language.implicitConversions
import scala.collection.parallel.ParSeq

/**
  * Created by davenpcm on 6/24/16.
  */
object GroupsTable extends LazyLogging{

  /**
    * This is the Primary Interface To Completely Update The Google Groups Table, It knows to go and get
    * the records from google from the requested domain, and then deletes Groups which do not exist, and then updates
    * all the existing groups.
    *
    * @param domains These are the domains to grab from google i.e. "eckerd.edu"
    * @param dbConfig This is a database configuration so we know where to write and remove information from
    * @param service The Google Directory Service that can go and get the Users
    * @param ec The Execution context So that we can split threads appropriately for the futures
    * @return A Sequence of Tuples with The Group and the Number of Rows Affected
    */
  def UpdateGoogleGroupsTableComplete(domains: Seq[String])(
    implicit dbConfig: DatabaseConfig[JdbcProfile],
    service: Directory,
    ec: ExecutionContext
  ): Future[Seq[(Group, Int)]] = {

    val currentGroups = for {
      domain <- domains
      result <- service.groups.list(domain)
    } yield result

    for {
      deleted <- DeleteNonExistentGroups(currentGroups)
      result <- UpdateGroupTable(currentGroups)
    } yield result

  }

  /**
    * This query takes a googleGroupID and checks it against the GoogleGroups table. This is the primary Key of the
    * table so while it returns a Sequence there will only ever be a single value if it exists
    *
    * @param id A GoogleGroup ID String
    * @return Once run a single row, however returning as a sequence) or None from googleGroupsTable
    */
  private def queryGoogleGroupsTableById(id: String)(implicit dbConfig: DatabaseConfig[JdbcProfile]) = {
    import dbConfig.driver.api._

    googleGroups.withFilter(_.id === id)
  }

  /**
    * This quest takes the email of the group returned from google and matches it against the listed email in the
    * GWBALIAS table which should be unique.
    *
    * @param email An email, which is trimmed at the @ and compared against the database both moved to upercase
    * @return Once run a single row, however returning as a sequence) or None from GWBALIAS
    */
  private def queryGwbAliasByEmail(email: String)(implicit dbConfig: DatabaseConfig[JdbcProfile]) = {
    import dbConfig.driver.api._

    val filteredEmail = email.takeWhile(_ != '@').toUpperCase
    gwbAlias.withFilter(_.alias.toUpperCase === filteredEmail)
  }

  /**
    * This is the method that creates the appropriate record depending on the three types of possibly existing
    * records. As this is an update, all groups here will exist in google. Then they may or may not exist in both
    * the GoogleGroups table and the previous GWBALIAS Table.
    *
    * If the group already exists in the GoogleGroups Table we use the auto indicators of the already existing group.
    * If the group exists is gwbalias but not in our table we give it the corresponding values from GWBALIAS
    * If it exists but exists in neither table, it was created independently so we tell it the auto-indicator is "N"
    * for No and null/None values for all Auto Indicators
    *
    * @param group A Group Returned from Google
    * @param existingRow The Option of an Existing Row in the GoogleGroups Table
    * @param aliasRow The Option of an Existing Row in the GWBALIAS table
    * @return A GoogleGroups row that best indicates the status of the Group, including any updates that were posted
    *         independently to google since the last run
    */
  private def createGroupsTableRow(group: Group, existingRow: Option[GoogleGroupsRow], aliasRow : Option[GwbaliasRow])
  : GoogleGroupsRow = {
    (group, existingRow, aliasRow) match {
      case (g, None, None) =>
        applyPartialGoogleGroupsRow(g, "N", None, None, None, None)
      case (g, Some(gr), _) =>
        applyPartialGoogleGroupsRow(g, gr.autoIndicator, gr.processIndicator, gr.autoType, gr.autoKey, gr.autoTermCode)
      case (g, None, Some(ar)) =>
        applyPartialGoogleGroupsRow(g, "Y", None, Some(ar.typePkCk), Some(ar.keyPk), Some(ar.termCode))
    }
  }

  /**
    * This is the helper method for the createGroupsTableRow Function. It generalizes so that all the fields that come
    * from the group are automatically input and all of the non automatic fields need to be placed in manually.
    *
    * @param g A Google Group
    * @param autoIndicator A String which indicates whether this groups is to be controlled automatically or is
    *                      manually controlled. This field is mandatory as without it we might delete user created
    *                      groups.
    * @param processIndicator A string which indicates if any action should be done to the group. Very useful for
    *                         asserting that something should be done by another script. It is not used here.
    * @param autoType Type of automatically created group this is, if it was created automatically
    * @param autoKey The keyFor this group insuring there are no 2 groups with the same automatic name
    * @param autoTermCode The term an automatically generated group was created for.
    * @return A Google Groups Row Properly Format
    */
  private def applyPartialGoogleGroupsRow(g: Group,
                                  autoIndicator: String,
                                  processIndicator: Option[String],
                                  autoType: Option[String],
                                  autoKey: Option[String],
                                  autoTermCode: Option[String]
                                 ): GoogleGroupsRow = {
    GoogleGroupsRow(g.id.get,
      autoIndicator,
      g.name, g.email, g.directMemberCount.get, g.description.map(_.take(254).replaceAll("/n", " ")),
      processIndicator,
      autoType,
      autoKey,
      autoTermCode
    )
  }

  /**
    * This Function is the aggregate that updates the database. It updates every record with its current status
    * from google. It gets records from google, and then a corresponding record from both the current table and
    * gwbalias in order to make the updated record correctly. Finally it runs the insertOrUpdate which utilizes
    * the Primary Key on this table to Update the record.
    *
    * @param currentGroups Current Google Groups
    * @param dbConfig The Database to Update
    * @param ec The Execution Context To Take Threads From
    * @return A Future of All Groups and an Int corresponding to Records effected by that group,
    *         which should always be 1
    */
  def UpdateGroupTable( currentGroups: Seq[Group] )(
    implicit dbConfig: DatabaseConfig[JdbcProfile], ec: ExecutionContext): Future[Seq[(Group, Int)]] = {
    import dbConfig.driver.api._
    val db : JdbcProfile#Backend#Database = dbConfig.db

    val UpdatedRows : ParSeq[Future[(Group, Int)]] = for {
      g <- currentGroups.par
    } yield for {
      gr <- db.run(queryGoogleGroupsTableById(g.id.get).result.headOption)
      ar <- db.run(queryGwbAliasByEmail(g.email).result.headOption)
      rowsAffected <- db.run(googleGroups.insertOrUpdate(createGroupsTableRow(g, gr, ar)))
    } yield {
      logger.debug(s"Updated Group ${createGroupsTableRow(g, gr, ar)}")
      (g, rowsAffected)
    }
    Future.sequence(UpdatedRows.seq)
  }

  /**
    * This function Deletes All Groups from the Database That Exist in the Database but not in Google. It queries
    * the database then filters out any record with an id consistent with google.
    *
    * @param currentGoogleGroups Current Google Groups
    * @param dbConfig Database to Delete From
    * @param ec The execution context to take threads from.
    * @return A Future Sequence of Int
    */
  def DeleteNonExistentGroups(currentGoogleGroups: Seq[Group])
                             (implicit dbConfig: DatabaseConfig[JdbcProfile],
                              ec: ExecutionContext): Future[Seq[(GoogleGroupsRow, Int)]] = {
    import dbConfig.driver.api._
    val db : JdbcProfile#Backend#Database = dbConfig.db

    val currentGoogleGroupsSet = currentGoogleGroups.map(_.id.get).toSet
    db.run(googleGroups.result).flatMap { groups =>
      Future.sequence{
        groups.withFilter(group => !currentGoogleGroupsSet.contains(group.id))
          .map { group =>
            logger.debug(s"Deleting Group From DB - $group")
            db.run(queryGoogleGroupsTableById(group.id).delete).map((group, _))
          }
      }
    }
  }

  /**
    * This is the function the can create the google Groups Table.
    *
    * @param dbConfig The Database to Create The Table In
    * @param ec The Execution Context to split into the Future
    * @return A Future of Unit
    */
  def createGoogleGroupsTable(implicit dbConfig: DatabaseConfig[JdbcProfile], ec: ExecutionContext): Future[Unit] = {
    import dbConfig.driver.api._
    val db = dbConfig.db
    val tableQuery = googleGroups

    val action = tableQuery.schema.create
    db.run(action) recoverWith recoverFromTableCreateFail
  }

  /**
    * This Drops the Google Groups Table. Hopefully this won't need to be used.
    *
    * @param dbConfig Where to get rid of this Table from
    * @param ec An Execution Context to Split Into the Future
    * @return A Future of Unit
    */
  def dropGoogleGroupsTable(implicit dbConfig: DatabaseConfig[JdbcProfile], ec: ExecutionContext): Future[Unit] = {
    import dbConfig.driver.api._
    val db = dbConfig.db
    val tableQuery = googleGroups

    val action = tableQuery.schema.drop
    db.run(action)
  }

}
