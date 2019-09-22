/*
 * Copyright 2014-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.multipage.markup;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.pkg.controller.PkgIconController;
import org.springframework.web.servlet.tags.RequestContextAwareTag;
import org.springframework.web.servlet.tags.form.TagWriter;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * <p>Renders HTML for a package version's icon.</p>
 */

public class PkgIconTag extends RequestContextAwareTag {

    private PkgVersion pkgVersion;

    private int size = 16;

    public PkgVersion getPkgVersion() {
        return pkgVersion;
    }

    public void setPkgVersion(PkgVersion pkgVersion) {
        this.pkgVersion = pkgVersion;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        Preconditions.checkState(size == 16 || size == 32);
        this.size = size;
    }

    private String getUrl() {
        return
                UriComponentsBuilder.newInstance()
                        .pathSegment(PkgIconController.SEGMENT_PKGICON, getPkgVersion().getPkg().getName() + ".png")
                        .queryParam(PkgIconController.KEY_FALLBACK,"true")
                        .queryParam(PkgIconController.KEY_SIZE, getImageUrlSizeParameter())
                        .queryParam("m", Long.toString(getPkgVersion().getPkg().getModifyTimestamp().getTime()))
                        .build()
                        .toString();
    }

    private String getImageUrlSizeParameter() {
        if (size == 16 || size == 32)
            return Integer.toString(size * 2);
        return Integer.toString(size);
    }

    @Override
    protected int doStartTagInternal() throws Exception {

        Preconditions.checkNotNull(pkgVersion);

        TagWriter tagWriter = new TagWriter(pageContext.getOut());

        tagWriter.startTag("img");
        tagWriter.writeAttribute("src", getUrl());
        tagWriter.writeAttribute("alt", "icon");
        tagWriter.writeAttribute("width", Integer.toString(size));
        tagWriter.writeAttribute("height", Integer.toString(size));
        tagWriter.writeAttribute("alt", "icon");

        tagWriter.endTag();

        return SKIP_BODY;
    }

}


