/*
 * Copyright (c) 2018, Syntak <syntaktv@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.driftnetfishing;

import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.*;
import java.util.Set;

public class DriftNetFishingOverlay extends Overlay {

    private final Client client;
    private final DriftNetFishingConfig config;
    private final DriftNetFishingPlugin plugin;

    @Inject
    DriftNetFishingOverlay(Client client, DriftNetFishingConfig config, DriftNetFishingPlugin plugin) {
        this.client = client;
        this.config = config;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {

        for (GameObject obj : plugin.getDriftAnchors()) {
            renderNetOverlay(graphics, obj);
        }

        Set<Actor> interactedNPCs = plugin.getActiveFishies();

        for (NPC npc : plugin.getFishies()) {
            Color hCol = Color.RED;
            if (interactedNPCs.contains(npc)) {
                hCol = Color.GREEN;
            }
            renderNpcOverlay(graphics, npc, hCol);
        }

        return null;
    }


    private void renderNpcOverlay(Graphics2D graphics, NPC actor, Color color) {

        Polygon objectClickbox = actor.getConvexHull();
        renderPoly(graphics, color, objectClickbox);

    }

    private void renderNetOverlay(Graphics2D graphics, GameObject net) {
        Polygon objectClickbox = net.getConvexHull();
        Color color = Color.GREEN;
        if (client.getObjectDefinition(net.getId()).getImpostor().getId() == ObjectID.DRIFT_NET_ANCHORS ||
                client.getObjectDefinition(net.getId()).getImpostor().getId() == ObjectID.DRIFT_NET_FULL) {
            color = Color.RED;
        }
        renderPoly(graphics, color, objectClickbox);

        // Text
        String text = "?";
        if (plugin.getNetFishCount().get(net) > 9)
            text = "Full";
        else if (plugin.getNetFishCount().get(net) > -1)
            text = plugin.getNetFishCount().get(net) + "";

        Point textLocation = net.getCanvasTextLocation(graphics, text, 40);
        OverlayUtil.renderTextLocation(graphics, textLocation, text, color);
    }

    private void renderPoly(Graphics2D graphics, Color color, Polygon polygon) {
        if (polygon != null) {
            graphics.setColor(color);
            graphics.setStroke(new BasicStroke(2));
            graphics.draw(polygon);
            graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 15));
            graphics.fill(polygon);
        }
    }
}
