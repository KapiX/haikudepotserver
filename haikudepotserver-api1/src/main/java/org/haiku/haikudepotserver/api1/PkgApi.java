/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.googlecode.jsonrpc4j.JsonRpcService;
import org.haiku.haikudepotserver.api1.model.miscellaneous.GetRuntimeInformationRequest;
import org.haiku.haikudepotserver.api1.model.pkg.*;
import org.haiku.haikudepotserver.api1.support.BadPkgIconException;
import org.haiku.haikudepotserver.api1.support.LimitExceededException;
import org.haiku.haikudepotserver.api1.support.ObjectNotFoundException;

/**
 * <p>This API is for access to packages and package versions.</p>
 */

@JsonRpcService("/api/v1/pkg")
public interface PkgApi {

    public final static Integer GETBULKPKG_LIMIT = 50;

    /**
     * <p>This method will ensure that the categories configured on the nominated package are as per the list of
     * packages.</p>
     */

    UpdatePkgCategoriesResult updatePkgCategories(UpdatePkgCategoriesRequest updatePkgCategoriesRequest) throws ObjectNotFoundException;

    /**
     * <p>This method can be invoked to get a list of all of the packages that match some search critera in the
     * request.</p>
     */

    SearchPkgsResult searchPkgs(SearchPkgsRequest request) throws ObjectNotFoundException;

    /**
     * <p>This method will return a package and the specified versions.  It will throw an
     * {@link ObjectNotFoundException} if the package was not able to be located.</p>
     */

    GetPkgResult getPkg(GetPkgRequest request) throws ObjectNotFoundException;

    /**
     * <p>Returns a list of meta-data regarding the icon data related to the pkg.  This does not contain the icon
     * data itself; just the meta data.</p>
     */

    GetPkgIconsResult getPkgIcons(GetPkgIconsRequest request) throws ObjectNotFoundException;

    /**
     * <p>This request will configure the icons for the package nominated.  Note that only certain configurations of
     * icon data may be acceptable; for example, it will require a 16x16px and 32x32px bitmap image.</p>
     */

    ConfigurePkgIconResult configurePkgIcon(ConfigurePkgIconRequest request) throws ObjectNotFoundException, BadPkgIconException;

    /**
     * <p>This request will remove any icons from the package.</p>
     */

    RemovePkgIconResult removePkgIcon(RemovePkgIconRequest request) throws ObjectNotFoundException;

    /**
     * <p>This method will get the details of a screenshot image.</p>
     */

    GetPkgScreenshotResult getPkgScreenshot(GetPkgScreenshotRequest request) throws ObjectNotFoundException;

    /**
     * <p>This method will return an ordered list of the screenshots that are available for this package.  It will
     * throw an {@link ObjectNotFoundException} in the case where the
     * nominated package is not able to be found.</p>
     */

    GetPkgScreenshotsResult getPkgScreenshots(GetPkgScreenshotsRequest getPkgScreenshotsRequest) throws ObjectNotFoundException;

    /**
     * <p>This method will remove the nominated screenshot from the package.  If the screenshot is not able to be
     * found using the code supplied, the method will throw an instance of
     * {@link ObjectNotFoundException}.</p>
     */

    RemovePkgScreenshotResult removePkgScreenshot(RemovePkgScreenshotRequest removePkgScreenshotRequest) throws ObjectNotFoundException;

    /**
     * <p>This method will reorder the screenshots related to the nominated package.  If any of the screenshots are
     * not accounted for, they will be ordered at the end in an indeterminate manner.  If the package is not able to be
     * found given the name supplied, an instance of
     * {@link ObjectNotFoundException} will be thrown.</p>
     */

    ReorderPkgScreenshotsResult reorderPkgScreenshots(ReorderPkgScreenshotsRequest reorderPkgScreenshotsRequest) throws ObjectNotFoundException;

    /**
     * <p>This method will update the localizations supplied for the package identified in the request.  In order to
     * remove a localization for a given language, supply the localization data for that language as NULL.</p>
     */

    UpdatePkgLocalizationResult updatePkgLocalization(UpdatePkgLocalizationRequest updatePkgLocalizationRequest) throws ObjectNotFoundException;

    /**
     * <p>This method will return all of the localizations that are specific to the package identified in the request.</p>
     */

    GetPkgLocalizationsResult getPkgLocalizations(GetPkgLocalizationsRequest getPkgLocalizationsRequest) throws ObjectNotFoundException;

    /**
     * <p>This method will return the package version localizations for the nominated package.  It will return
     * data for the latest package version in the architecture nominated in the request.  The architecture is
     * required.  If a "major" value is supplied in the request then it is assumed that the request is in the
     * context of a specific package version rather than the latest.</p>
     */

    GetPkgVersionLocalizationsResult getPkgVersionLocalizations(GetPkgVersionLocalizationsRequest getPkgVersionLocalizationsRequest) throws ObjectNotFoundException;

