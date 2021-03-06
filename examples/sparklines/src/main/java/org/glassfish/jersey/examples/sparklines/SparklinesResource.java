/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2014 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.jersey.examples.sparklines;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.math.BigInteger;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

/**
 * @author Paul Sandoz
 */
@Path("/")
@Produces("image/png")
public class SparklinesResource {
    List<Integer> data;

    @DefaultValue("20")
    @QueryParam("height")
    int imageHeight;

    org.glassfish.jersey.examples.sparklines.Interval limits;

    EntityTag tag;

    public SparklinesResource(
            @QueryParam("d") IntegerList data,
            @DefaultValue("0,100") @QueryParam("limits") Interval limits,
            @Context Request request,
            @Context UriInfo ui) {
        if (data == null)
            throw new WebApplicationException(400);

        this.data = data;

        this.limits = limits;

        if (!limits.contains(data))
            throw new WebApplicationException(400);

        this.tag = computeEntityTag(ui.getRequestUri());
        if (request.getMethod().equals("GET")) {
            Response.ResponseBuilder rb = request.evaluatePreconditions(tag);
            if (rb != null)
                throw new WebApplicationException(rb.build());
        }
    }

    @Path("discrete")
    @GET
    public Response discrete(
            @DefaultValue("2") @QueryParam("width") int width,
            @DefaultValue("50") @QueryParam("upper") int upper,
            @DefaultValue("red") @QueryParam("upper-color") ColorParam upperColor,
            @DefaultValue("gray") @QueryParam("lower-color") ColorParam lowerColor
    ) {
        BufferedImage image = new BufferedImage(
                data.size() * width - 1, imageHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setBackground(Color.WHITE);
        g.clearRect(0, 0, image.getWidth(), image.getHeight());


        int gap = 4;
        float d = (limits.width() + 1) / (float) (imageHeight - gap);
        for (int i = 0, x = 0, y = 0; i < data.size(); i++, x += width) {
            int v = data.get(i);
            g.setColor((v >= upper) ? upperColor : lowerColor);

            y = imageHeight - (int) ((v - limits.lower()) / d);
            g.drawRect(x, y - gap, width - 2, gap);
        }

        return Response.ok(image).tag(tag).build();
    }

    @Path("smooth")
    @GET
    public Response smooth(
            @DefaultValue("2") @QueryParam("step") int step,
            @DefaultValue("true") @QueryParam("min-m") boolean hasMin,
            @DefaultValue("true") @QueryParam("max-m") boolean hasMax,
            @DefaultValue("true") @QueryParam("last-m") boolean hasLast,
            @DefaultValue("blue") @QueryParam("min-color") ColorParam minColor,
            @DefaultValue("green") @QueryParam("max-color") ColorParam maxColor,
            @DefaultValue("red") @QueryParam("last-color") ColorParam lastColor
    ) {
        BufferedImage image = new BufferedImage(
                data.size() * step - 4, imageHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setBackground(Color.WHITE);
        g.clearRect(0, 0, image.getWidth(), image.getHeight());

        g.setColor(Color.gray);
        int[] xs = new int[data.size()];
        int[] ys = new int[data.size()];
        int gap = 4;
        float d = (limits.width() + 1) / (float) (imageHeight - gap);
        for (int i = 0, x = 0, y = 0; i < data.size(); i++, x += step) {
            int v = data.get(i);
            xs[i] = x;
            ys[i] = imageHeight - 3 - (int) ((v - limits.lower()) / d);
        }
        g.drawPolyline(xs, ys, data.size());

        if (hasMin) {
            int i = data.indexOf(Collections.min(data));
            g.setColor(minColor);
            g.fillRect(xs[i] - step / 2, ys[i] - step / 2, step, step);
        }
        if (hasMax) {
            int i = data.indexOf(Collections.max(data));
            g.setColor(maxColor);
            g.fillRect(xs[i] - step / 2, ys[i] - step / 2, step, step);
        }
        if (hasMax) {
            g.setColor(lastColor);
            g.fillRect(xs[xs.length - 1] - step / 2, ys[ys.length - 1] - step / 2, step, step);
        }

        return Response.ok(image).tag(tag).build();
    }

    private EntityTag computeEntityTag(URI u) {
        return new EntityTag(
                computeDigest(u.getRawPath() + u.getRawQuery()));
    }

    private String computeDigest(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA");
            byte[] digest = md.digest(content.getBytes());
            BigInteger bi = new BigInteger(digest);
            return bi.toString(16);
        } catch (Exception e) {
            return "";
        }
    }
}
