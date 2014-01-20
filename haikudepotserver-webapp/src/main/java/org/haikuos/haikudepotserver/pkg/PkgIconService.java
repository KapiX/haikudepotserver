/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.dataobjects.MediaType;
import org.haikuos.haikudepotserver.dataobjects.Pkg;
import org.haikuos.haikudepotserver.dataobjects.PkgIcon;
import org.haikuos.haikudepotserver.dataobjects.PkgIconImage;
import org.haikuos.haikudepotserver.pkg.model.BadPkgIconException;
import org.haikuos.haikudepotserver.support.Closeables;
import org.haikuos.haikudepotserver.support.ImageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * <p>This service helps out with package icons.</p>
 */

@Service
public class PkgIconService {

    protected static Logger logger = LoggerFactory.getLogger(PkgIconService.class);

    @Resource
    ServerRuntime serverRuntime;

    private ImageHelper imageHelper = new ImageHelper();

    private void writeGenericIconImage(
            OutputStream output,
            int size) throws IOException {

        Preconditions.checkNotNull(output);
        Preconditions.checkState(16==size||32==size);

        String resource = String.format("/img/generic/generic%d.png", size);
        InputStream inputStream = null;

        try {
            inputStream = this.getClass().getResourceAsStream(resource);

            if(null==inputStream) {
                throw new IllegalStateException(String.format("the resource; %s was not able to be found, but should be in the application build product", resource));
            }
            else {
                ByteStreams.copy(inputStream, output);
            }
        }
        finally {
            Closeables.closeQuietly(inputStream);
        }
    }

    /**
     * <p>This method will write the package's icon to the supplied output stream.  If there is no icon stored
     * for the package then a generic icon will be provided instead.</p>
     */

    public void writePkgIconImage(
            OutputStream output,
            ObjectContext context,
            Pkg pkg,
            int size) throws IOException {

        Preconditions.checkNotNull(output);
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(pkg);
        Preconditions.checkState(16==size||32==size);

        Optional<PkgIconImage> pkgIconImageOptional = pkg.getPkgIconImage(
                MediaType.getByCode(context, com.google.common.net.MediaType.PNG.toString()).get(),
                size);

        if(pkgIconImageOptional.isPresent()) {
            output.write(pkgIconImageOptional.get().getData());
        }

        writeGenericIconImage(output, size);
    }

    /**
     * <p>This method will write the PNG data supplied in the input to the package as its icon.  Note that the icon
     * must comply with necessary characteristics; for example it must be either 16 or 32 pixels along both its sides.
     * If it is non-compliant then an instance of {@link BadPkgIconException} will be thrown.</p>
     */

    public void storePkgIconImage(
            InputStream input,
            int expectedSize,
            ObjectContext context,
            Pkg pkg) throws IOException, BadPkgIconException {

        Preconditions.checkNotNull(input);
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(pkg);
        Preconditions.checkState(16==expectedSize||32==expectedSize);

        byte[] pngData = ByteStreams.toByteArray(input);
        ImageHelper.Size size =  imageHelper.derivePngSize(pngData);

        // check that the file roughly looks like PNG and that the size can be
        // parsed and that the size fits the requirements for the icon.

        if(null==size || (!size.areSides(16) && !size.areSides(32))) {
            logger.warn("attempt to set the package icon for package {}, but the size was not able to be established; either it is not a valid png image or the size of the png image is not appropriate",pkg.getName());
            throw new BadPkgIconException();
        }

        if(expectedSize != size.height && expectedSize != size.width) {
            logger.warn("attempt to set the package icon for package {}, but the size was note the expected {}px",pkg.getName(),expectedSize);
            throw new BadPkgIconException();
        }

        MediaType png = MediaType.getByCode(context, com.google.common.net.MediaType.PNG.toString()).get();
        Optional<PkgIconImage> pkgIconImageOptional = pkg.getPkgIconImage(png,size.width);
        PkgIconImage pkgIconImage = null;

        if(pkgIconImageOptional.isPresent()) {
            pkgIconImage = pkgIconImageOptional.get();
        }
        else {
            PkgIcon pkgIcon = context.newObject(PkgIcon.class);
            pkg.addToManyTarget(Pkg.PKG_ICONS_PROPERTY, pkgIcon, true);
            pkgIcon.setMediaType(png);
            pkgIcon.setSize(size.width);
            pkgIconImage = context.newObject(PkgIconImage.class);
            pkgIcon.addToManyTarget(PkgIcon.PKG_ICON_IMAGES_PROPERTY, pkgIconImage, true);
        }

        pkgIconImage.setData(pngData);
        pkg.setModifyTimestamp(new java.util.Date());

        logger.info("the icon {}px for package {} has been updated", size.width, pkg.getName());
    }

}