    /**
     * <p>This method will obtain data about some named packages.  Note that the quantity of packages requested should
     * not exceed {@link #GETBULKPKG_LIMIT}; if it does exceed this limit then an instance of
     * {@link LimitExceededException} will be thrown.</p>
     *
     * <p>This limit can be obtained from the
     * {@link MiscellaneousApi#getRuntimeInformation(GetRuntimeInformationRequest)}
     * method.
     * </p>
     *
     * <p>If a package was not able to be found then it will simply not appear in the results.  If reference data
     * objects such as the architecture was unable to be found then this method will throw an instance of
     * {@link ObjectNotFoundException}.</p>
     *
     * <p>The definition of architecture on this method is strict; will only return data for which there is
     * a version on that architecture.</p>
     *
     * <p>Various elements of the response can be filtered in or out by using the filter attribute on the request
     * object.</p>
     */

    GetBulkPkgResult getBulkPkg(GetBulkPkgRequest getBulkPkgRequest) throws LimitExceededException, ObjectNotFoundException;

    /**
     * <p>This method will update the prominence of the nominated package.  The prominence is identified by the
     * ordering of the prominence as a natural identifier.</p>
     */

    UpdatePkgProminenceResult updatePkgProminence(UpdatePkgProminenceRequest request) throws ObjectNotFoundException;

    /**
     * <p>Enqueues a request on behalf of the current user to produce a spreadsheet showing the coverage of categories
     * for the packages.  See the {@link JobApi} for details on how to control the
     * job.</p>
     */

    QueuePkgCategoryCoverageExportSpreadsheetJobResult queuePkgCategoryCoverageExportSpreadsheetJob(QueuePkgCategoryCoverageExportSpreadsheetJobRequest request);

    /**
     * <P>Enqueues a request on behalf od the current user to import package data from a spreadsheet that is uploaded
     * to the server.  It does this and also produces an outbound spreadsheet of the result.</P>
     * @throws ObjectNotFoundException in the case that the data identified by GUID does not exist.
     */

    QueuePkgCategoryCoverageImportSpreadsheetJobResult queuePkgCategoryCoverageImportSpreadsheetJob(QueuePkgCategoryCoverageImportSpreadsheetJobRequest request) throws ObjectNotFoundException;

    /**
     * <p>Enqueues a request on behalf of the current user to produce a spreadsheet showing which packages have icons
     * associated with them.  See the {@link JobApi} for details on how to control the
     * job.</p>
     */

    QueuePkgIconSpreadsheetJobResult queuePkgIconSpreadsheetJob(QueuePkgIconSpreadsheetJobRequest request);

    /**
     * <p>Enqueues a request on behalf of the current user to produce a spreadsheet showing which packages have what
     * prominence.  See the {@link JobApi} for details on how to control the
     * job.</p>
     */

    QueuePkgProminenceAndUserRatingSpreadsheetJobResult queuePkgProminenceAndUserRatingSpreadsheetJob(QueuePkgProminenceAndUserRatingSpreadsheetJobRequest request);

    /**
     * <p>Enqueues a request to produce an archive of all of the icons of the packages.</p>
     */

    QueuePkgIconExportArchiveJobResult queuePkgIconExportArchiveJob(QueuePkgIconExportArchiveJobRequest request);

    /**
     * <p>Enqueues a job to cause all PkgVersion objects with no payload length to get those populated if possible.</p>
     */

    QueuePkgVersionPayloadLengthPopulationJobResult queuePkgVersionPayloadLengthPopulationJob(QueuePkgVersionPayloadLengthPopulationJobRequest request);

    /**
     * <p>Enqueues a job to produce a spreadsheet of the coverage of package version localizations.</p>
     */

    QueuePkgVersionLocalizationCoverageExportSpreadsheetJobResult queuePkgVersionLocalizationCoverageExportSpreadsheetJob(QueuePkgVersionLocalizationCoverageExportSpreadsheetJobRequest request);

    /**
     * <p>Enqueues a job to produce a spreadsheet of the coverage of package localizations.</p>
     */

    QueuePkgLocalizationCoverageExportSpreadsheetJobResult queuePkgLocalizationCoverageExportSpreadsheetJob(QueuePkgLocalizationCoverageExportSpreadsheetJobRequest request);

    /**
     * <p>The package might have a change log associated with it.  This is just a long string with notes
     * about what versions were released and what changed in those releases.  If there is no change log
     * stored for this package, a NULL value may be returned in {@link GetPkgChangelogResult#content}.
     * </p>
     */

    GetPkgChangelogResult getPkgChangelog(GetPkgChangelogRequest request) throws ObjectNotFoundException;

    /**
     * <p>The package is able to have a change log associated with it.  This method will update the change
     * log.  If the change log content is supplied as NULL or an empty string then the change log may be
     * removed.</p>
     * @throws ObjectNotFoundException if the package is not able to be found.
     */

    UpdatePkgChangelogResult updatePkgChangelog(UpdatePkgChangelogRequest request) throws ObjectNotFoundException;

    /**
     * <p>This method will allow a package version to be updated.</p>
     * @throws ObjectNotFoundException if the package version is not able to be found.
     */

    UpdatePkgVersionResult updatePkgVersion(UpdatePkgVersionRequest request) throws ObjectNotFoundException;

}
