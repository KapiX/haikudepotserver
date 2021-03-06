/*
 * Copyright 2018-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.reference.job;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.Country;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.PkgCategory;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.pkg.job.PkgDumpExportJobRunner;
import org.haiku.haikudepotserver.reference.model.ReferenceDumpExportJobSpecification;
import org.haiku.haikudepotserver.reference.model.dumpexport.DumpExportReference;
import org.haiku.haikudepotserver.reference.model.dumpexport.DumpExportReferenceCountry;
import org.haiku.haikudepotserver.reference.model.dumpexport.DumpExportReferenceNaturalLanguage;
import org.haiku.haikudepotserver.reference.model.dumpexport.DumpExportReferencePkgCategory;
import org.haiku.haikudepotserver.support.ArchiveInfo;
import org.haiku.haikudepotserver.support.RuntimeInformationService;
import org.haiku.haikudepotserver.support.cayenne.GeneralQueryHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

/**
 * <p>This produces a set of reference data that can be used by the HaikuDepot
 * client to get a list of valid categories, natural languages, etc...</p>
 */

@Component
public class ReferenceDumpExportJobRunner extends AbstractJobRunner<ReferenceDumpExportJobSpecification> {

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgDumpExportJobRunner.class);

    private final ServerRuntime serverRuntime;
    private final MessageSource messageSource;
    private final RuntimeInformationService runtimeInformationService;
    private final ObjectMapper objectMapper;

    public ReferenceDumpExportJobRunner(
            ServerRuntime serverRuntime,
            MessageSource messageSource,
            RuntimeInformationService runtimeInformationService,
            ObjectMapper objectMapper) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.messageSource = Preconditions.checkNotNull(messageSource);
        this.runtimeInformationService = Preconditions.checkNotNull(runtimeInformationService);
        this.objectMapper = Preconditions.checkNotNull(objectMapper);
    }

    @Override
    public void run(JobService jobService, ReferenceDumpExportJobSpecification specification)
            throws IOException {
        // this will register the outbound data against the job.
        JobDataWithByteSink jobDataWithByteSink = jobService.storeGeneratedData(
                specification.getGuid(),
                "download",
                MediaType.JSON_UTF_8.toString());

        try (
                final OutputStream outputStream = jobDataWithByteSink.getByteSink().openBufferedStream();
                final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream);
                final JsonGenerator jsonGenerator = objectMapper.getFactory().createGenerator(gzipOutputStream)
        ) {
            jsonGenerator.writeStartObject();
            writeInfo(jsonGenerator);
            writeData(jsonGenerator, specification);
            jsonGenerator.writeEndObject();
        }
    }

    private void writeInfo(JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.writeFieldName("info");
        objectMapper.writeValue(jsonGenerator, createArchiveInfo(serverRuntime.newContext()));
    }

    private void writeData(
            JsonGenerator jsonGenerator,
            ReferenceDumpExportJobSpecification specification) throws IOException {
        DumpExportReference dumpExportReference = createDumpExportReference(specification);
        jsonGenerator.writeFieldName("countries");
        objectMapper.writeValue(jsonGenerator, dumpExportReference.getCountries());
        jsonGenerator.writeFieldName("naturalLanguages");
        objectMapper.writeValue(jsonGenerator, dumpExportReference.getNaturalLanguages());
        jsonGenerator.writeFieldName("pkgCategories");
        objectMapper.writeValue(jsonGenerator, dumpExportReference.getPkgCategories());
    }

    private ArchiveInfo createArchiveInfo(ObjectContext context) {
        Date modifyTimestamp = GeneralQueryHelper.getLastModifyTimestampSecondAccuracy(
                context,
                Country.class, NaturalLanguage.class, PkgCategory.class);
        return new ArchiveInfo(modifyTimestamp, runtimeInformationService.getProjectVersion());
    }

    private DumpExportReference createDumpExportReference(ReferenceDumpExportJobSpecification specification) {
        DumpExportReference dumpExportReference = new DumpExportReference();
        ObjectContext context = serverRuntime.newContext();
        NaturalLanguage naturalLanguage = NaturalLanguage.tryGetByCode(context, specification.getNaturalLanguageCode())
                .orElseGet(() -> NaturalLanguage.getEnglish(context));

        dumpExportReference.setCountries(
                Country.getAll(context)
                        .stream()
                        .map(c -> {
                            DumpExportReferenceCountry dc = new DumpExportReferenceCountry();
                            dc.setCode(c.getCode());
                            dc.setName(c.getName());
                            return dc;
                        })
                        .collect(Collectors.toList()));

        dumpExportReference.setNaturalLanguages(
                NaturalLanguage.getAll(context)
                        .stream()
                        .map(nl -> {
                            DumpExportReferenceNaturalLanguage dnl = new DumpExportReferenceNaturalLanguage();
                            dnl.setIsPopular(nl.getIsPopular());
                            dnl.setCode(nl.getCode());
                            dnl.setName(
                                    messageSource.getMessage(
                                            nl.getTitleKey(),
                                            new Object[] {},
                                            naturalLanguage.toLocale()));
                            return dnl;
                        })
                        .collect(Collectors.toList()));

        dumpExportReference.setPkgCategories(
                PkgCategory.getAll(context)
                        .stream()
                        .map(pc -> {
                            DumpExportReferencePkgCategory dpc = new DumpExportReferencePkgCategory();
                            dpc.setCode(pc.getCode());
                            dpc.setName(
                                    messageSource.getMessage(
                                            pc.getTitleKey(),
                                            new Object[] {},
                                            naturalLanguage.toLocale()));
                            return dpc;
                        })
                        .collect(Collectors.toList()));

        return dumpExportReference;
    }

}