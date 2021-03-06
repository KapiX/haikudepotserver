package org.haiku.haikudepotserver.dataobjects.auto;

import org.apache.cayenne.exp.Property;
import org.haiku.haikudepotserver.dataobjects.MediaType;
import org.haiku.haikudepotserver.dataobjects.PkgScreenshot;
import org.haiku.haikudepotserver.dataobjects.support.AbstractDataObject;

/**
 * Class _PkgScreenshotImage was generated by Cayenne.
 * It is probably a good idea to avoid changing this class manually,
 * since it may be overwritten next time code is regenerated.
 * If you need to make any customizations, please use subclass.
 */
public abstract class _PkgScreenshotImage extends AbstractDataObject {

    private static final long serialVersionUID = 1L; 

    public static final String ID_PK_COLUMN = "id";

    public static final Property<byte[]> DATA = Property.create("data", byte[].class);
    public static final Property<MediaType> MEDIA_TYPE = Property.create("mediaType", MediaType.class);
    public static final Property<PkgScreenshot> PKG_SCREENSHOT = Property.create("pkgScreenshot", PkgScreenshot.class);

    public void setData(byte[] data) {
        writeProperty("data", data);
    }
    public byte[] getData() {
        return (byte[])readProperty("data");
    }

    public void setMediaType(MediaType mediaType) {
        setToOneTarget("mediaType", mediaType, true);
    }

    public MediaType getMediaType() {
        return (MediaType)readProperty("mediaType");
    }


    public void setPkgScreenshot(PkgScreenshot pkgScreenshot) {
        setToOneTarget("pkgScreenshot", pkgScreenshot, true);
    }

    public PkgScreenshot getPkgScreenshot() {
        return (PkgScreenshot)readProperty("pkgScreenshot");
    }


}
