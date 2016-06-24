package edu.eckerd.scripts.google.methods

import edu.eckerd.google.api.services.directory.Directory
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import edu.eckerd.scripts.google.temp.GoogleTables.googleGroups
import edu.eckerd.scripts.google.temp.GoogleTables.GoogleGroupsRow
import edu.eckerd.scripts.google.temp.GoogleTables.gwbAlias
import edu.eckerd.scripts.google.temp.GoogleTables.GwbaliasRow
import edu.eckerd.google.api.services.directory.models.Group

import concurrent.{ExecutionContext, Future}
import language.postfixOps
import scala.collection.parallel.ParSeq
/**
  * Created by davenpcm on 6/24/16.
  */
trait GroupsTable {

  def UpdateGoogleGroupsTable(implicit dbConfig: DatabaseConfig[JdbcProfile], service: Directory, ec: ExecutionContext
                             ): Future[Seq[(Group, Int)]] = {
    import dbConfig.driver.api._
    val db = dbConfig.db

    /**
      * This query takes a googleGroupID and checks it against the GoogleGroups table. This is the primary Key of the
      * table so while it returns a Sequence there will only ever be a single value if it exists
      *
      * @param id A GoogleGroup ID String
      * @return Once run a single row, however returning as a sequence) or None from googleGroupsTable
      */
    def queryGoogleGroupsTableById(id: String) = googleGroups.withFilter(_.id === id)

    /**
      * This quest takes the email of the group returned from google and matches it against the listed email in the
      * GWBALIAS table which should be unique.
      *
      * @param email An email, which is trimmed at the @ and compared against the database both moved to upercase
      * @return Once run a single row, however returning as a sequence) or None from GWBALIAS
      */
    def queryGwbAliasByEmail(email: String) = {
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
    def createGroupsTableRow(group: Group, existingRow: Option[GoogleGroupsRow], aliasRow : Option[GwbaliasRow])
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
    def applyPartialGoogleGroupsRow(g: Group,
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


    val currentGroups: ParSeq[Group] = service.groups.list("eckerd.edu").par

    val UpdatedRows : ParSeq[Future[(Group, Int)]] = for {
        g <- currentGroups
      } yield for {
        gr <- db.run(queryGoogleGroupsTableById(g.id.get).result.headOption)
        ar <- db.run(queryGwbAliasByEmail(g.email).result.headOption)
        rowsAffected <- db.run(googleGroups.insertOrUpdate(createGroupsTableRow(g, gr, ar)))
      } yield (g, rowsAffected)

    Future.sequence(UpdatedRows.seq)

  }

  def createGoogleGroupsTable(implicit dbConfig: DatabaseConfig[JdbcProfile], ec: ExecutionContext): Future[Unit] = {
    import dbConfig.driver.api._
    val db = dbConfig.db
    val tableQuery = googleGroups

    val action = tableQuery.schema.create
    db.run(action)
  }

  def dropGoogleGroupsTable(implicit dbConfig: DatabaseConfig[JdbcProfile], ec: ExecutionContext): Future[Unit] = {
    import dbConfig.driver.api._
    val db = dbConfig.db
    val tableQuery = googleGroups

    val action = tableQuery.schema.drop
    db.run(action)
  }

}
