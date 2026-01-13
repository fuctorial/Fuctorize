package ru.fuctorial.fuctorize.utils.pathfinding;

import net.minecraft.entity.player.EntityPlayer;
import ru.fuctorial.fuctorize.utils.RenderUtils;

import java.awt.Color;
import java.util.List;

/**
 * Rendering utilities for path visualization extracted from BotNavigator.
 */
public final class PathRenderer {
    private PathRenderer() {}

    public static void render(EntityPlayer player,
                              List<PathNode> traversedPathHistory,
                              List<PathNode> currentPath,
                              int currentNodeIndex,
                              boolean isNavigating,
                              float globalAlpha) { // <-- ДОБАВЛЕН НОВЫЙ ПАРАМЕТР

        if (player == null || (currentPath == null || currentPath.isEmpty())) return;

        RenderUtils.beginWorld3DRender();
        try {
            final double yOffset = 0.1;
            final double raise = 1.0; // поднимаем рендер на 1 блок выше уровня ног

            double playerX = player.posX;
            double playerY = player.boundingBox.minY + raise + yOffset;
            double playerZ = player.posZ;

            if (traversedPathHistory != null && !traversedPathHistory.isEmpty()) {
                int historySize = traversedPathHistory.size();
                for (int i = 0; i < historySize - 1; i++) {
                    PathNode from = traversedPathHistory.get(i);
                    PathNode to = traversedPathHistory.get(i + 1);
                    float progress = (float) i / (float) Math.max(1, historySize - 1);
                    Color lineColor = interpolateColor(new Color(0, 100, 0), new Color(0, 255, 100), progress);
                    RenderUtils.drawLine(
                            from.x + 0.5, from.getFeetY() + raise + yOffset, from.z + 0.5,
                            to.x + 0.5, to.getFeetY() + raise + yOffset, to.z + 0.5,
                            lineColor, 0.6f * globalAlpha, 2.5f
                    );
                }
                PathNode lastHistoricNode = traversedPathHistory.get(historySize - 1);
                RenderUtils.drawLine(
                        lastHistoricNode.x + 0.5, lastHistoricNode.getFeetY() + raise + yOffset, lastHistoricNode.z + 0.5,
                        playerX, playerY, playerZ,
                        new Color(0, 255, 100), 0.8f * globalAlpha, 3.0f
                );
            }

            if (isNavigating && currentNodeIndex < currentPath.size()) {
                int remainingNodes = currentPath.size() - currentNodeIndex;

                PathNode nextNode = currentPath.get(currentNodeIndex);
                RenderUtils.drawLine(
                        playerX, playerY, playerZ,
                        nextNode.x + 0.5, nextNode.getFeetY() + raise + yOffset, nextNode.z + 0.5,
                        new Color(0, 200, 255), 0.9f * globalAlpha, 3.5f
                );

                for (int i = currentNodeIndex; i < currentPath.size() - 1; i++) {
                    PathNode from = currentPath.get(i);
                    PathNode to = currentPath.get(i + 1);

                    float progress = (float) (i - currentNodeIndex) / (float) Math.max(1, remainingNodes - 1);
                    Color lineColor = interpolateColor(new Color(0, 200, 255), new Color(255, 200, 0), progress);
                    float lineWidth = 3.0f - (progress * 1.0f);

                    RenderUtils.drawLine(
                            from.x + 0.5, from.getFeetY() + raise + yOffset, from.z + 0.5,
                            to.x + 0.5, to.getFeetY() + raise + yOffset, to.z + 0.5,
                            lineColor, 0.8f * globalAlpha, lineWidth
                    );

                    if (to.breakBlocks > 0) {
                        RenderUtils.drawLine(to.x + 0.5, to.getFeetY() + raise + yOffset, to.z + 0.5, to.x + 0.5, to.getFeetY() + raise + yOffset + 0.5, to.z + 0.5, new Color(255, 0, 0), 1.0f * globalAlpha, 4.0f);
                    }
                    if (to.placeBlocks > 0) {
                        RenderUtils.drawLine(to.x + 0.5, to.getFeetY() + raise + yOffset, to.z + 0.5, to.x + 0.5, to.getFeetY() + raise + yOffset + 0.5, to.z + 0.5, new Color(200, 0, 255), 1.0f * globalAlpha, 4.0f);
                    }
                    if (to.needsJump) {
                        RenderUtils.drawLine(to.x + 0.5, to.getFeetY() + raise + yOffset + 0.2, to.z + 0.5, to.x + 0.5, to.getFeetY() + raise + yOffset + 0.7, to.z + 0.5, new Color(255, 255, 255), 1.0f * globalAlpha, 3.0f);
                    }
                }
            }

            renderNodeMarkers(traversedPathHistory, currentPath, currentNodeIndex, globalAlpha);
        } finally {
            RenderUtils.endWorld3DRender();
        }
    }


    private static void renderNodeMarkers(List<PathNode> traversed,
                                          List<PathNode> currentPath,
                                          int currentIndex,
                                          float globalAlpha) {
        if ((traversed == null || traversed.isEmpty()) && (currentPath == null || currentPath.isEmpty())) {
            return;
        }

        final double baseThickness = 0.02;
        final double pillarHeight = 0.45;
        final double nodeHalfSize = 0.28;

        if (traversed != null) {
            for (PathNode node : traversed) {
                drawNodeMarker(node, new Color(40, 130, 60), 0.25f* globalAlpha, nodeHalfSize, baseThickness, pillarHeight * 0.6);
            }
        }

        if (currentPath == null) return;

        int totalNodes = currentPath.size();
        int remaining = Math.max(1, totalNodes - currentIndex);
        for (int i = 0; i < currentPath.size(); i++) {
            PathNode node = currentPath.get(i);
            Color color;
            float alpha;
            if (i < currentIndex) {
                color = new Color(60, 170, 80);
                alpha = 0.35f;
            } else if (i == currentIndex) {
                color = new Color(255, 255, 255);
                alpha = 0.9f;
            } else {
                float progress = (float) (i - currentIndex) / (float) Math.max(1, remaining - 1);
                color = interpolateColor(new Color(0, 200, 255), new Color(255, 160, 0), progress);
                alpha = 0.55f;
            }
            drawNodeMarker(node, color, alpha, nodeHalfSize, baseThickness, pillarHeight);
        }
    }

    private static void drawNodeMarker(PathNode node,
                                       Color color,
                                       float alpha, // Эта alpha уже будет умножена на globalAlpha
                                       double halfSize,
                                       double thickness,
                                       double pillarHeight) {
        if (node == null) return;
        double cx = node.x + 0.5;
        double cz = node.z + 0.5;
        double baseY = node.getFeetY() + 1.0;

        RenderUtils.drawBox(
                cx - halfSize,
                baseY - thickness,
                cz - halfSize,
                cx + halfSize,
                baseY + thickness,
                cz + halfSize,
                color,
                alpha
        );

        RenderUtils.drawLine(
                cx,
                baseY - thickness,
                cz,
                cx,
                baseY - thickness + pillarHeight,
                cz,
                color,
                Math.min(1.0f, alpha + 0.1f),
                2.0f
        );
    }

    private static Color interpolateColor(Color c1, Color c2, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int r = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * t);
        int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * t);
        int b = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * t);
        return new Color(r, g, b);
    }
}
