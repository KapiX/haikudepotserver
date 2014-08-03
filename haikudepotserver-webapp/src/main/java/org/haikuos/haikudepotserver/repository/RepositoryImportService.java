/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.repository;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.access.Transaction;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.dataobjects.Repository;
import org.haikuos.haikudepotserver.pkg.PkgOrchestrationService;
import org.haikuos.haikudepotserver.repository.model.PkgRepositoryImportJob;
import org.haikuos.haikudepotserver.support.AbstractLocalBackgroundProcessingService;
import org.haikuos.pkg.PkgIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * <p>This object is responsible for migrating a HPKR file from a remote repository into the Haiku Depot Server
 * database.  It will copy the data into a local file and then work through it there.</p>
 *
 * <p>The system works by the caller lodging a request to update from a remote repository.  The request may be
 * later superceeded by another request for the same repository.  When the import process has capacity then it
 * will undertake the import process.</p>
 */

// TODO; at some time in the future, when it is worthwhile, use a JMS broker to not loose jobs.

public class RepositoryImportService extends AbstractLocalBackgroundProcessingService {

    protected static Logger LOGGER = LoggerFactory.getLogger(RepositoryImportService.class);

    @Resource
    ServerRuntime serverRuntime;

    @Resource
    PkgOrchestrationService pkgService;

    /**
     * <p>This method will check that there is not already a job in the queue for this repository and then will
     * add it to the queue so that it is run at some time in the future.</p>
     */

    public void submit(final PkgRepositoryImportJob job) {
        Preconditions.checkNotNull(job);
        Preconditions.checkState(null!=executor, "the service is not running, but a job is being submitted");

        // first thing to do is to validate the request; does the repository exist and what is it's URL?
        Optional<Repository> repositoryOptional = Repository.getByCode(serverRuntime.getContext(), job.getCode());

        if(!repositoryOptional.isPresent()) {
            throw new RuntimeException("unable to import repository data because repository was not able to be found for code; "+job.getCode());
        }

        // make sure that we do not enqueue a job for the repository import twice.

        if(!Iterables.tryFind(
                Lists.newArrayList(runnables),
                new Predicate<Runnable>() {
                    @Override
                    public boolean apply(java.lang.Runnable input) {
                        ImportRepositoryDataJobRunnable importRepositoryDataJobRunnable = (ImportRepositoryDataJobRunnable) input;
                        return importRepositoryDataJobRunnable.getJob().equals(job);
                    }
                }).isPresent()) {
            executor.submit(new ImportRepositoryDataJobRunnable(this, job));
            LOGGER.info("have submitted job to import repository data; {}", job.toString());
        }
        else {
            LOGGER.info("ignoring job to import repository data as there is already one waiting; {}", job.toString());
        }
    }

    protected void run(PkgRepositoryImportJob job) {
        Preconditions.checkNotNull(job);

        Repository repository = Repository.getByCode(serverRuntime.getContext(), job.getCode()).get();
        URL url;

        try {
            url = new URL(repository.getUrl());
        }
        catch(MalformedURLException mue) {
            throw new IllegalStateException("the repository "+job.getCode()+" has a malformed url; "+repository.getUrl(),mue);
        }

        // now shift the URL's data into a temporary file and then process it.
        File temporaryFile = null;

        // start a cayenne long-running txn
        Transaction transaction = serverRuntime.getDataDomain().createTransaction();
        Transaction.bindThreadTransaction(transaction);

        try {
            temporaryFile = File.createTempFile(job.getCode()+"__import",".hpkr");
            Resources.asByteSource(url).copyTo(Files.asByteSink(temporaryFile));

            LOGGER.debug("did copy data for repository {} ({}) to temporary file", job.getCode(), url.toString());

            org.haikuos.pkg.HpkrFileExtractor fileExtractor = new org.haikuos.pkg.HpkrFileExtractor(temporaryFile);
            PkgIterator pkgIterator = new PkgIterator(fileExtractor.getPackageAttributesIterator());

            long startTimeMs = System.currentTimeMillis();
            LOGGER.info("will process data for repository {}", job.getCode());

            while (pkgIterator.hasNext()) {
                ObjectContext pkgImportContext = serverRuntime.getContext();
                pkgService.importFrom(pkgImportContext, repository.getObjectId(), pkgIterator.next());
                pkgImportContext.commitChanges();
            }

            LOGGER.info("did process data for repository {} in {}ms", job.getCode(), System.currentTimeMillis() - startTimeMs);

            transaction.commit();
        }
        catch(Throwable th) {

            transaction.setRollbackOnly();
            LOGGER.error("a problem has arisen processing a repository file for repository " + job.getCode() + " from url '" + url.toString() + "'", th);

        }
        finally {

            if(null!=temporaryFile && temporaryFile.exists()) {
                if(!temporaryFile.delete()) {
                    LOGGER.error("unable to delete the file; {}" + temporaryFile.getAbsolutePath());
                }
            }

            Transaction.bindThreadTransaction(null);

            if (Transaction.STATUS_MARKED_ROLLEDBACK == transaction.getStatus()) {
                try {
                    transaction.rollback();
                }
                catch(Exception e) {
                    // ignore
                }
            }

        }

    }

    /**
     * <p>This is the object that gets enqueued to actually do the work.</p>
     */

    public static class ImportRepositoryDataJobRunnable implements Runnable {

        private PkgRepositoryImportJob job;

        private RepositoryImportService service;

        public ImportRepositoryDataJobRunnable(
                RepositoryImportService service,
                PkgRepositoryImportJob job) {
            Preconditions.checkNotNull(service);
            Preconditions.checkNotNull(job);
            this.service = service;
            this.job = job;
        }

        public PkgRepositoryImportJob getJob() {
            return job;
        }

        @Override
        public void run() {
            service.run(job);
        }

    }

}
