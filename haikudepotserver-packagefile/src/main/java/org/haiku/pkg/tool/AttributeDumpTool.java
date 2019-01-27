/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg.tool;

import org.haiku.pkg.HpkrFileExtractor;
import org.haiku.pkg.output.AttributeWriter;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.OutputStreamWriter;

/**
 * <p>Given an HPKR file, this small program will dump all of the attributes of the HPKR file.  This is handy for
 * diagnostic purposes.</p>
 */

public class AttributeDumpTool implements Runnable {

    private static Logger LOGGER = LoggerFactory.getLogger(AttributeDumpTool.class);

    @Option(name = "-f", required = true, usage = "the HPKR file is required")
    private File hpkrFile;

    public static void main(String[] args) {
        AttributeDumpTool main = new AttributeDumpTool();
        CmdLineParser parser = new CmdLineParser(main);

        try {
            parser.parseArgument(args);
            main.run();
        }
        catch (CmdLineException cle) {
            throw new IllegalStateException("unable to parse arguments",cle);
        }
    }

    public void run() {
        new CmdLineParser(this);

        try (
                HpkrFileExtractor hpkrFileExtractor = new HpkrFileExtractor(hpkrFile);
                OutputStreamWriter streamWriter = new OutputStreamWriter(System.out);
                AttributeWriter attributeWriter = new AttributeWriter(streamWriter) ) {
            attributeWriter.write(hpkrFileExtractor.getPackageAttributesIterator());
            attributeWriter.flush();
        } catch (Throwable th) {
            LOGGER.error("unable to dump attributes", th);
        }

    }

}
